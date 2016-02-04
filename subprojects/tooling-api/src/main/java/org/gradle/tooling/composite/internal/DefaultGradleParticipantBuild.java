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

package org.gradle.tooling.composite.internal;

import java.io.File;
import java.io.Serializable;
import java.net.URI;

class DefaultGradleParticipantBuild implements GradleParticipantBuild, Serializable {
    private final File gradleUserHomeDir;
    private final File projectDir;
    private File gradleHome;
    private URI gradleDistribution;
    private String gradleVersion;

    DefaultGradleParticipantBuild(File projectDir, File gradleUserHomeDir) {
        this.projectDir = projectDir;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    DefaultGradleParticipantBuild(File projectDir, File gradleUserHomeDir, File gradleHome) {
        this(projectDir, gradleUserHomeDir);
        this.gradleHome = gradleHome;
    }

    DefaultGradleParticipantBuild(File projectDir, File gradleUserHomeDir, String gradleVersion) {
        this(projectDir, gradleUserHomeDir);
        this.gradleVersion = gradleVersion;
    }

    DefaultGradleParticipantBuild(File projectDir, File gradleUserHomeDir, URI gradleDistribution) {
        this(projectDir, gradleUserHomeDir);
        this.gradleDistribution = gradleDistribution;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public File getGradleHome() {
        return gradleHome;
    }

    @Override
    public String getDisplayName() {
        return "build " + projectDir.getAbsolutePath();
    }

    public URI getGradleDistribution() {
        return gradleDistribution;
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion;
    }
}
