/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultFileSystemChangeWaiterFactory implements FileSystemChangeWaiterFactory {
    private final FileWatcherFactory fileWatcherFactory;
    private final long quietPeriodMillis;
    private final TimeProvider timeProvider;

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory, TimeProvider timeProvider) {
        this(fileWatcherFactory, timeProvider, 250L);
    }

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory, TimeProvider timeProvider, long quietPeriodMillis) {
        this.fileWatcherFactory = fileWatcherFactory;
        this.quietPeriodMillis = quietPeriodMillis;
        this.timeProvider = timeProvider;
    }

    @Override
    public FileSystemChangeWaiter createChangeWaiter(BuildCancellationToken cancellationToken) {
        return new ChangeWaiter(fileWatcherFactory, timeProvider, quietPeriodMillis, cancellationToken);
    }

    @Override
    public void stop() {
        if (timeProvider instanceof Stoppable) {
            ((Stoppable) timeProvider).stop();
        }
    }

    private static class ChangeWaiter implements FileSystemChangeWaiter {
        private final long quietPeriodMillis;
        private final BuildCancellationToken cancellationToken;
        private final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private final AtomicLong lastChangeAt = new AtomicLong(0);
        private final FileWatcher watcher;
        private final Action<Throwable> onError;
        private boolean watching;
        private final List<FileWatcherEvent> receivedEvents = Collections.synchronizedList(new ArrayList<FileWatcherEvent>());
        private final TimeProvider timeProvider;

        private ChangeWaiter(FileWatcherFactory fileWatcherFactory, final TimeProvider timeProvider, long quietPeriodMillis, BuildCancellationToken cancellationToken) {
            this.timeProvider = timeProvider;
            this.quietPeriodMillis = quietPeriodMillis;
            this.cancellationToken = cancellationToken;
            this.onError = new Action<Throwable>() {
                @Override
                public void execute(Throwable throwable) {
                    error.set(throwable);
                    signal(lock, condition);
                }
            };
            watcher = fileWatcherFactory.watch(
                onError,
                new FileWatcherListener() {
                    @Override
                    public void onChange(final FileWatcher watcher, FileWatcherEvent event) {
                        if (!(event.getType() == FileWatcherEvent.Type.MODIFY && event.getFile().isDirectory())) {
                            receivedEvents.add(event);
                            signal(lock, condition, new Runnable() {
                                @Override
                                public void run() {
                                    lastChangeAt.set(timeProvider.getCurrentTime());
                                }
                            });
                        }
                    }
                }
            );
        }

        @Override
        public void watch(FileSystemSubset fileSystemSubset) {
            try {
                if (!fileSystemSubset.isEmpty()) {
                    watching = true;
                    watcher.watch(fileSystemSubset);
                }
            } catch (IOException e) {
                onError.execute(e);
            }
        }

        public List<FileWatcherEvent> wait(Runnable notifier) {
            Runnable cancellationHandler = new Runnable() {
                @Override
                public void run() {
                    signal(lock, condition);
                }
            };
            try {
                if (cancellationToken.isCancellationRequested()) {
                    return Collections.emptyList();
                }
                cancellationToken.addCallback(cancellationHandler);
                notifier.run();
                lock.lock();
                try {
                    long lastChangeAtValue = lastChangeAt.get();
                    while (!cancellationToken.isCancellationRequested() && error.get() == null && (lastChangeAtValue == 0 || timeProvider.getCurrentTime() - lastChangeAtValue < quietPeriodMillis)) {
                        condition.await(quietPeriodMillis, TimeUnit.MILLISECONDS);
                        lastChangeAtValue = lastChangeAt.get();
                    }
                } finally {
                    lock.unlock();
                }
                Throwable throwable = error.get();
                if (throwable != null) {
                    throw throwable;
                }
            } catch (Throwable e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                cancellationToken.removeCallback(cancellationHandler);
                watcher.stop();
            }
            return ImmutableList.copyOf(receivedEvents);
        }

        @Override
        public boolean isWatching() {
            return watching;
        }

        @Override
        public void stop() {
            watcher.stop();
        }
    }

    private static void signal(Lock lock, Condition condition, Runnable runnable) {
        boolean interrupted = Thread.interrupted();
        lock.lock();
        try {
            runnable.run();
            condition.signal();
        } finally {
            lock.unlock();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void signal(Lock lock, Condition condition) {
        signal(lock, condition, new Runnable() {
            @Override
            public void run() {

            }
        });
    }

}
