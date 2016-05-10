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

package org.gradle.api.internal.changedetection.state;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.file.BackingFileExtractor;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.execution.taskgraph.TaskInfo;
import org.gradle.internal.Cast;
import org.gradle.util.CollectionUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CachingTreeVisitorCleaner implements Closeable {
    private final Gradle gradle;
    private final CacheCleaner buildListener;

    public CachingTreeVisitorCleaner(CachingTreeVisitor cachingTreeVisitor, Gradle gradle) {
        this.gradle = gradle;
        buildListener = new CacheCleaner(cachingTreeVisitor);
        gradle.addBuildListener(buildListener);
        CacheExpirationStrategy cacheExpirationStrategy = new CacheExpirationStrategy(cachingTreeVisitor);
        gradle.getTaskGraph().addTaskExecutionGraphListener(cacheExpirationStrategy);
        gradle.getTaskGraph().addTaskExecutionListener(cacheExpirationStrategy);
    }

    @Override
    public void close() throws IOException {
        gradle.removeListener(buildListener);
    }

    private static class CacheExpirationStrategy implements TaskExecutionGraphListener, TaskExecutionListener {
        private final CachingTreeVisitor cachingTreeVisitor;

        public CacheExpirationStrategy(CachingTreeVisitor cachingTreeVisitor) {
            this.cachingTreeVisitor = cachingTreeVisitor;
        }

        @Override
        public void graphPopulated(TaskExecutionGraph graph) {
            TaskGraphExecuter graphExecuter = Cast.cast(TaskGraphExecuter.class, graph);
            for(TaskInfo taskInfo : graphExecuter.getAllTaskInfos()) {
                Task task = taskInfo.getTask();
                List<File> inputs = extractFiles(task.getInputs().getFiles());
                List<File> outputs = extractFiles(task.getOutputs().getFiles());
            }
        }

        private List<File> extractFiles(FileCollection files) {
            List<BackingFileExtractor.FileEntry> entries = new BackingFileExtractor().extractFilesOrDirectories(files);
            return CollectionUtils.collect(entries, new Transformer<File, BackingFileExtractor.FileEntry>() {
                @Override
                public File transform(BackingFileExtractor.FileEntry fileEntry) {
                    return fileEntry.getFile().getAbsoluteFile();
                }
            });
        }

        @Override
        public void beforeExecute(Task task) {

        }

        @Override
        public void afterExecute(Task task, TaskState state) {

        }
    }

    private static class CacheCleaner implements BuildListener {
        private final CachingTreeVisitor cachingTreeVisitor;

        public CacheCleaner(CachingTreeVisitor cachingTreeVisitor) {
            this.cachingTreeVisitor = cachingTreeVisitor;
        }

        @Override
        public void buildFinished(BuildResult result) {
            cachingTreeVisitor.clearCache();
        }

        @Override
        public void buildStarted(Gradle gradle) {

        }

        @Override
        public void settingsEvaluated(Settings settings) {

        }

        @Override
        public void projectsLoaded(Gradle gradle) {

        }

        @Override
        public void projectsEvaluated(Gradle gradle) {

        }

    }
}
