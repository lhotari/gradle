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

package org.gradle.composite;

import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.net.URI;
import java.util.Map;

public abstract class CompositeBuildConnector {
    public static CompositeBuildConnector newComposite() {
        return null;
    }
    // Define the Gradle instance that coordinate the composite
    protected abstract CompositeBuildConnection useInstallation(File gradleHome);
    protected abstract CompositeBuildConnection useGradleVersion(String gradleVersion);
    protected abstract CompositeBuildConnection useDistribution(URI gradleDistribution);

    // Define the Gradle user home directory for the entire composite
    protected abstract CompositeParticipant useGradleUserHomeDir(File gradleUserHomeDir);

    protected abstract CompositeParticipant addParticipant(File rootProjectDirectory);

    protected abstract CompositeBuildConnection connect();

    interface CompositeParticipant {
        // Define the Gradle instance that should build this participant
        CompositeParticipant useBuildDistribution(); // default
        CompositeParticipant useInstallation(File gradleHome);
        CompositeParticipant useGradleVersion(String gradleVersion);
        CompositeParticipant useDistribution(URI gradleDistribution);
    }

    public static void main(String[] args) {
        CompositeBuildConnector compositeBuildConnector = CompositeBuildConnector.newComposite();
        compositeBuildConnector.addParticipant(new File("foo"));
        compositeBuildConnector.addParticipant(new File("bar"));
        CompositeBuildConnection compositeBuildConnection = compositeBuildConnector.connect();

        Map<ProjectIdentity, EclipseProject> buildModels = compositeBuildConnection.getModels(EclipseProject.class);
    }
}
