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

package org.gradle.internal.filewatch;

import com.google.common.collect.Maps;
import org.gradle.logging.StyledTextOutput;

import java.io.File;
import java.util.Map;

import static org.gradle.internal.filewatch.FileWatcherEvent.Type.*;

public class ChangeReporter implements FileWatcherEventListener {
    public static final int SHOW_INDIVIDUAL_CHANGES_LIMIT = 3;
    private final Map<File, FileWatcherEvent.Type> aggregatedEvents = Maps.newLinkedHashMap();

    private void logOutput(StyledTextOutput logger, String message, Object... objects) {
        logger.formatln(message, objects);
    }

    @Override
    public void onChange(FileWatcherEvent event) {
        if (event.getType() == UNDEFINED) {
            return;
        }

        File file = event.getFile();
        FileWatcherEvent.Type existingType = aggregatedEvents.get(file);

        if (existingType == event.getType()
            || existingType == CREATE && event.getType() == MODIFY) {
            return;
        }

        aggregatedEvents.put(file, event.getType());
    }

    public void reportChanges(StyledTextOutput logger) {
        int counter = 0;
        for (Map.Entry<File, FileWatcherEvent.Type> entry : aggregatedEvents.entrySet()) {
            counter++;
            if (counter > SHOW_INDIVIDUAL_CHANGES_LIMIT) {
                logOutput(logger, "and %d more changes", aggregatedEvents.size() - SHOW_INDIVIDUAL_CHANGES_LIMIT);
                break;
            }
            FileWatcherEvent.Type changeType = entry.getValue();
            File file = entry.getKey();
            showIndividualChange(logger, file, changeType);
        }
    }

    private void showIndividualChange(StyledTextOutput logger, File file, FileWatcherEvent.Type changeType) {
        String changeDescription;
        switch (changeType) {
            case CREATE:
                changeDescription = "new " + (file.isDirectory() ? "directory" : "file");
                break;
            case DELETE:
                changeDescription = "deleted";
                break;
            case MODIFY:
            default:
                changeDescription = "modified";
        }
        logOutput(logger, "%s: %s", changeDescription, file.getAbsolutePath());
    }
}
