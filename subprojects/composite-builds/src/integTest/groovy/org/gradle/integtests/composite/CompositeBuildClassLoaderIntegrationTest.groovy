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

package org.gradle.integtests.composite

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.build.BuildTestFile

/**
 * Tests for classloader related bugs with a composite build.
 */
class CompositeBuildClassLoaderIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        dependency 'org.test:buildB:1.0'

        buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        file('gradle-user-home/init.gradle') << """
initscript {
  repositories {
    mavenLocal()
    mavenCentral()
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }

  dependencies {
    File searchDir = gradle.startParameter.projectDir ?: gradle.startParameter.currentDir
    new File(searchDir, 'plugins.txt').text.trim().split(';').each {
      classpath it.trim()
    }
  }
}

gradle.apply plugin: com.example.InitPlugin
"""
        includedBuilds << buildB
    }

    def "uses conventional init-script in included build"() {

        given:
        [buildA, buildB].each {
            it.buildFile << """
    if (project.initProperty != "foo") {
        throw new RuntimeException("init script property not passed to build")
    }
"""
        }

        when:
        executer.withGradleUserHomeDir(file('gradle-user-home'))
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar"
    }
}
