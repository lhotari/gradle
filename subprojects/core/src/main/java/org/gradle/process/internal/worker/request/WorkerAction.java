/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker.request;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WorkerAction implements Action<WorkerProcessContext>, Serializable, RequestProtocol {
    private final String workerImplementationName;
    private final int idleTimeout;
    private transient CountDownLatch completed;
    private transient CountDownLatch idleTimeoutLatch;
    private transient ResponseProtocol responder;
    private transient Throwable failure;
    private transient Class<?> workerImplementation;
    private transient Object implementation;
    private transient long lastAccessMillis;
    private transient Lock lastAccessLock;

    public WorkerAction(Class<?> workerImplementation, int idleTimeout) {
        this.workerImplementationName = workerImplementation.getName();
        this.idleTimeout = idleTimeout;
    }

    @Override
    public void execute(WorkerProcessContext workerProcessContext) {
        lastAccessMillis = System.currentTimeMillis();
        completed = new CountDownLatch(1);
        idleTimeoutLatch = new CountDownLatch(1);
        lastAccessLock = new ReentrantLock();
        try {
            workerImplementation = Class.forName(workerImplementationName);
            implementation = workerImplementation.newInstance();
        } catch (Throwable e) {
            failure = e;
        }

        ObjectConnection connection = workerProcessContext.getServerConnection();
        connection.addIncoming(RequestProtocol.class, this);
        responder = connection.addOutgoing(ResponseProtocol.class);
        connection.connect();

        waitForStopOrTimeout();
    }

    private void waitForStopOrTimeout() {
        try {
            if (idleTimeout > 0) {
                idleTimeoutLatch.await();
                while (true) {
                    if (completed.await(idleTimeout, TimeUnit.MILLISECONDS) || hasTimedOut()) {
                        break;
                    }
                }
            } else {
                completed.await();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean hasTimedOut() {
        lastAccessLock.lock();
        try {
            long sinceLastAccess = System.currentTimeMillis() - lastAccessMillis;
            if (sinceLastAccess > idleTimeout) {
                completed.countDown();
                return true;
            } else if (sinceLastAccess < 0) {
                // negative duration, time has jumped, recover
                lastAccessMillis = System.currentTimeMillis();
            }
            return false;
        } finally {
            lastAccessLock.unlock();
        }
    }

    @Override
    public void stop() {
        completed.countDown();
        idleTimeoutLatch.countDown();
    }

    @Override
    public void runThenStop(String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            run(methodName, paramTypes, args);
        } finally {
            stop();
        }
    }

    @Override
    public void ping() {
        lastAccessLock.lock();
        try {
            lastAccessMillis = System.currentTimeMillis();
            responder.completed(Boolean.valueOf(completed != null && completed.getCount() > 0));
        } finally {
            lastAccessLock.unlock();
        }
    }

    @Override
    public void run(String methodName, Class<?>[] paramTypes, Object[] args) {
        lastAccessLock.lock();
        try {
            if (failure != null) {
                responder.infrastructureFailed(failure);
                return;
            }
            try {
                Method method = workerImplementation.getDeclaredMethod(methodName, paramTypes);
                Object result;
                try {
                    result = method.invoke(implementation, args);
                } catch (InvocationTargetException e) {
                    Throwable failure = e.getCause();
                    if (failure instanceof NoClassDefFoundError) {
                        // Assume an infrastructure problem
                        responder.infrastructureFailed(failure);
                    } else {
                        responder.failed(failure);
                    }
                    return;
                }
                responder.completed(result);
            } catch (Throwable t) {
                responder.infrastructureFailed(t);
            }
        } finally {
            lastAccessMillis = System.currentTimeMillis();
            lastAccessLock.unlock();
            if (idleTimeout == 0) {
                stop();
            } else {
                idleTimeoutLatch.countDown();
            }
        }
    }
}
