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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.CompositeBuildActionRunner;
import org.gradle.launcher.exec.CompositeBuildController;
import org.gradle.launcher.exec.CompositeBuildActionParameters;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.provider.connection.GradleParticipantBuild;
import org.gradle.tooling.internal.protocol.eclipse.DefaultSetOfEclipseProjects;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    @Override
    public void run(BuildAction action, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        Set<Object> projects = getEclipseProjects(actionParameters);
        DefaultSetOfEclipseProjects workspace = new DefaultSetOfEclipseProjects(projects);
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(workspace), null));
    }

    private Set<Object> getEclipseProjects(CompositeBuildActionParameters actionParameters) {
        Set<Object> result = new LinkedHashSet<Object>();
        for (GradleParticipantBuild build : actionParameters.getCompositeParameters().getBuilds()) {
            ProjectConnection projectConnection = connect(build);
            try {
                result.add(projectConnection.getModel(EclipseProject.class));
            } finally {
                projectConnection.close();
            }
        }
        return result;
    }

    private ProjectConnection connect(GradleParticipantBuild build) {
        return configureDistribution(GradleConnector.newConnector().forProjectDirectory(build.getProjectDir()), build).connect();
    }

    private GradleConnector configureDistribution(GradleConnector connector, GradleParticipantBuild build) {
        if (build.getGradleDistribution() == null) {
            if (build.getGradleHome() == null) {
                if (build.getGradleVersion() == null) {
                    connector.useBuildDistribution();
                } else {
                    connector.useGradleVersion(build.getGradleVersion());
                }
            } else {
                connector.useInstallation(build.getGradleHome());
            }
        } else {
            connector.useDistribution(build.getGradleDistribution());
        }

        return connector;
    }

}
