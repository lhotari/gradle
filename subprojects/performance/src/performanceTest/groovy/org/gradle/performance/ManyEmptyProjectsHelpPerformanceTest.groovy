/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.BasicPerformanceTest
import org.junit.experimental.categories.Category

@Category(BasicPerformanceTest)
class ManyEmptyProjectsHelpPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "many empty projects help"() {
        given:
        runner.testId = "many empty projects help"
        runner.testProject = "bigEmpty"
        runner.tasksToRun = ['help']
        runner.targetVersions = ['3.1-20160818000032+0000', 'last']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "many empty projects help (daemon)"() {
        given:
        runner.testId = "many empty projects help (daemon)"
        runner.testProject = "bigEmpty"
        runner.tasksToRun = ['help']
        runner.targetVersions = ['3.1-20160818000032+0000', 'last']
        runner.useDaemon = true

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
