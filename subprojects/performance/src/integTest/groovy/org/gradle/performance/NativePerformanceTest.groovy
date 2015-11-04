/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.GradleInvocationCustomizer
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

class NativePerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll('Project #type native build')
    def "build" () {
        given:
        runner.testId = "native build ${type}"
        runner.testProject = "${type}Native"
        runner.tasksToRun = [ "clean", "assemble" ]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = [ '2.3', '2.4', '2.8', 'last' ]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type           | maxExecutionTimeRegression
        "small"        | millis(500)
        "medium"       | millis(500)
        "big"          | millis(1500)
        "multi"        | millis(1500)
    }

    def "Many projects native build" () {
        given:
        runner.testId = "native build many projects"
        runner.testProject = "manyProjectsNative"
        runner.tasksToRun = [ "clean", "assemble" ]
        runner.maxExecutionTimeRegression = millis(1000)
        runner.targetVersions = [ '2.3', '2.4', '2.8', 'last' ]
        runner.useDaemon = true

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @Unroll('Project #type native build 1 change')
    def "build with 1 change"() {
        given:
        runner.testId = "native build ${type} 1 change"
        runner.testProject = "${type}NativeMonolithic"
        runner.tasksToRun = ["assemble"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', 'last']
        runner.buildExperimentListener = new BuildExperimentListener() {
            @Override
            GradleInvocationCustomizer createInvocationCustomizer(BuildExperimentInvocationInfo invocationInfo) {
                null
            }

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if(invocationInfo.iterationNumber % 2 == 0) {
                    // do change

                } else if (invocationInfo.iterationNumber > 2) {
                    // remove change

                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if(invocationInfo.iterationNumber % 2 == 1) {
                    measurementCallback.omitMeasurement()
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type     | maxExecutionTimeRegression
        "small"  | millis(1000)
        "medium" | millis(5000)
    }
}
