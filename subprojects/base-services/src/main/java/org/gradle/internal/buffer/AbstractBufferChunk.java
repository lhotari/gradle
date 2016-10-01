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

abstract class AbstractBufferChunk {
    public abstract void writeTo(OutputStream target) throws IOException;

    public abstract int readFrom(InputStream inputStream, int limit) throws IOException;

    public abstract ByteBuffer readToNioBuffer();

    public abstract int bytesUnread();

    public abstract int bytesUsed();

    public abstract int spaceLeft();

    public abstract void reset();

    public abstract int read();

    public abstract void read(byte[] buf, int offset, int len);

    public abstract void write(byte[] buf, int offset, int len);

    public abstract boolean write(byte b);

    public abstract void clear();
}
