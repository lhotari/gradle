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

package org.gradle.internal.buffer

import groovy.transform.CompileStatic
import spock.lang.Specification

abstract class AbstractByteBufferTest extends Specification {
    private static final int TESTROUNDS = 10000
    private static final String TEST_STRING = "Hello \u00f6\u00e4\u00e5\u00d6\u00c4\u00c5"
    private static final byte[] TEST_STRING_BYTES = TEST_STRING.getBytes('UTF-8')

    static byte[] testbuffer = new byte[256 * TESTROUNDS]

    @CompileStatic
    def setupSpec() {
        for (int i = 0; i < TESTROUNDS; i++) {
            for (int j = 0; j < 256; j++) {
                testbuffer[i * 256 + j] = (byte) (j & 0xff)
            }
        }
    }

    abstract Class<? extends AbstractByteBuffer> getBufferClass()

    protected AbstractByteBuffer createBuffer(Object... args) {
        getBufferClass().newInstance(args)
    }

    def "can convert to byte array"() {
        given:
        def buffer = createTestInstance()
        expect:
        buffer.readAsByteArray() == testbuffer
    }

    def "reads source InputStream fully"() {
        given:
        def buffer = createBuffer()
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)

        when:
        buffer.readFully(byteArrayInputStream)

        then:
        buffer.totalBytesUnread() == testbuffer.length
        buffer.readAsByteArray() == testbuffer
    }

    def "can create buffer from InputStream"() {
        given:
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)

        when:
        def buffer = bufferClass.of(byteArrayInputStream)

        then:
        buffer.totalBytesUnread() == testbuffer.length
        buffer.readAsByteArray() == testbuffer
    }

    def "reads source InputStream up to limit"() {
        given:
        def buffer = createBuffer(chunkSize)
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)
        byte[] testBufferPart = new byte[limit]
        System.arraycopy(testbuffer, 0, testBufferPart, 0, limit)

        when:
        buffer.readFrom(byteArrayInputStream, limit)

        then:
        buffer.totalBytesUnread() == limit
        buffer.readAsByteArray() == testBufferPart

        where:
        chunkSize = 8192
        limit << [1, 8191, 8192, 8193, 8194]
    }

    def "can create buffer from InputStream and limiting size"() {
        given:
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)
        byte[] testBufferPart = new byte[limit]
        System.arraycopy(testbuffer, 0, testBufferPart, 0, limit)

        when:
        def buffer = bufferClass.of(byteArrayInputStream, limit)

        then:
        buffer.totalBytesUnread() == limit
        buffer.readAsByteArray() == testBufferPart

        where:
        limit << [1, 8191, 8192, 8193, 8194]
    }

    def "converts to String"() {
        given:
        def buffer = createBuffer(chunkSize)
        def out = buffer.outputStream

        when:
        if (preUseBuffer) {
            // check the case that buffer has been used before
            out.write('HELLO'.getBytes("UTF-8"))
            buffer.readAsString("UTF-8") == 'HELLO'
            buffer.readAsString() == ''
            buffer.readAsString() == ''
        }
        out.write(TEST_STRING_BYTES)

        then:
        buffer.readAsString("UTF-8") == TEST_STRING

        where:
        // make sure that multi-byte unicode characters get split in different chunks
        [chunkSize, preUseBuffer] << [(1..(TEST_STRING_BYTES.length * 3)).toList() + [100, 1000], [false, true]].combinations()
    }

    def "empty buffer to String returns empty String"() {
        given:
        def buffer = createBuffer()

        expect:
        buffer.readAsString() == ''
    }

    def "can use InputStream interface to read from buffer"() {
        given:
        def buffer = createTestInstance()
        def input = buffer.getInputStream()
        def bytesOut = new ByteArrayOutputStream(buffer
                .totalBytesUnread())

        when:
        copy(input, bytesOut, 2048)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    def "can use InputStream and OutputStream interfaces to access buffer"() {
        given:
        def streamBuf = createBuffer(32000)
        def output = streamBuf.getOutputStream()

        when:
        output.write(1)
        output.write(2)
        output.write(3)
        output.write(255)
        output.close()

        then:
        def input = streamBuf.getInputStream()
        input.read() == 1
        input.read() == 2
        input.read() == 3
        input.read() == 255
        input.read() == -1
        input.close()
    }

    def "can write array and read one-by-one"() {
        given:
        def streamBuf = createBuffer(32000)
        def output = streamBuf.getOutputStream()
        byte[] bytes = [(byte) 1, (byte) 2, (byte) 3] as byte[]

        when:
        output.write(bytes)
        output.close()

        then:
        def input = streamBuf.getInputStream()
        input.read() == 1
        input.read() == 2
        input.read() == 3
        input.read() == -1
        input.close()
    }

    def "smoke test read to array"(int bufferSize, int testBufferSize) {
        expect:
        def streamBuf = createBuffer(bufferSize)
        def output = streamBuf.getOutputStream()
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2))
        }
        output.close()

        byte[] buffer = new byte[testBufferSize]
        def input = streamBuf.getInputStream()
        assert testBufferSize == input.available()
        int readBytes = input.read(buffer)
        assert readBytes == testBufferSize
        for (int i = 0; i < buffer.length; i++) {
            assert (byte) (i % (Byte.MAX_VALUE * 2)) == buffer[i]
        }
        assert input.read() == -1
        input.close()

        where:
        [bufferSize, testBufferSize] << [[10000, 10000], [1, 10000], [2, 10000], [10000, 2], [10000, 1]]
    }

    def "smoke test read one by one"(int bufferSize, int testBufferSize) {
        expect:
        def streamBuf = createBuffer(bufferSize)
        def output = streamBuf.getOutputStream()
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2))
        }
        output.close()

        def input = streamBuf.getInputStream()
        assert input.available() == testBufferSize
        for (int i = 0; i < testBufferSize; i++) {
            assert input.read() == i % (Byte.MAX_VALUE * 2)
        }
        assert input.read() == -1
        input.close()

        where:
        [bufferSize, testBufferSize] << [[10000, 10000], [1, 10000], [2, 10000], [10000, 2], [10000, 1]]
    }

    def "can write buffer to OutputStream"() {
        given:
        def buffer = createTestInstance()
        def bytesOut = new ByteArrayOutputStream(buffer.totalBytesUnread())

        when:
        buffer.writeTo(bytesOut)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    def "can copy buffer to OutputStream one-by-one"() {
        given:
        def buffer = createTestInstance()
        def input = buffer.getInputStream()
        def bytesOut = new ByteArrayOutputStream(buffer.totalBytesUnread())

        when:
        copyOneByOne(input, bytesOut)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    @CompileStatic
    static int copy(InputStream input, OutputStream output, int bufSize) throws IOException {
        byte[] buffer = new byte[bufSize]
        int count = 0
        int n = 0
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n)
            count += n
        }
        return count
    }

    @CompileStatic
    static int copyOneByOne(InputStream input, OutputStream output) throws IOException {
        int count = 0
        int b
        while (-1 != (b = input.read())) {
            output.write(b)
            count++
        }
        return count
    }

    AbstractByteBuffer createTestInstance() throws IOException {
        AbstractByteBuffer buffer = createBuffer()
        OutputStream output = buffer.getOutputStream()
        copyAllFromTestBuffer(output, 27)
        return buffer
    }

    @CompileStatic
    void copyAllFromTestBuffer(OutputStream output, int partsize) throws IOException {
        int position = 0
        int bytesLeft = testbuffer.length
        while (bytesLeft > 0) {
            output.write(testbuffer, position, partsize)
            position += partsize
            bytesLeft -= partsize
            if (bytesLeft < partsize) {
                partsize = bytesLeft
            }
        }
    }

    def "returns chunk size in range"() {
        given:
        def defaultChunkSize = bufferClass.DEFAULT_CHUNK_SIZE
        def maxChunkSize = bufferClass.MAX_CHUNK_SIZE
        expect:
        bufferClass.chunkSizeInDefaultRange(1) == defaultChunkSize
        bufferClass.chunkSizeInDefaultRange(0) == defaultChunkSize
        bufferClass.chunkSizeInDefaultRange(-1) == defaultChunkSize
        bufferClass.chunkSizeInDefaultRange(defaultChunkSize + 1) == defaultChunkSize + 1
        bufferClass.chunkSizeInDefaultRange(defaultChunkSize) == defaultChunkSize
        bufferClass.chunkSizeInDefaultRange(defaultChunkSize - 1) == defaultChunkSize
        bufferClass.chunkSizeInDefaultRange(maxChunkSize) == maxChunkSize
        bufferClass.chunkSizeInDefaultRange(maxChunkSize - 1) == maxChunkSize - 1
        bufferClass.chunkSizeInDefaultRange(maxChunkSize + 1) == maxChunkSize
        bufferClass.chunkSizeInDefaultRange(2 * maxChunkSize) == maxChunkSize
    }

    def "creates new instance with chunk size in range"() {
        when:
        def buffer = bufferClass.createWithChunkSizeInDefaultRange(1)
        then:
        buffer.chunkSize == bufferClass.DEFAULT_CHUNK_SIZE
    }

    def "reads available unicode characters in buffer and pushes in-progress ones back"() {
        given:
        def buffer = new StreamByteBuffer(chunkSize)
        def out = buffer.getOutputStream()
        def stringBuilder = new StringBuilder()

        when:
        out.write("HELLO".bytes)
        then:
        buffer.readAsString() == "HELLO"
        buffer.readAsString() == ""
        buffer.readAsString() == ""

        when:
        for (int i = 0; i < TEST_STRING_BYTES.length; i++) {
            out.write(TEST_STRING_BYTES[i])
            stringBuilder.append(buffer.readAsString('UTF-8'))
            if (readTwice) {
                // make sure 2nd readAsString handles properly multi-byte boundary
                stringBuilder.append(buffer.readAsString('UTF-8'))
            }
        }

        then:
        stringBuilder.toString() == TEST_STRING

        where:
        // make sure that multi-byte unicode characters get split in different chunks
        [chunkSize, readTwice] << [(1..(TEST_STRING_BYTES.length * 3)).toList() + [100, 1000], [false, true]].combinations()
    }

    def "calculates available characters when reading and writing"() {
        given:
        def buffer = new StreamByteBuffer(8)
        def out = buffer.outputStream

        when:
        out.write("1234567890123".bytes)
        then:
        buffer.totalBytesUnread() == 13

        when:
        buffer.readAsString()
        then:
        buffer.totalBytesUnread() == 0

        when:
        out.write("4567890123456".bytes)
        then:
        buffer.totalBytesUnread() == 13

        when:
        buffer.readAsString()
        then:
        buffer.totalBytesUnread() == 0

        when:
        out.write("789".bytes)
        then:
        buffer.totalBytesUnread() == 3

        when:
        buffer.readAsString()
        then:
        buffer.totalBytesUnread() == 0

        when:
        buffer.readAsString()
        then:
        buffer.totalBytesUnread() == 0
    }

    def "retains buffer when using retain mode"() {
        given:
        def buffer = new StreamByteBuffer(chunkSize, ReadMode.RETAIN_AFTER_READING)

        when:
        buffer.getOutputStream().write(TEST_STRING_BYTES)

        then:
        buffer.readAsString('UTF-8') == TEST_STRING

        expect:
        buffer.readAsString('UTF-8') == TEST_STRING
        buffer.readAsString('UTF-8') == TEST_STRING

        where:
        // make sure that multi-byte unicode characters get split in different chunks
        chunkSize << (1..(TEST_STRING_BYTES.length * 3)).toList() + [100, 1000]
    }
}