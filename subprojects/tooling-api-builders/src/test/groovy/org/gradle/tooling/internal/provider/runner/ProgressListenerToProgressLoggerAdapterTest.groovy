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

package org.gradle.tooling.internal.provider.runner

import groovy.transform.TupleConstructor
import org.gradle.logging.ProgressLogger
import org.gradle.tooling.ProgressEvent
import spock.lang.Specification
import spock.lang.Subject

class ProgressListenerToProgressLoggerAdapterTest extends Specification {
    ProgressLogger progressLogger = Mock()
    @Subject
    ProgressListenerToProgressLoggerAdapter adapter = new ProgressListenerToProgressLoggerAdapter(progressLogger)

    @TupleConstructor
    static class SimpleProgressEvent implements ProgressEvent {
        String description
    }

    def "can adapt a single status"() {
        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLogger.start('A', 'A')
        then:
        1 * progressLogger.completed()
        then:
        0 * _._
    }

    def "can adapt a hierarchical callstack"() {
        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLogger.start('A', 'A')
        then:
        1 * progressLogger.start('B', 'B')
        then:
        1 * progressLogger.completed()
        then:
        1 * progressLogger.completed()
        then:
        0 * _._
    }

    def "can adapt a looping call stack"() {
        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLogger.start('A', 'A')
        then:
        1 * progressLogger.start('B', 'B')
        then:
        1 * progressLogger.start('C', 'C')
        then:
        1 * progressLogger.completed()
        then:
        1 * progressLogger.start('C', 'C')
        then:
        1 * progressLogger.completed()
        then:
        1 * progressLogger.start('C', 'C')
        then:
        1 * progressLogger.completed()
        then:
        1 * progressLogger.completed()
        then:
        1 * progressLogger.completed()
        then:
        0 * _._
    }

}
