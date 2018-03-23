/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.internal.resources.ResourceLock;

/**
 * Represents an execution that cannot begin until a given resource lock has been acquired.
 */
public interface ConditionalExecution<T> {
    /**
     * Provides the resource lock that much be acquired before execution can begin.
     */
    ResourceLock getResourceLock();

    /**
     * Provides the Runnable that should be executed once the resource lock is acquired.
     */
    Runnable getExecution();

    /**
     * Blocks waiting for this execution to complete.  Returns a result provided by the execution.
     */
    T await();

    /**
     * This method will be called upon completion of the execution.
     */
    void complete();

    /**
     * Whether this execution has been completed or not.
     */
    boolean isComplete();

    /**
     * If a failure occurs during execution, this method will be called with the exception.
     */
    void registerFailure(Throwable t);
}
