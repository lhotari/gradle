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

package org.gradle.internal.time;

import org.gradle.internal.TimeProvider;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class SingleThreadTimeProvider implements TimeProvider, Stoppable {
    private final StoppableExecutor executor;

    public SingleThreadTimeProvider(ExecutorFactory executorFactory) {
        this.executor = executorFactory.create(SingleThreadTimeProvider.class.getSimpleName(), 1);
    }

    public long getCurrentTime() {
        try {
            return executor.submit(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return System.currentTimeMillis();
                }
            }).get();
        } catch (InterruptedException e) {
            return System.currentTimeMillis();
        } catch (ExecutionException e) {
            return System.currentTimeMillis();
        }
    }

    @Override
    public void stop() {
        executor.stop();
    }
}
