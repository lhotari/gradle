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
import org.gradle.tooling.model.eclipse.EclipseProject

class CompositeBuildConnectionCrossVersionSpec extends CompositeToolingApiSpecification {

    def "can request model for two projects"() {
        given:
        def projectDir1 = populate("project1") {
            buildFile << "apply plugin: 'java'"
        }
        def projectDir2 = populate("project2") {
            buildFile << "apply plugin: 'java'"
        }
        when:
        def eclipseProjects = withCompositeConnection([projectDir1, projectDir2]) { connection ->
            connection.models(EclipseProject)
            //.setJvmArguments('-Xmx1G', '-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005')
                .get()
        }

        then:
        eclipseProjects != null
        eclipseProjects.size() == 2

        eclipseProjects.find {
            it.name == 'project1'
        }.projectDirectory.absolutePath == projectDir1.absolutePath
        eclipseProjects.find {
            it.name == 'project2'
        }.projectDirectory.absolutePath == projectDir2.absolutePath
    }
}
