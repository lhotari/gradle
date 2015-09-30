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
        def entries = (1..10).toList()
        def snapshot1 = snap(entries)
        def snapshot2 = snap(entries)
        def changeIterator = new SnapshotChangeIterator(snapshot1, snapshot2).adaptToFilenameChangeIterator()
        def changeListener = Mock(ChangeListener)
        def counter = 0
        when:
        while (changeIterator.next(changeListener)) {
            counter++
        }
        then:
        0 * changeListener._
        counter == 0
    }

    def "should call ChangeListener on differences"() {
        given:
        def changeIterator = new SnapshotChangeIterator(snapshot1, snapshot2).adaptToFilenameChangeIterator()
        def added = []
        def removed = []
        def changed = []
        def changeListener = new ChangeListener() {
            @Override
            void added(Object element) {
                added << element
            }

            @Override
            void removed(Object element) {
                removed << element
            }

            @Override
            void changed(Object element) {
                changed << element
            }
        }

        when:
        while (changeIterator.next(changeListener)) {
            ;
        }
        then:
        added == expectedAdded
        changed == expectedChanged
        removed == expectedRemoved
        where:
        snapshot1                        | snapshot2                        | expectedAdded                                      | expectedChanged | expectedRemoved
        snap(1..10)                      | snap(1..10)                      | []                                                 | []              | []
        snap(1..10)                      | snap(1..11)                      | ["file11"]                                         | []              | []
        snap(2..10)                      | snap(1..10)                      | ["file01"]                                         | []              | []
        snap(2..9)                       | snap(1..10)                      | ["file01", "file10"]                               | []              | []
        snap((2..9).findAll { it != 5 }) | snap(1..10)                      | ["file01", "file05", "file10"]                     | []              | []
        snap(1..10)                      | snap((2..9).findAll { it != 5 }) | []                                                 | []              | ["file01", "file05", "file10"]
        snap(1..10)                      | snap(1..9)                       | []                                                 | []              | ["file10"]
        snap(1..10)                      | snap(2..10)                      | []                                                 | []              | ["file01"]
        snap((1..10).step(2))            | snap(1..10)                      | ["file02", "file04", "file06", "file08", "file10"] | []              | []
        snap([])                         | snap(1..3)                       | ["file01", "file02", "file03"]                     | []              | []
        snap(1..3)                       | snap([])                         | []                                                 | []              | ["file01", "file02", "file03"]
        snap(1..10) | snap(1..10, [5])  | [] | ['file05']                     | []
        snap(1..10) | snap(1..10, [1])  | [] | ['file01']                     | []
        snap(1..10) | snap(1..10, [10]) | [] | ['file10']                     | []
        snap(1..3)  | snap(1..3, 1..3)  | [] | ['file01', 'file02', 'file03'] | []
    }

    private DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl snap(entries, changedEntries = []) {
        def changedEntriesSet = changedEntries as Set
        SortedMap<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> snapshots = new TreeMap<>()
        entries.each {
            String fileNumber = String.format("%02d", it)
            snapshots.put("file$fileNumber".toString(), new DefaultFileCollectionSnapshotter.FileHashSnapshot("hash$fileNumber${changedEntriesSet.contains(it) ? ' changed' : ''}".bytes))
        }
        new DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl(snapshots)
    }
}
