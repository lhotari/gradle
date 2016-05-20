/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.util.ConcurrentSpecification
import spock.lang.Subject

class CompilerClientsManagerTest extends ConcurrentSpecification {

    def workingDir = new File("some-dir")

    def options = Stub(DaemonForkOptions)
    def starter = Stub(CompilerDaemonStarter)

    @Subject manager = new CompilerClientsManager(starter, 0)

    def "does not reserve idle client when it doesn't match"() {
        def noMatch = Stub(CompilerDaemonClient) {
            isCompatibleWith(_) >> false
        }
        manager.idleClients << noMatch

        expect:
        manager.reserveClient(workingDir, options) != noMatch
    }

    def "reserves idle client when match found"() {
        def noMatch = Stub(CompilerDaemonClient) { isCompatibleWith(_) >> false; ping() >> true}
        def match = Stub(CompilerDaemonClient) { isCompatibleWith(_) >> true; ping() >> true }
        def input = [noMatch, match]
        manager.allClients.addAll(input)
        manager.idleClients.addAll(input)

        expect:
        manager.reserveClient(workingDir, options) == match
        manager.idleClients.size() == 1
        manager.idleClients.getFirst() == noMatch //match removed from input
    }

    def "reserves new client"() {
        def newClient = Stub(CompilerDaemonClient)
        starter.startDaemon(workingDir, options) >> newClient

        when:
        def client = manager.reserveClient(workingDir, options)

        then:
        newClient == client
    }

    def "can stop all created clients"() {
        def client1 = Mock(CompilerDaemonClient) { ping() >> true }
        def client2 = Mock(CompilerDaemonClient) { ping() >> true }
        starter.startDaemon(workingDir, options) >>> [client1, client2]

        when:
        manager.reserveClient(workingDir, options)
        manager.reserveClient(workingDir, options)
        manager.stop()

        then:
        1 * client1.stop()
        1 * client2.stop()
    }

    def "clients can be released for further use"() {
        def client = Mock(CompilerDaemonClient) { isCompatibleWith(_) >> true; ping() >> true; isReuseable() >> true }
        starter.startDaemon(workingDir, options) >> client

        when:
        manager.reserveClient(workingDir, options)

        then:
        manager.idleClients.size == 0

        when:
        manager.release(client)

        then:
        manager.idleClients.getFirst() == client
    }
}
