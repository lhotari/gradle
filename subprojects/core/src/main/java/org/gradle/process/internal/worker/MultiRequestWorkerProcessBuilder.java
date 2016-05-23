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

package org.gradle.process.internal.worker;

/**
 * Configures and builds multi-request workers. A multi-request worker runs zero or more requests in a forked worker process.
 *
 * <p>This builder produces instances of type {@link T}. Each method call on the returned object will run the method in the worker and block until the result is received. Any exception thrown by the worker method is rethrown to the caller.
 *
 * <p>The worker process executes the request using an instance of the implementation type specified as a parameter to {@link WorkerProcessFactory#multiRequestWorker(Class, Class, Class)}.</p>
 *
 * <p>The worker process must be explicitly started and stopped using the methods on {@link WorkerControl}.</p>
 */
public interface MultiRequestWorkerProcessBuilder<T> extends WorkerProcessSettings {

    int USE_DEFAULT_IDLE_TIMEOUT_VALUE = -9999;

    /**
     * <p>Sets the idle timeout for the compiler daemon in milliseconds</p>
     *
     * <p>When timeout value is 0, the forked process will not be reused.</p>
     * <p>When timeout value < 0, the idle timeout is disabled.</p>
     * <p>When timeout value == -9999 (USE_DEFAULT_IDLE_TIMEOUT_VALUE constant value), the value of the System property
     * "org.gradle.workerprocess.idletimeout" gets used if it's set.</p>
     *
     * @param idleTimeout timeout in milliseconds
     */
    void setIdleTimeout(int idleTimeout);

    /**
     * Creates a worker.
     *
     * <p>The worker process is not started until {@link WorkerControl#start()} is called on the returned object.</p>
     */
    T build();
}
