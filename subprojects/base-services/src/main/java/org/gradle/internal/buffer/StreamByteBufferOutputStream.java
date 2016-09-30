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

package org.gradle.internal.buffer;

import java.io.IOException;
import java.io.OutputStream;

class StreamByteBufferOutputStream extends OutputStream {
    private StreamByteBuffer streamByteBuffer;
    private boolean closed;

    public StreamByteBufferOutputStream(StreamByteBuffer streamByteBuffer) {
        this.streamByteBuffer = streamByteBuffer;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }

        if ((off < 0) || (off > b.length) || (len < 0)
                || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return;
        }

        int bytesLeft = len;
        int currentOffset = off;
        while (bytesLeft > 0) {
            int spaceLeft = streamByteBuffer.allocateSpace();
            int writeBytes = Math.min(spaceLeft, bytesLeft);
            streamByteBuffer.getCurrentWriteChunk().write(b, currentOffset, writeBytes);
            bytesLeft -= writeBytes;
            currentOffset += writeBytes;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void write(int b) throws IOException {
        streamByteBuffer.allocateSpace();
        streamByteBuffer.getCurrentWriteChunk().write((byte) b);
    }

    public StreamByteBuffer getBuffer() {
        return streamByteBuffer;
    }
}
