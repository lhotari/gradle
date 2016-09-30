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
import org.gradle.internal.io.NullOutputStream

import java.security.DigestInputStream
import java.security.MessageDigest

class MemoryMappedFileByteBufferTest extends AbstractByteBufferTest {
    @Override
    Class<? extends AbstractByteBuffer> getBufferClass() {
        return MemoryMappedFileByteBuffer
    }

    def "should support large buffers"() {
        given:
        def buffer = new MemoryMappedFileByteBuffer(chunkSize)
        def writeDigest = MessageDigest.getInstance("MD5")
        def readDigest = MessageDigest.getInstance("MD5")

        when:
        writeToBuffer(buffer, bufferSize, writeDigest)
        DigestInputStream digestInputStream = new DigestInputStream(buffer.getInputStream(), readDigest)
        copy(digestInputStream, NullOutputStream.INSTANCE, 8192)

        then:
        writeDigest.digest() == readDigest.digest()

        cleanup:
        buffer.close()

        where:
        chunkSize = 10 * 1024 * 1024 // 10MB
        bufferSize = 100 * 1024 * 1024 // 100MB
    }

    @CompileStatic
    void writeToBuffer(AbstractByteBuffer buffer, int len, MessageDigest digest) {
        OutputStream outputStream = buffer.getOutputStream()
        for (int i = 0; i < len; i++) {
            byte b = (byte) (i & 0xff)
            digest.update(b)
            outputStream.write(b)
        }
    }
}
