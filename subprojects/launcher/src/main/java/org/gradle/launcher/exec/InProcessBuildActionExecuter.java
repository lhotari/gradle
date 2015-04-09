/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;

public class InProcessBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final GradleLauncherFactory gradleLauncherFactory;
    private final BuildActionRunner buildActionRunner;

    public InProcessBuildActionExecuter(GradleLauncherFactory gradleLauncherFactory, BuildActionRunner buildActionRunner) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.buildActionRunner = buildActionRunner;
    }

    public Object execute(BuildAction action, BuildRequestContext buildRequestContext, BuildActionParameters actionParameters) {
        return doExecute(action, createBuildController(action, buildRequestContext, actionParameters));

    }

    protected BuildController createBuildController(BuildAction action, BuildRequestContext buildRequestContext, BuildActionParameters actionParameters) {
        BuildController buildController;
        if(action.getStartParameter().isWatchMode()) {
            buildController = createWatchModeBuildController(action, buildRequestContext, actionParameters);
        } else {
            buildController = createDefaultBuildController(action, buildRequestContext, actionParameters);
        }
        return buildController;
    }

    private BuildController createWatchModeBuildController(BuildAction action, BuildRequestContext buildRequestContext, BuildActionParameters actionParameters) {
        return new WatchModeBuildController(gradleLauncherFactory, action.getStartParameter(), buildRequestContext);
    }

    private BuildController createDefaultBuildController(BuildAction action, BuildRequestContext buildRequestContext, BuildActionParameters actionParameters) {
        DefaultGradleLauncher gradleLauncher = (DefaultGradleLauncher) gradleLauncherFactory.newInstance(action.getStartParameter(), buildRequestContext);
        return new DefaultBuildController(gradleLauncher);
    }

    private Object doExecute(BuildAction action, BuildController buildController) {
        buildActionRunner.run(action, buildController);
        return buildController.getResult();
    }
}
