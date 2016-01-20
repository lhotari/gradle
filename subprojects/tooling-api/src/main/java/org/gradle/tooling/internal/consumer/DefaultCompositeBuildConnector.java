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

import org.gradle.tooling.*;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;

public class DefaultCompositeBuildConnector extends CompositeBuildConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(GradleConnector.class);
    private final CompositeConnectionFactory connectionFactory;
    private final DistributionFactory distributionFactory;
    private Distribution distribution;

    private final DefaultCompositeConnectionParameters.Builder connectionParamsBuilder = DefaultCompositeConnectionParameters.builder();

    public DefaultCompositeBuildConnector(CompositeConnectionFactory connectionFactory, DistributionFactory distributionFactory) {
        this.connectionFactory = connectionFactory;
        this.distributionFactory = distributionFactory;
    }

    @Override
    protected CompositeBuildConnector useInstallation(File gradleHome) {
        distribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    @Override
    protected CompositeBuildConnector useGradleVersion(String gradleVersion) {
        distribution = distributionFactory.getDistribution(gradleVersion);
        return this;
    }

    @Override
    protected CompositeBuildConnector useDistribution(URI gradleDistribution) {
        distribution = distributionFactory.getDistribution(gradleDistribution);
        return this;
    }

    @Override
    protected CompositeBuildConnector useGradleUserHomeDir(File gradleUserHomeDir) {
        connectionParamsBuilder.setGradleUserHomeDir(gradleUserHomeDir);
        return this;
    }

    @Override
    protected CompositeParticipant addParticipant(File rootProjectDirectory) {
        // no-op
        return new CompositeParticipant() {
            @Override
            public CompositeParticipant useBuildDistribution() {
                return this;
            }
        };
    }

    @Override
    public CompositeBuildConnection connect() throws GradleConnectionException {
        LOGGER.debug("Connecting from tooling API consumer version {}", GradleVersion.current().getVersion());

        CompositeConnectionParameters connectionParameters = connectionParamsBuilder.build();
        if (distribution == null) {
            useClasspathDistribution();
        }
        return connectionFactory.create(distribution, connectionParameters);
    }

    public CompositeBuildConnector useClasspathDistribution() {
        distribution = distributionFactory.getClasspathDistribution();
        return this;
    }
}
