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

package org.gradle.internal.filewatch

import org.gradle.api.JavaVersion
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !JavaVersion.current().java7Compatible })
class WatchServiceFileWatcherTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    FileWatcherFactory fileWatcherFactory
    long waitForEventsMillis = OperatingSystem.current().isMacOsX() ? 3500L : 2000L
    FileWatcher fileWatcher

    void setup() {
        NativeServicesTestFixture.initialize()
        fileWatcherFactory = new DefaultFileWatcherFactory(new DefaultExecutorFactory())
    }

    void cleanup() {
        fileWatcher?.stop()
        fileWatcherFactory.stop()
    }

    def "watch service should notify of new files"() {
        given:
        def listener = Mock(FileWatchListener)
        when:
        fileWatcher = fileWatcherFactory.watch([testDir.getTestDirectory()], listener)
        File createdFile = testDir.file("newfile.txt")
        createdFile.text = "Hello world"
        waitForChanges()
        then:
        (1.._) * listener.onChange(_)
    }

    def "watch service should notify of new files in subdirectories"() {
        given:
        def listener = Mock(FileWatchListener)
        when:
        fileWatcher = fileWatcherFactory.watch([testDir.getTestDirectory()], listener)
        def subdir = testDir.createDir("subdir")
        subdir.createFile("somefile").text = "Hello world"
        waitForChanges()
        then:
        (1.._) * listener.onChange(_)
        when:
        subdir.file('someotherfile').text = "Hello world"
        waitForChanges()
        then:
        (1.._) * listener.onChange(_)
    }

    def "watch service should register to watch subdirs at startup"() {
        given:
        def listener = Mock(FileWatchListener)
        def subdir = testDir.createDir("subdir")
        when:
        fileWatcher = fileWatcherFactory.watch([testDir.getTestDirectory()], listener)
        subdir.createFile("somefile").text = "Hello world"
        waitForChanges()
        then:
        (1.._) * listener.onChange(_)
    }

    private void waitForChanges() {
        sleep(waitForEventsMillis)
    }
}
