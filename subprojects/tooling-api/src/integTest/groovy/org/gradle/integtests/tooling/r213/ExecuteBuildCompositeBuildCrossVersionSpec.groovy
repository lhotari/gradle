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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification

/**
 * Tooling client can define a composite and execute tasks
 */
class ExecuteBuildCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def "executes task in a single project within a composite "() {
        given:
        embedCoordinatorAndParticipants = true
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
"""
        }
        def build2 = populate("build2") {
            buildFile << "apply plugin: 'java'"
        }
        when:
        def build1Id = createGradleBuildParticipant(build1).toBuildIdentity()
        withCompositeConnection([build1, build2]) { connection ->
            def buildLauncher = connection.newBuild(build1Id)
            buildLauncher.forTasks("hello")
            buildLauncher.run()
        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'
    }
}
