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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.invocation.BuildController;

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
            gradle.addListener(new TaskInputsTaskListener());
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
            System.out.println("inputs to " + task.getPath() + " are " + task.getInputs());
        }
    }
}
