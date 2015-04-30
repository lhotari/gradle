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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.FileWatchListener;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class Jdk7FileWatcherFactory implements FileWatcherFactory {
    private final ExecutorService executor;

    public Jdk7FileWatcherFactory(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public FileWatcher watch(Iterable<File> roots, FileWatchListener listener) {
        WatchServiceFileWatcher fileWatcher = null;
        try {
            fileWatcher = new WatchServiceFileWatcher(roots, listener);
        } catch (IOException e) {
            UncheckedException.throwAsUncheckedException(e);
        }
        executor.submit(fileWatcher);
        return fileWatcher;
    }
}
