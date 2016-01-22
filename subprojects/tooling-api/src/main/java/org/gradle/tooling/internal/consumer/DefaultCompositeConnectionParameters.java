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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultCompositeConnectionParameters extends AbstractConnectionParameters implements CompositeConnectionParameters {
    private final List<File> participantRootDirs;
    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ProjectConnectionParameters connectionParameters) {
        return (Builder) new Builder().setDaemonMaxIdleTimeUnits(connectionParameters.getDaemonMaxIdleTimeUnits()).
            setDaemonMaxIdleTimeValue(connectionParameters.getDaemonMaxIdleTimeValue()).
            setEmbedded(connectionParameters.isEmbedded()).
            setGradleUserHomeDir(connectionParameters.getGradleUserHomeDir()).
            setVerboseLogging(connectionParameters.getVerboseLogging());
    }

    @Override
    public List<File> getParticipantRootDirs() {
        return participantRootDirs;
    }

    public static class Builder extends AbstractConnectionParameters.Builder {
        private final List<File> participantRootDirs = new ArrayList<File>();

        private Builder() {
            super();
        }

        public DefaultCompositeConnectionParameters build() {
            return new DefaultCompositeConnectionParameters(gradleUserHomeDir, embedded,
                daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging, participantRootDirs);
        }

        public void addParticipant(File rootProjectDirectory) {
            participantRootDirs.add(rootProjectDirectory);
        }
    }

    private DefaultCompositeConnectionParameters(File gradleUserHomeDir, Boolean embedded,
                                                 Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                                 boolean verboseLogging, List<File> participantRootDirs) {
        super(gradleUserHomeDir, embedded, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging);
        this.participantRootDirs = Collections.unmodifiableList(participantRootDirs);
    }
}
