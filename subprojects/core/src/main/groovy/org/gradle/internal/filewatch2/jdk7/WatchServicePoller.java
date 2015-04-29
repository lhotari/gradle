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
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.filewatch2.DefaultFileWatcherEvent;
import org.gradle.internal.filewatch2.EventType;
import org.gradle.internal.filewatch2.FileWatcherEvent;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

class WatchServicePoller {
    // http://stackoverflow.com/a/18362404
    // make watch sensitivity as 2 seconds on MacOSX, polls every 2 seconds for changes. Default is 10 seconds.
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS = new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};
    private final WatchService watchService;

    WatchServicePoller(WatchService watchService) throws IOException {
        this.watchService = watchService;
    }

    @Nullable
    public List<FileWatcherEvent> pollEvents(long timeout, TimeUnit unit) throws InterruptedException {
        WatchKey watchKey = watchService.poll(timeout, unit);
        if (watchKey != null) {
            return handleWatchKey(watchKey);
        }
        return null;
    }

    private List<FileWatcherEvent> handleWatchKey(WatchKey watchKey) {
        final Path watchedPath = (Path)watchKey.watchable();
        Transformer<FileWatcherEvent, WatchEvent<?>> watchEventTransformer = new Transformer<FileWatcherEvent, WatchEvent<?>>() {
            @Override
            public FileWatcherEvent transform(WatchEvent<?> event) {
                WatchEvent.Kind kind = event.kind();
                EventType eventType = toEventType(kind);
                File file = null;
                if (kind.type() == Path.class) {
                    WatchEvent<Path> ev = Cast.uncheckedCast(event);
                    file = watchedPath.resolve(ev.context()).toFile();
                }
                return new DefaultFileWatcherEvent(eventType, file);
            }
        };
        List<FileWatcherEvent> events = CollectionUtils.collect(watchKey.pollEvents(), watchEventTransformer);
        watchKey.reset();
        return events;
    }

    private EventType toEventType(WatchEvent.Kind kind) {
        if(kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return EventType.CREATE;
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return EventType.DELETE;
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY){
            return EventType.MODIFY;
        } else if (kind == StandardWatchEventKinds.OVERFLOW){
            return EventType.MODIFY;
        } else {
            throw new IllegalStateException("Unknown watch kind " + kind);
        }
    }
}
