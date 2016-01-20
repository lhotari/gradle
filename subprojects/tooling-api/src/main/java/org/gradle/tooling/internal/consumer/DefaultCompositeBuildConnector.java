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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.CompositeBuildConnection;
import org.gradle.tooling.CompositeBuildConnector;
import org.gradle.tooling.CompositeParticipant;

import java.io.File;
import java.net.URI;

public class DefaultCompositeBuildConnector extends CompositeBuildConnector {
    public DefaultCompositeBuildConnector(CompositeConnectionFactory connectionFactory, DistributionFactory distributionFactory) {

    }

    @Override
    protected CompositeBuildConnection useInstallation(File gradleHome) {
        return null;
    }

    @Override
    protected CompositeBuildConnection useGradleVersion(String gradleVersion) {
        return null;
    }

    @Override
    protected CompositeBuildConnection useDistribution(URI gradleDistribution) {
        return null;
    }

    @Override
    protected CompositeBuildConnection useGradleUserHomeDir(File gradleUserHomeDir) {
        return null;
    }

    @Override
    protected CompositeParticipant addParticipant(File rootProjectDirectory) {
        return null;
    }

    @Override
    protected CompositeBuildConnection connect() {
        return null;
    }
}
