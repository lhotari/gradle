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
import java.io.InputStream;

class StreamByteBufferInputStream extends InputStream {
    private StreamByteBuffer streamByteBuffer;

    public StreamByteBufferInputStream(StreamByteBuffer streamByteBuffer) {
        this.streamByteBuffer = streamByteBuffer;
    }

    @Override
    public int read() throws IOException {
        streamByteBuffer.prepareRead();
        return streamByteBuffer.getCurrentReadChunk().read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return readImpl(b, off, len);
    }

    int readImpl(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }

        if ((off < 0) || (off > b.length) || (len < 0)
                || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        int bytesLeft = len;
        int currentOffset = off;
        int bytesUnread = streamByteBuffer.prepareRead();
        int totalBytesRead = 0;
        while (bytesLeft > 0 && bytesUnread != -1) {
            int readBytes = Math.min(bytesUnread, bytesLeft);
            streamByteBuffer.getCurrentReadChunk().read(b, currentOffset, readBytes);
            bytesLeft -= readBytes;
            currentOffset += readBytes;
            totalBytesRead += readBytes;
            bytesUnread = streamByteBuffer.prepareRead();
        }
        if (totalBytesRead > 0) {
            return totalBytesRead;
        }

        return -1;
    }

    @Override
    public int available() throws IOException {
        return streamByteBuffer.totalBytesUnread();
    }

    public StreamByteBuffer getBuffer() {
        return streamByteBuffer;
    }
}
