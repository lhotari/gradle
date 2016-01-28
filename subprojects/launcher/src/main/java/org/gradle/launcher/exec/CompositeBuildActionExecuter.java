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
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeBuildActionExecuter implements BuildActionExecuter<CompositeBuildActionParameters> {

    public CompositeBuildActionExecuter() {

    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, ServiceRegistry contextServices) {
        if (action instanceof BuildModelAction) {
            Set<EclipseProject> result = getEclipseProjects(actionParameters);
            PayloadSerializer payloadSerializer = contextServices.get(PayloadSerializer.class);
            return new BuildActionResult(payloadSerializer.serialize(result), null);
        } else {
            throw new RuntimeException("Not implemented yet.");
        }
    }

    private Set<EclipseProject> getEclipseProjects(CompositeBuildActionParameters actionParameters) {
        Set<EclipseProject> result = new LinkedHashSet<EclipseProject>();
        for (File projectRoot : actionParameters.getCompositeParameters().getBuildRoots()) {
            ProjectConnection projectConnection = GradleConnector.newConnector().forProjectDirectory(projectRoot).connect();
            result.add(projectConnection.getModel(EclipseProject.class));
            projectConnection.close();
        }
        return result;
    }
}
