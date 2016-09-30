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

public class StreamByteBuffer extends AbstractByteBuffer {
    private static final int DEFAULT_CHUNK_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = 1024 * 1024;

    public StreamByteBuffer() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public StreamByteBuffer(final int chunkSize) {
        this(chunkSize, ReadMode.REMOVE_AFTER_READING);
    }

    public StreamByteBuffer(final int chunkSize, ReadMode readMode) {
        this(chunkSize, Math.max(chunkSize, MAX_CHUNK_SIZE), readMode);
    }

    public StreamByteBuffer(int chunkSize, int maxChunkSize, ReadMode readMode) {
        super(chunkSize, Math.max(chunkSize, MAX_CHUNK_SIZE), readMode);
    }

    public static StreamByteBuffer of(InputStream inputStream) throws IOException {
        StreamByteBuffer buffer = new StreamByteBuffer(chunkSizeInDefaultRange(inputStream.available()));
        buffer.readFully(inputStream);
        return buffer;
    }

    public static StreamByteBuffer of(InputStream inputStream, int len) throws IOException {
        StreamByteBuffer buffer = new StreamByteBuffer(chunkSizeInDefaultRange(len));
        buffer.readFrom(inputStream, len);
        return buffer;
    }

    public static StreamByteBuffer createWithChunkSizeInDefaultRange(int value) {
        return new StreamByteBuffer(chunkSizeInDefaultRange(value));
    }

    static int chunkSizeInDefaultRange(int value) {
        return valueInRange(value, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
    }

    protected AbstractBufferChunk createStreamByteBufferChunk(int chunkSize) {
        return new StreamByteBufferChunk(chunkSize);
    }

}
