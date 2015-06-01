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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import spock.lang.Timeout

import java.util.concurrent.atomic.AtomicInteger

@Timeout(60)
class ContinuousBuildCrossVersionSpec extends ContinuousBuildToolingApiSpecification {
    enum ProgressListenerType {
        OLD,
        NEW
    }

    def progressListener

    void customizeLauncher(BuildLauncher launcher) {
        if (progressListener) {
            launcher.addProgressListener(progressListener)
        }
    }

    def "client executes continuous build that succeeds, then responds to input changes and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'

        when:
        succeeds('build')

        then:
        executedAndNotSkipped ":compileJava", ":build"

        when:
        javaSrcFile.text = 'public class Thing { public static final int FOO=1; }'

        then:
        succeeds()
        executedAndNotSkipped ":compileJava", ":build"
    }

    def "client executes continuous build that succeeds, then responds to input changes and fails, then … and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'

        when:
        succeeds('build')

        then:
        executedAndNotSkipped ":compileJava", ":build"

        when:
        javaSrcFile.text = 'public class Thing { *******'

        then:
        fails()

        when:
        javaSrcFile.text = 'public class Thing {} '

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "client executes continuous build that fails, then responds to input changes and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {'

        when:
        fails('build')

        then:
        noExceptionThrown()

        when:
        javaSrcFile.text = 'public class Thing {} '

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "client can receive appropriate logging and progress events for subsequent builds"(ProgressListenerType progressListenerTypeToUse) {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'
        AtomicInteger buildCounter = new AtomicInteger(0)
        AtomicInteger eventCounter = new AtomicInteger(0)
        int lastEventCounter
        progressListener = progressListenerTypeToUse == ProgressListenerType.OLD ? createOldListener(buildCounter, eventCounter) : createNewListener(buildCounter, eventCounter)

        when:
        succeeds('build')

        then:
        eventCounter.get() > 0
        buildCounter.get() == 1

        when:
        lastEventCounter = eventCounter.get()
        javaSrcFile.text = 'public class Thing { public static final int FOO=1; }'

        then:
        succeeds()
        eventCounter.get() > lastEventCounter
        buildCounter.get() == 2

        when:
        lastEventCounter = eventCounter.get()
        javaSrcFile.text = 'public class Thing {}'

        then:
        succeeds()
        eventCounter.get() > lastEventCounter
        buildCounter.get() == 3

        where:
        progressListenerTypeToUse << (isClassAvailable("org.gradle.tooling.events.ProgressListener") ? ProgressListenerType.values() : [ProgressListenerType.OLD])
    }

    private boolean isClassAvailable(String className) {
        try {
            this.getClass().getClassLoader().loadClass(className);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    def createNewListener(buildCounter, eventCounter) {
        return {
            eventCounter.incrementAndGet()
            if (it instanceof FinishEvent && it.descriptor.name == 'Running build') {
                buildCounter.incrementAndGet()
            }
        } as org.gradle.tooling.events.ProgressListener
    }

    def createOldListener(buildCounter, eventCounter) {
        return {
            eventCounter.incrementAndGet()
            if (it.description == 'Build') {
                buildCounter.incrementAndGet()
            }
        } as org.gradle.tooling.ProgressListener
    }

    def "client can request continuous mode when building a model, but request is effectively ignored"() {
        given:
        setupJavaProject()
        stderr = new ByteArrayOutputStream(512)
        stdout = new ByteArrayOutputStream(512)

        when:
        BuildEnvironment buildEnvironment = withConnection { ProjectConnection connection ->
            connection.model(BuildEnvironment.class)
                .withArguments("--continuous")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        !containsContinuousBuildOutput(stdout)
        buildEnvironment.gradle.gradleVersion != null

        when:
        stdout.reset()
        stderr.reset()
        GradleProject gradleProject = withConnection { ProjectConnection connection ->
            connection.model(GradleProject.class)
                .withArguments("--continuous")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        !containsContinuousBuildOutput(stdout)
        gradleProject.buildDirectory != null

        when: 'running task before fetching model'
        stdout.reset()
        stderr.reset()
        gradleProject = withConnection { ProjectConnection connection ->
            connection.model(GradleProject.class)
                .withArguments("--continuous")
                .forTasks("classes")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        !containsContinuousBuildOutput(stdout)
        gradleProject.buildDirectory != null
    }

    private boolean containsContinuousBuildOutput(stdout) {
        String logOutput = stdout.toString()
        return logOutput.contains("Continuous build is an incubating feature.") ||
            logOutput.contains("Waiting for changes to input files of tasks...") ||
            logOutput.contains("Exiting continuous build as no executed tasks declared file system inputs.")
    }
}
