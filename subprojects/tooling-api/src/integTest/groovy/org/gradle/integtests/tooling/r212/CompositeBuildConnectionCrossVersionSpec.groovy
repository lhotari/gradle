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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.IgnoreRest

@ToolingApiVersion("current")
@TargetGradleVersion("current")
class CompositeBuildConnectionCrossVersionSpec extends ToolingApiSpecification {

    def "can create a composite connection"() {
        expect:
        withGradleConnection {
            assert it != null
            true
        }
    }

    def "can request model for a single project"() {
        given:
        buildFile << "apply plugin: 'java'"
        toolingApi.requireDaemons()


        when:
        withGradleConnectionBuilder { builder ->
            builder.addBuild(projectDir)
        }
        def eclipseProjects = withGradleConnection { connection ->
            connection.getModels(EclipseProject)
        }

        then:
        eclipseProjects != null
        eclipseProjects.size() == 1
    }

    @IgnoreRest
    def "can request model for two projects"() {
        given:
        def projectDir1 = temporaryFolder.createDir("project1")
        def projectDir2 = temporaryFolder.createDir("project2")
        [projectDir1, projectDir2].each {
            it.file("build.gradle") << "apply plugin: 'java'"
        }
        when:
        withGradleConnectionBuilder { builder ->
            builder.addBuild(projectDir1)
            builder.addBuild(projectDir2)
        }
        def eclipseProjects = withGradleConnection { connection ->
            connection.models(EclipseProject)
            //.setJvmArguments('-Xmx1G', '-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005')
                .get()
        }

        then:
        eclipseProjects != null
        eclipseProjects.size() == 2
    }
}
