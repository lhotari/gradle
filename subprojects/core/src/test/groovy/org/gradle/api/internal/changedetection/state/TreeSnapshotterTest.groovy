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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class TreeSnapshotterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    @Subject
    TreeSnapshotter treeSnapshotter
    File rootDir
    File fileStoreDir
    def additionalInputs = []

    def setup() {
        rootDir = testDir.createDir("root")
        fileStoreDir = testDir.createDir("caches/modules-2/files-2.1")
        treeSnapshotter = new TreeSnapshotter(fileStoreDir)
    }

    def "should return list of file details and cache it once"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees()

        when:
        def fileDetails = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], true)

        then:
        fileDetails.size() == 8
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 5
        treeSnapshotter.cachedTrees.size() == 1

        when:
        def fileDetails2 = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], true)

        then:
        fileDetails2 == fileDetails
        treeSnapshotter.cachedTrees.size() == 1
    }

    def "should not cache list of file details when there is a pattern or filter"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees(includePattern, includeFilter)

        when:
        def fileDetails = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], true)

        then:
        fileDetails.size() == 6
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 3
        treeSnapshotter.cachedTrees.size() == 0

        when:
        def fileDetails2 = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], true)

        then:
        !fileDetails2.is(fileDetails)
        fileDetails2.collect { it.file } as Set == fileDetails.collect { it.file } as Set
        treeSnapshotter.cachedTrees.size() == 0

        where:
        includePattern | includeFilter
        "**/*.txt"     | null
        null           | "**/*.txt"
        "**/*.txt"     | "**/*.txt"
    }

    def "should not use cached when allowReuse == false but should still add it to cache"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees()

        when:
        def fileDetails = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], false)

        then:
        fileDetails.size() == 8
        treeSnapshotter.cachedTrees.size() == 1

        when:
        def fileDetails2 = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], false)

        then:
        !fileDetails2.is(fileDetails)
        fileDetails2.collect { it.file } as Set == fileDetails.collect { it.file } as Set
        treeSnapshotter.cachedTrees.size() == 1

        when:
        def fileDetails3 = treeSnapshotter.visitTreeForSnapshotting(fileTrees[0], true)

        then:
        fileDetails3.is(fileDetails2)
        treeSnapshotter.cachedTrees.size() == 1
    }

    def "should cache files in file store directory"() {
        given:
        createSampleFiles()
        def jarfiles = [fileStoreDir.createFile("commons-lang/commons-lang/2.5/b0236b252e86419eef20c31a44579d2aee2f0a69/commons-lang-2.5.jar"),
                        fileStoreDir.createFile("log4j/log4j/1.2.17/5af35056b4d257e4b64b9e8069c0746e8b08629f/log4j-1.2.17.jar")]
        additionalInputs.addAll(jarfiles)
        def fileTrees = resolveAsFileTrees()
        def fileDetails = []

        when:
        fileTrees.each { fileDetails.addAll(treeSnapshotter.visitTreeForSnapshotting(it, true)) }

        then:
        fileDetails.size() == 10
        treeSnapshotter.cachedTrees.size() == 1
        treeSnapshotter.cachedFileStoreFiles.size() == 2

        when: 'cache is cleared'
        treeSnapshotter.clearCache()

        then: 'file store files should not be cleared'
        treeSnapshotter.cachedFileStoreFiles.size() == 2
        treeSnapshotter.cachedTrees.size() == 0
    }

    def "should not cache files in other than file store directory"() {
        given:
        createSampleFiles()
        def jarfiles = [testDir.createFile("commons-lang/commons-lang/2.5/b0236b252e86419eef20c31a44579d2aee2f0a69/commons-lang-2.5.jar"),
                        testDir.createFile("log4j/log4j/1.2.17/5af35056b4d257e4b64b9e8069c0746e8b08629f/log4j-1.2.17.jar")]
        additionalInputs.addAll(jarfiles)
        def fileTrees = resolveAsFileTrees()
        def fileDetails = []

        when:
        fileTrees.each { fileDetails.addAll(treeSnapshotter.visitTreeForSnapshotting(it, true)) }

        then:
        fileDetails.size() == 10
        treeSnapshotter.cachedTrees.size() == 1
        treeSnapshotter.cachedFileStoreFiles.size() == 0
    }

    private def createSampleFiles() {
        [rootDir.createFile("a/file1.txt"),
         rootDir.createFile("a/b/file2.txt"),
         rootDir.createFile("a/b/c/file3.txt"),
         rootDir.createFile("a/b/c/file4.md"),
         rootDir.createFile("a/file5.md"),]
    }

    private List<FileTreeInternal> resolveAsFileTrees(includePattern = null, includeFilter = null) {
        def fileResolver = TestFiles.resolver()

        def directorySet = new DefaultSourceDirectorySet("files", fileResolver, new DefaultDirectoryFileTreeFactory())
        directorySet.srcDir(rootDir)
        if (includePattern) {
            directorySet.include(includePattern)
        }
        if (includeFilter) {
            directorySet.filter.include(includeFilter)
        }
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(fileResolver)
        context.add(directorySet)
        additionalInputs.each {
            context.add(it)
        }
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees()
        fileTrees
    }
}
