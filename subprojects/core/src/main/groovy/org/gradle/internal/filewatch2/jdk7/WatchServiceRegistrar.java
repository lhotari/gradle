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

package org.gradle.internal.filewatch2.jdk7;

import com.sun.nio.file.SensitivityWatchEventModifier;

import java.io.IOException;
import java.nio.file.*;

class WatchServiceRegistrar {
    // http://stackoverflow.com/a/18362404
    // make watch sensitivity as 2 seconds on MacOSX, polls every 2 seconds for changes. Default is 10 seconds.
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS = new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};
    private final WatchService watchService;

    WatchServiceRegistrar(WatchService watchService) throws IOException {
        this.watchService = watchService;
    }

    WatchKey registerWatch(Path path) throws IOException {
        WatchKey watchKey = path.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
        return watchKey;
    }
}
