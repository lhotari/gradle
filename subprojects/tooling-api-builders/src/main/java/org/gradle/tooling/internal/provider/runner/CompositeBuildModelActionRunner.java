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

import com.google.common.collect.Sets;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.CompositeBuildActionParameters;
import org.gradle.launcher.exec.CompositeBuildActionRunner;
import org.gradle.launcher.exec.CompositeBuildController;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.GradleCompositeException;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.protocol.eclipse.DefaultSetOfEclipseProjects;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.connection.GradleParticipantBuild;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    @Override
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        Set<Object> projects = getEclipseProjects(actionParameters, requestContext.getCancellationToken());
        DefaultSetOfEclipseProjects workspace = new DefaultSetOfEclipseProjects(projects);
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(workspace), null));
    }

    private Set<Object> getEclipseProjects(CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken) {
        Set<Object> results = new LinkedHashSet<Object>();
        results.addAll(fetchModels(actionParameters.getCompositeParameters().getBuilds(), EclipseProject.class, cancellationToken));
        return results;
    }

    private <T> Set<T> fetchModels(List<GradleParticipantBuild> participantBuilds, Class<T> modelType, final BuildCancellationToken cancellationToken) {
        final Set<T> results = Sets.newConcurrentHashSet();
        final AtomicReference<Throwable> firstFailure = new AtomicReference<Throwable>();
        final CountDownLatch countDownLatch = new CountDownLatch(participantBuilds.size());
        for (GradleParticipantBuild participant : participantBuilds) {
            // TODO: connection doesn't get closed
            ProjectConnection projectConnection = connect(participant);
            ModelBuilder<T> modelBuilder = projectConnection.model(modelType);
            if (cancellationToken != null) {
                modelBuilder.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
            }
            modelBuilder.get(new ProjectResultHandler<T>(countDownLatch, results, firstFailure));
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            UncheckedException.throwAsUncheckedException(e);
        }
        if (firstFailure.get() != null) {
            throw new GradleCompositeException("Error retrieving model", firstFailure.get());
        }
        return results;
    }

    private ProjectConnection connect(GradleParticipantBuild build) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(build.getProjectDir());
        configureDistribution(connector, build);
        return connector.connect();
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

    private final static class CancellationTokenAdapter implements CancellationToken, CancellationTokenInternal {
        private final BuildCancellationToken token;

        private CancellationTokenAdapter(BuildCancellationToken token) {
            this.token = token;
        }

        @Override
        public boolean isCancellationRequested() {
            return token.isCancellationRequested();
        }

        @Override
        public BuildCancellationToken getToken() {
            return token;
        }
    }

    private final static class ProjectResultHandler<T> implements ResultHandler<T> {
        private final Set<T> results;
        private final CountDownLatch countDownLatch;
        private final AtomicReference<Throwable> firstFailure;

        private ProjectResultHandler(CountDownLatch countDownLatch, Set<T> results, AtomicReference<Throwable> firstFailure) {
            this.countDownLatch = countDownLatch;
            this.results = results;
            this.firstFailure = firstFailure;
        }

        @Override
        public void onComplete(T result) {
            results.add(result);
            countDownLatch.countDown();
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            firstFailure.compareAndSet(null, failure);
            countDownLatch.countDown();
        }
    }
}
