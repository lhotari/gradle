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
import java.io.OutputStream;
import java.nio.ByteBuffer;

class StreamByteBufferChunk extends AbstractBufferChunk {
    private int pointer;
    private byte[] buffer;
    private int size;
    private int used;

    public StreamByteBufferChunk(int size) {
        this.size = size;
        buffer = new byte[size];
    }

    public ByteBuffer readToNioBuffer() {
        if (pointer < used) {
            ByteBuffer result;
            if (pointer > 0 || used < size) {
                result = ByteBuffer.wrap(buffer, pointer, used - pointer);
            } else {
                result = ByteBuffer.wrap(buffer);
            }
            pointer = used;
            return result;
        }

        return null;
    }

    public boolean write(byte b) {
        if (used < size) {
            buffer[used++] = b;
            return true;
        }

        return false;
    }

    public void write(byte[] b, int off, int len) {
        System.arraycopy(b, off, buffer, used, len);
        used = used + len;
    }

    public void read(byte[] b, int off, int len) {
        System.arraycopy(buffer, pointer, b, off, len);
        pointer = pointer + len;
    }

    public void writeTo(OutputStream target) throws IOException {
        if (pointer < used) {
            target.write(buffer, pointer, used - pointer);
            pointer = used;
        }
    }

    public void reset() {
        pointer = 0;
    }

    public int bytesUsed() {
        return used;
    }

    public int bytesUnread() {
        return used - pointer;
    }

    public int read() {
        if (pointer < used) {
            return buffer[pointer++] & 0xff;
        }

        return -1;
    }

    public int spaceLeft() {
        return size - used;
    }

    public int readFrom(InputStream inputStream, int len) throws IOException {
        int readBytes = inputStream.read(buffer, used, len);
        if (readBytes > 0) {
            used += readBytes;
        }
        return readBytes;
    }

    public void clear() {
        used = pointer = 0;
    }

    @Override
    public void close() throws IOException {

    }
}
