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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class MemoryMappedFileByteBuffer extends AbstractByteBuffer {
    private static final int DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = 320 * 1024 * 1024;

    public MemoryMappedFileByteBuffer() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public MemoryMappedFileByteBuffer(final int chunkSize) {
        this(chunkSize, ReadMode.REMOVE_AFTER_READING);
    }

    public MemoryMappedFileByteBuffer(final int chunkSize, ReadMode readMode) {
        this(chunkSize, Math.max(chunkSize, MAX_CHUNK_SIZE), readMode);
    }

    public MemoryMappedFileByteBuffer(int chunkSize, int maxChunkSize, ReadMode readMode) {
        super(chunkSize, maxChunkSize, readMode);
    }

    public static MemoryMappedFileByteBuffer of(InputStream inputStream) throws IOException {
        MemoryMappedFileByteBuffer buffer = new MemoryMappedFileByteBuffer(chunkSizeInDefaultRange(inputStream.available()));
        buffer.readFully(inputStream);
        return buffer;
    }

    public static MemoryMappedFileByteBuffer of(InputStream inputStream, int len) throws IOException {
        MemoryMappedFileByteBuffer buffer = new MemoryMappedFileByteBuffer(chunkSizeInDefaultRange(len));
        buffer.readFrom(inputStream, len);
        return buffer;
    }

    public static MemoryMappedFileByteBuffer createWithChunkSizeInDefaultRange(int value) {
        return new MemoryMappedFileByteBuffer(chunkSizeInDefaultRange(value));
    }

    static int chunkSizeInDefaultRange(int value) {
        return valueInRange(value, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
    }

    @Override
    protected AbstractBufferChunk createStreamByteBufferChunk(int chunkSize) {
        return new MemoryMappedFileBufferChunk(chunkSize, createTempFile());
    }

    protected File createTempFile() {
        try {
            return File.createTempFile("bufferchunk", ".bin");
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
