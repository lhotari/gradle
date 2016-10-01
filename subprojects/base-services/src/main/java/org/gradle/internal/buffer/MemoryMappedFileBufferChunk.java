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

import org.gradle.internal.UncheckedException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;

class MemoryMappedFileBufferChunk extends AbstractBufferChunk implements Closeable {
    private final MappedByteBuffer mappedByteBuffer;
    private final File file;
    private BufferMode bufferMode = BufferMode.WRITE;
    private byte[] copyBuffer;
    private boolean nioBufferReturned;
    private int bufferWrittenSize = -1;

    enum BufferMode {
        WRITE,
        READ
    }

    public MemoryMappedFileBufferChunk(int size, File tempFile) {
        try {
            this.file = tempFile;
            this.mappedByteBuffer = createMappedByteBuffer(file, size);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private MappedByteBuffer createMappedByteBuffer(File file, int size) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        MappedByteBuffer mappedByteBuffer = randomAccessFile.getChannel().map(READ_WRITE, 0, size);
        randomAccessFile.close(); // MappedByteBuffer stays open
        return mappedByteBuffer;
    }

    public ByteBuffer readToNioBuffer() {
        switchToMode(BufferMode.READ);
        nioBufferReturned = true;
        return mappedByteBuffer;
    }

    private void switchToMode(BufferMode newMode) {
        if (bufferMode != newMode) {
            if (bufferMode == BufferMode.WRITE) {
                bufferWrittenSize = mappedByteBuffer.position();
                mappedByteBuffer.flip();
            } else if (bufferMode == BufferMode.READ) {
                throw new IllegalStateException("Cannot continue writing after reading");
            }
            bufferMode = newMode;
        }
    }

    public boolean write(byte b) {
        switchToMode(BufferMode.WRITE);
        if (mappedByteBuffer.hasRemaining()) {
            mappedByteBuffer.put(b);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        mappedByteBuffer.clear();
        bufferMode = BufferMode.WRITE;
        nioBufferReturned = false;
        bufferWrittenSize = -1;
    }

    public void write(byte[] b, int off, int len) {
        switchToMode(BufferMode.WRITE);
        mappedByteBuffer.put(b, off, len);
    }

    public void read(byte[] b, int off, int len) {
        switchToMode(BufferMode.READ);
        mappedByteBuffer.get(b, off, len);
    }

    public void writeTo(OutputStream target) throws IOException {
        switchToMode(BufferMode.READ);
        if (mappedByteBuffer.hasRemaining()) {
            allocateCopyBuffer();
            int bytesLeft = mappedByteBuffer.remaining();
            while (bytesLeft > 0 && mappedByteBuffer.hasRemaining()) {
                int len = Math.min(copyBuffer.length, bytesLeft);
                mappedByteBuffer.get(copyBuffer, 0, len);
                target.write(copyBuffer, 0, len);
                bytesLeft -= len;
            }
        }
    }

    protected void allocateCopyBuffer() {
        if (copyBuffer == null) {
            copyBuffer = new byte[8192];
        }
    }

    public void reset() {
        nioBufferReturned = false;
        mappedByteBuffer.position(bufferWrittenSize);
        mappedByteBuffer.flip();
        bufferMode = BufferMode.READ;
    }

    public int bytesUsed() {
        if (bufferMode == BufferMode.WRITE) {
            return mappedByteBuffer.position();
        } else {
            return bufferWrittenSize;
        }
    }

    public int bytesUnread() {
        if (nioBufferReturned) {
            return 0;
        }
        if (bufferMode == BufferMode.WRITE) {
            return mappedByteBuffer.position();
        } else {
            return mappedByteBuffer.remaining();
        }
    }

    public int read() {
        switchToMode(BufferMode.READ);
        if (mappedByteBuffer.hasRemaining()) {
            return mappedByteBuffer.get() & 0xff;
        }
        return -1;
    }

    public int spaceLeft() {
        switchToMode(BufferMode.WRITE);
        return mappedByteBuffer.remaining();
    }

    public int readFrom(InputStream inputStream, int len) throws IOException {
        switchToMode(BufferMode.WRITE);
        int totalReadBytes = 0;
        if (mappedByteBuffer.hasRemaining()) {
            allocateCopyBuffer();
            int bytesLeft = len;
            while (bytesLeft > 0 && mappedByteBuffer.hasRemaining()) {
                int readLen = Math.min(copyBuffer.length, bytesLeft);
                int readBytes = inputStream.read(copyBuffer, 0, readLen);
                if (readBytes > 0) {
                    mappedByteBuffer.put(copyBuffer, 0, readBytes);
                    totalReadBytes += readBytes;
                    bytesLeft -= readBytes;
                } else {
                    return readBytes;
                }
            }
        }
        return totalReadBytes;
    }

    @Override
    public void close() {
        mappedByteBuffer.clear();
        file.delete();
    }

    //CHECKSTYLE:OFF
    @Override
    protected void finalize() throws Throwable {
        close();
    }
    //CHECKSTYLE:ON
}
