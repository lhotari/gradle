/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CompilerClientsManager {

    private static final Logger LOGGER = Logging.getLogger(CompilerDaemonManager.class);

    private final Lock lock = new ReentrantLock(true);
    private final Condition waitClientCondition = lock.newCondition();
    private final LinkedList<CompilerDaemonClient> allClients = new LinkedList<CompilerDaemonClient>();
    private final LinkedList<CompilerDaemonClient> idleClients = new LinkedList<CompilerDaemonClient>();
    private final int maxDaemons;
    private final AtomicInteger daemonsStarting = new AtomicInteger(0);
    private boolean stopping;

    private CompilerDaemonStarter compilerDaemonStarter;

    public CompilerClientsManager(CompilerDaemonStarter compilerDaemonStarter, int maxDaemons) {
        this.compilerDaemonStarter = compilerDaemonStarter;
        this.maxDaemons = maxDaemons;
    }

    public CompilerDaemonClient reserveClient(File workingDir, DaemonForkOptions forkOptions) {
        boolean startNewClient = false;
        lock.lock();
        try {
            while (!stopping) {
                CompilerDaemonClient client = reserveCompatibleIdleClient(forkOptions);
                if (client != null) {
                    if (client.ping()) {
                        return client;
                    } else {
                        try {
                            client.stop();
                        } catch (Exception e) {
                            // ignore exceptions
                        }
                        allClients.remove(client);
                    }
                }
                if (maxDaemons <= 0 || allClients.size() + daemonsStarting.get() < maxDaemons) {
                    daemonsStarting.incrementAndGet();
                    startNewClient = true;
                    break;
                } else {
                    // wait for suitable client to become idle
                    waitClientCondition.await();
                }
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            lock.unlock();
        }
        if (stopping) {
            throw new IllegalStateException("Stop has been requested.");
        }

        // start new clients concurrently
        if (startNewClient) {
            CompilerDaemonClient client = addNewClient(workingDir, forkOptions);
            daemonsStarting.decrementAndGet();
            return client;
        }

        throw new IllegalStateException("Should never reach this.");
    }

    private CompilerDaemonClient reserveCompatibleIdleClient(DaemonForkOptions forkOptions) {
        Iterator<CompilerDaemonClient> it = idleClients.iterator();
        while (it.hasNext()) {
            CompilerDaemonClient candidate = it.next();
            if (candidate.isCompatibleWith(forkOptions)) {
                it.remove();
                return candidate;
            }
        }
        return null;
    }

    private CompilerDaemonClient addNewClient(File workingDir, DaemonForkOptions forkOptions) {
        // allow the daemon to be started concurrently
        final CompilerDaemonClient client = compilerDaemonStarter.startDaemon(workingDir, forkOptions);
        lock.lock();
        try {
            client.addProcessStopListener(new Runnable() {
                @Override
                public void run() {
                    removeStoppedClient(client);
                }
            });
            allClients.add(client);
        } finally {
            lock.unlock();
        }
        return client;
    }

    private void removeStoppedClient(CompilerDaemonClient client) {
        if (stopping) {
            return;
        }
        lock.lock();
        try {
            if (allClients.remove(client)) {
                idleClients.remove(client);
                // signal a single waiting thread
                waitClientCondition.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public void release(CompilerDaemonClient client) {
        if (!client.isReuseable()) {
            return;
        }
        lock.lock();
        try {
            if (allClients.contains(client)) {
                idleClients.add(client);
                // signal all waiting threads since the first waiting thread might not be able to use the available client
                waitClientCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        stopping = true;
        lock.lock();
        try {
            LOGGER.debug("Stopping {} compiler daemon(s).", allClients.size());
            CompositeStoppable.stoppable(allClients).stop();
            LOGGER.info("Stopped {} compiler daemon(s).", allClients.size());
            allClients.clear();
            idleClients.clear();
            waitClientCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
