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

package org.gradle.launcher.exec;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.protocol.GradleParticipantBuild;
import org.gradle.tooling.internal.protocol.eclipse.DefaultSetOfEclipseProjects;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeBuildActionExecuter implements BuildActionExecuter<CompositeBuildActionParameters> {

    public CompositeBuildActionExecuter() {

    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, ServiceRegistry contextServices) {
        if (action instanceof BuildModelAction) {
            Set<Object> projects = getEclipseProjects(actionParameters);
            DefaultSetOfEclipseProjects workspace = new DefaultSetOfEclipseProjects(projects);
            PayloadSerializer payloadSerializer = contextServices.get(PayloadSerializer.class);
            return new BuildActionResult(payloadSerializer.serialize(workspace), null);
        } else {
            throw new RuntimeException("Not implemented yet.");
        }
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
