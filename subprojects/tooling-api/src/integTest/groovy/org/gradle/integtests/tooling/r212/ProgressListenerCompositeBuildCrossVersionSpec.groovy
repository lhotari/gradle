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

package org.gradle.integtests.tooling.r212

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * Tooling client provides progress listener for composite model request
 */
class ProgressListenerCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def "compare events from a composite build and a regular build"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        def progressListenerForComposite = new CapturingProgressListener()
        withCompositeConnection(singleBuild) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.addProgressListener(progressListenerForComposite)
            modelBuilder.get()
        }

        def progressListenerForRegularBuild = new CapturingProgressListener()
        toolingApi.withConnection { ProjectConnection connection ->
            def modelBuilder = connection.model(EclipseProject)
            modelBuilder.addProgressListener(progressListenerForRegularBuild)
            modelBuilder.get()
        }

        then:
        progressListenerForComposite.eventDescriptions.size() > 0
        progressListenerForComposite.eventDescriptions == progressListenerForRegularBuild.eventDescriptions
    }

    static class CapturingProgressListener implements ProgressListener {
        def eventDescriptions = []

        @Override
        void statusChanged(ProgressEvent event) {
            eventDescriptions.add(event.description)
        }
    }
}
