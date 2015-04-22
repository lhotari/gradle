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

package org.gradle.launcher.continuous;

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherFactory;

public class DefaultTriggerGeneratorFactory implements TriggerGeneratorFactory {
    private final ExecutorFactory executorFactory;
    private final FileWatcherFactory fileWatcherFactory;
    private final ListenerManager listenerManager;

    public DefaultTriggerGeneratorFactory(ExecutorFactory executorFactory, FileWatcherFactory fileWatcherFactory, ListenerManager listenerManager) {
        this.executorFactory = executorFactory;
        this.fileWatcherFactory = fileWatcherFactory;
        this.listenerManager = listenerManager;
    }

    @Override
    public TriggerGenerator newInstance(TriggerListener listener) {
        FileWatchStrategy fileWatchStrategy = new FileWatchStrategy(listener, fileWatcherFactory);
        // TODO: will this leak memory?
        listenerManager.addListener(fileWatchStrategy);
        return new DefaultTriggerGenerator(executorFactory.create("trigger"), fileWatchStrategy);
    }
}
