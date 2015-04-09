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

package org.gradle.launcher.exec;

import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.CompositeFileTree;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.invocation.BuildController;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by lari on 09/04/15.
 */
public class WatchModeBuildController extends AbstractBuildController {
    private final GradleLauncherFactory gradleLauncherFactory;
    private final StartParameter startParameter;
    private final BuildRequestContext buildRequestContext;
    private DefaultGradleLauncher currentLauncher;

    public WatchModeBuildController(GradleLauncherFactory gradleLauncherFactory, StartParameter startParameter, BuildRequestContext buildRequestContext) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.startParameter = startParameter;
        this.buildRequestContext = buildRequestContext;
    }

    @Override
    protected DefaultGradleLauncher getLauncher() {
        if(currentLauncher == null) {
            currentLauncher = (DefaultGradleLauncher)gradleLauncherFactory.newInstance(startParameter, buildRequestContext);
        }
        return currentLauncher;
    }

    @Override
    void stopLauncher() {
        currentLauncher.stop();
        currentLauncher = null;
    }

    @Override
    public GradleInternal run() {
        GradleInternal gradle = null;
        while(!buildRequestContext.getCancellationToken().isCancellationRequested()) {
            System.out.println("----- WATCH MODE -----");
            gradle = getGradle();
            TaskInputsTaskListener taskInputsListener = new TaskInputsTaskListener();
            gradle.addListener(taskInputsListener);
            super.run();
            System.out.println("-------- WAITING -------");
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return gradle;
    }

    private static class TaskInputsTaskListener implements TaskExecutionListener {
        @Override
        public void beforeExecute(Task task) {

        }

        @Override
        public void afterExecute(Task task, TaskState state) {
            addInputFiles(task.getInputs().getFiles());
        }

        private void addInputFiles(FileCollection files) {
            if(files instanceof UnionFileCollection) {
                for(FileCollection source : ((UnionFileCollection) files).getSources()) {
                    addInputFiles(source);
                }
            } else {
                debugPrintFileCollection(files);
            }
        }

        private void debugPrintFileCollection(FileCollection files) {
            System.out.println("files " + files + " implementation class:" + files.getClass().getName());
            FileTree fileTree = files.getAsFileTree();
            if (fileTree instanceof CompositeFileTree) {
                for (FileCollection sourceCollection : ((CompositeFileTree) fileTree).getSourceCollections()) {
                    System.out.println("sourceCollection: " + sourceCollection);
                    System.out.println("implementation class:" + sourceCollection.getClass().getName());
                    if(sourceCollection instanceof FileTreeAdapter) {
                        MinimalFileTree minimalFileTree = ((FileTreeAdapter)sourceCollection).getTree();
                        System.out.println("minimalFileTree:" + minimalFileTree);
                        System.out.println("implementation class:" + minimalFileTree.getClass().getName());
                        if(minimalFileTree instanceof DirectoryFileTree) {
                            DirectoryFileTree dirFileTree = (DirectoryFileTree)minimalFileTree;
                            System.out.println("Directory: " + dirFileTree.getDir() + " includes: " + dirFileTree.getPatterns().getIncludes() + " exclude:" + dirFileTree.getPatterns().getExcludes());
                        }
                    }
                }
            }
        }
    }
}
