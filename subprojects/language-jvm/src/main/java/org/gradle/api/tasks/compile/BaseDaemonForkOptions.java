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

package org.gradle.api.tasks.compile;

import static org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions.USE_DEFAULT_IDLE_TIMEOUT_VALUE;

/**
 * Fork options for worker process.
 */
public class BaseDaemonForkOptions extends BaseForkOptions {
    private int idleTimeout = USE_DEFAULT_IDLE_TIMEOUT_VALUE;

    /**
     * Returns the idle timeout for the worker process in milliseconds
     *
     * When timeout value is 0, the forked process will not be reused. When timeout value < 0, the idle timeout is disabled.
     *
     * @return idle timeout in milliseconds
     */
    public int getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Sets the idle timeout for the worker process in milliseconds
     *
     * When timeout value is 0, the forked process will not be reused. When timeout value < 0, the idle timeout is disabled.
     *
     * @param idleTimeout timeout in milliseconds
     */
    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }
}
