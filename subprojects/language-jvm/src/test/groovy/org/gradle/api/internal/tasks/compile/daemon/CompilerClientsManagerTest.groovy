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

import java.util.concurrent.CountDownLatch

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
        def client1 = createClient()
        def client2 = createClient()
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
        def client = createClient()
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

    private CompilerDaemonClient createClient() {
        Mock(CompilerDaemonClient) { isCompatibleWith(_) >> true; ping() >> true; isReuseable() >> true }
    }

    def "idle client gets reused"() {
        given:
        def client1 = createClient()
        def client2 = createClient()
        starter.startDaemon(workingDir, options) >>> [client1, client2]

        when:
        def reservedClient1 = manager.reserveClient(workingDir, options)
        then:
        reservedClient1 == client1

        when:
        def reservedClient2 = manager.reserveClient(workingDir, options)
        then:
        reservedClient2 == client2

        when:
        manager.release(reservedClient1)
        def reservedClient3 = manager.reserveClient(workingDir, options)
        then:
        reservedClient3 == client1
    }

    def "max clients blocks until client is available"() {
        given:
        manager = new CompilerClientsManager(starter, 2)
        def client1 = createClient()
        def client2 = createClient()
        starter.startDaemon(workingDir, options) >>> [client1, client2]

        when:
        def reservedClient1 = manager.reserveClient(workingDir, options)
        then:
        reservedClient1 == client1

        when:
        def reservedClient2 = manager.reserveClient(workingDir, options)
        then:
        reservedClient2 == client2

        when:
        def reservedClient3
        def reserveClientOperation = start {
            start {
                sleep(500)
                manager.release(reservedClient1)
            }
            reservedClient3 = manager.reserveClient(workingDir, options)
        }

        then:
        reserveClientOperation.completed()
        reservedClient3 == client1
    }

    def "max clients blocks multiple waiting client requests"() {
        given:
        manager = new CompilerClientsManager(starter, 2)
        def client1 = createClient()
        def client2 = createClient()
        starter.startDaemon(workingDir, options) >>> [client1, client2]
        def reservedClient1 = manager.reserveClient(workingDir, options)
        def reservedClient2 = manager.reserveClient(workingDir, options)
        def waitLatch = new CountDownLatch(1)
        def noMatch = Stub(CompilerDaemonClient) {
            ping() >> true
            isCompatibleWith(_) >> false
            isReuseable() >> true
        }

        when:
        def reservedClient3
        def reserveClientOperation1 = start {
            start {
                for (int i = 0; i < 10; i++) {
                    manager.release(noMatch)
                }
                sleep(500)
                manager.release(reservedClient1)
            }
            waitFor waitLatch
            reservedClient3 = manager.reserveClient(workingDir, options)
        }
        def reservedClient4
        def reserveClientOperation2 = start {
            start {
                for (int i = 0; i < 10; i++) {
                    manager.release(noMatch)
                }
                sleep(600)
                manager.release(reservedClient2)
            }
            waitLatch.countDown()
            reservedClient4 = manager.reserveClient(workingDir, options)
        }

        then:
        reserveClientOperation1.completed()
        reserveClientOperation2.completed()
        reservedClient3 != null
        reservedClient4 != null
        reservedClient3 == client1 && reservedClient4 == client2 || reservedClient3 == client2 && reservedClient4 == client1
    }

    def "max clients blocks and discards client that replies false to ping - opens new connection after discarding"() {
        given:
        manager = new CompilerClientsManager(starter, 2)
        def client1 = Mock(CompilerDaemonClient) { isCompatibleWith(_) >> true; ping() >>> false; isReuseable() >> true }
        def client2 = createClient()
        def client3 = createClient()
        starter.startDaemon(workingDir, options) >>> [client1, client2, client3]
        manager.reserveClient(workingDir, options)
        manager.reserveClient(workingDir, options)

        when:
        def reservedClient3
        def reserveClientOperation1 = start {
            start {
                sleep(500)
                manager.release(client1)
            }
            reservedClient3 = manager.reserveClient(workingDir, options)
        }

        then:
        reserveClientOperation1.completed()
        reservedClient3 == client3
    }
}
