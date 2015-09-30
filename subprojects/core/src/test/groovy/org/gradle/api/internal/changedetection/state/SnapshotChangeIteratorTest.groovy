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

package org.gradle.api.internal.changedetection.state

import org.gradle.util.ChangeListener
import spock.lang.Specification


class SnapshotChangeIteratorTest extends Specification {
    def "should not call changelistener when inputs are equal"() {
        given:
        def snapshot1 = createSnapshots(10)
        def snapshot2 = createSnapshots(10)
        def changeIterator = new DefaultFileCollectionSnapshotter.SnapshotChangeIterator(snapshot1, snapshot2)
        def changeListener = Mock(ChangeListener)
        def counter = 0
        when:
        while (changeIterator.next(changeListener)) {
            counter++
        }
        then:
        0 * changeListener._
        counter == 10
    }

    private FileCollectionSnapshot createSnapshots(int entries) {
        SortedMap<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> snapshots = new TreeMap<>()
        (1..entries).each {
            snapshots.put("file$it".toString(), new DefaultFileCollectionSnapshotter.FileHashSnapshot("hash$it".bytes))
        }
        new DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl(snapshots)
    }
}
