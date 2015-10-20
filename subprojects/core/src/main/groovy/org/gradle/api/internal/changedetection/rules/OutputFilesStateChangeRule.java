/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.util.ChangeListener;

import java.util.Collections;
import java.util.Iterator;

/**
 * A rule which detects changes in output files.
 */
class OutputFilesStateChangeRule {

    public static TaskStateChanges create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileCollectionSnapshotter outputFilesSnapshotter, final FileCollectionSnapshotter.FileCollectionPreCheck outputFilesPrecheckBefore) {
        return new TaskStateChanges() {
            FileCollectionSnapshot outputFilesBefore;

            public void snapshotBeforeTask() {
                getOutputFilesBefore();
            }

            private FileCollectionSnapshot getOutputFilesBefore() {
                if (outputFilesBefore == null) {
                    outputFilesBefore = outputFilesSnapshotter.snapshot(outputFilesPrecheckBefore);
                }
                return outputFilesBefore;
            }

            public Iterator<TaskStateChange> iterator() {
                if (previousExecution.getOutputFilesSnapshot() == null) {
                    return Collections.<TaskStateChange>singleton(new DescriptiveChange("Output file history is not available.")).iterator();
                }

                return new AbstractIterator<TaskStateChange>() {
                    FileCollectionSnapshot.ChangeIterator<String> changeIterator;
                    final ChangeListenerAdapter listenerAdapter = new ChangeListenerAdapter();

                    @Override
                    protected TaskStateChange computeNext() {
                        if (changeIterator == null) {
                            changeIterator = getOutputFilesBefore().iterateChangesSince(previousExecution.getOutputFilesSnapshot());
                        }
                        if (changeIterator.next(listenerAdapter)) {
                            return listenerAdapter.lastChange;
                        }
                        return endOfData();
                    }
                };
            }

            public void snapshotAfterTask() {
                FileCollectionSnapshotter.FileCollectionPreCheck outputFilesPrecheckAfter = outputFilesSnapshotter.preCheck(task.getOutputs().getFiles());
                currentExecution.setOutputFilesHash(outputFilesPrecheckAfter.getHash());
                FileCollectionSnapshot outputFilesAfter = outputFilesSnapshotter.snapshot(outputFilesPrecheckAfter);
                currentExecution.setOutputFilesSnapshot(outputFilesAfter);
            }
        };
    }

    private static class ChangeListenerAdapter implements ChangeListener<String> {
        public OutputFileChange lastChange;

        public void added(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.ADDED);
        }

        public void removed(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.REMOVED);
        }

        public void changed(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.MODIFIED);
        }
    }
}
