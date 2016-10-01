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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * An in-memory buffer that provides OutputStream and InputStream interfaces.
 *
 * This is more efficient than using ByteArrayOutputStream/ByteArrayInputStream
 *
 * This is not thread-safe, it is intended to be used by a single Thread.
 */
public abstract class AbstractByteBuffer {
    private LinkedList<AbstractBufferChunk> chunks = new LinkedList<AbstractBufferChunk>();
    private AbstractBufferChunk currentWriteChunk;
    private AbstractBufferChunk currentReadChunk;
    protected int chunkSize;
    private int nextChunkSize;
    private int maxChunkSize;
    private BufferOutputStream output;
    private BufferInputStream input;
    private int totalBytesUnreadInList;
    private int totalBytesUnreadInIterator;
    private ReadMode readMode;
    private Iterator<AbstractBufferChunk> readIterator;
    private byte[] remainingBytesBuffer;

    public AbstractByteBuffer(int chunkSize, int maxChunkSize, ReadMode readMode) {
        this.chunkSize = chunkSize;
        this.readMode = readMode;
        this.nextChunkSize = chunkSize;
        this.maxChunkSize = maxChunkSize;
        currentWriteChunk = createStreamByteBufferChunk(nextChunkSize);
        output = new BufferOutputStream(this);
        input = new BufferInputStream(this);
    }

    protected abstract AbstractBufferChunk createStreamByteBufferChunk(int chunkSize);

    static int valueInRange(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public OutputStream getOutputStream() {
        return output;
    }

    public InputStream getInputStream() {
        return input;
    }

    public void writeTo(OutputStream target) throws IOException {
        while (prepareRead() != -1) {
            getCurrentReadChunk().writeTo(target);
        }
    }

    public void readFrom(InputStream inputStream, int len) throws IOException {
        int bytesLeft = len;
        while (bytesLeft > 0) {
            int spaceLeft = allocateSpace();
            int limit = Math.min(spaceLeft, bytesLeft);
            int readBytes = getCurrentWriteChunk().readFrom(inputStream, limit);
            if (readBytes == -1) {
                throw new EOFException("Unexpected EOF");
            }
            bytesLeft -= readBytes;
        }
    }

    public void readFully(InputStream inputStream) throws IOException {
        while (true) {
            int len = allocateSpace();
            int readBytes = getCurrentWriteChunk().readFrom(inputStream, len);
            if (readBytes == -1) {
                break;
            }
        }
    }

    public byte[] readAsByteArray() {
        byte[] buf = new byte[totalBytesUnread()];
        input.readImpl(buf, 0, buf.length);
        return buf;
    }

    public String readAsString(String encoding) {
        Charset charset = Charset.forName(encoding);
        return readAsString(charset);
    }

    public String readAsString() {
        return readAsString(Charset.defaultCharset());
    }

    public String readAsString(Charset charset) {
        try {
            return doReadAsString(charset);
        } catch (CharacterCodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String doReadAsString(Charset charset) throws CharacterCodingException {
        int unreadSize = totalBytesUnread();
        if (unreadSize > 0) {
            return readAsCharBuffer(charset).toString();
        }
        return "";
    }

    private CharBuffer readAsCharBuffer(Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder().onMalformedInput(
                CodingErrorAction.REPLACE).onUnmappableCharacter(
                CodingErrorAction.REPLACE);
        CharBuffer charbuffer = CharBuffer.allocate(totalBytesUnread());
        ByteBuffer buf = null;
        boolean wasUnderflow = false;
        ByteBuffer nextBuf = null;
        boolean needsFlush = false;
        while (hasRemaining(nextBuf) || hasRemaining(buf) || prepareRead() != -1) {
            if (hasRemaining(buf)) {
                // handle decoding underflow, multi-byte unicode character at buffer chunk boundary
                if (!wasUnderflow) {
                    throw new IllegalStateException("Unexpected state. Buffer has remaining bytes without underflow in decoding.");
                }
                if (!hasRemaining(nextBuf) && prepareRead() != -1) {
                    nextBuf = currentReadChunk.readToNioBuffer();
                }
                // copy one by one until the underflow has been resolved
                buf = ByteBuffer.allocate(buf.remaining() + 1).put(buf);
                buf.put(nextBuf.get());
                buf.flip();
            } else {
                if (hasRemaining(nextBuf)) {
                    buf = nextBuf;
                } else if (prepareRead() != -1) {
                    buf = currentReadChunk.readToNioBuffer();
                    if (!hasRemaining(buf)) {
                        throw new IllegalStateException("Unexpected state. Buffer is empty.");
                    }
                }
                nextBuf = null;
            }
            boolean endOfInput = !hasRemaining(nextBuf) && prepareRead() == -1;
            int bufRemainingBefore = buf.remaining();
            CoderResult result = decoder.decode(buf, charbuffer, false);
            if (bufRemainingBefore > buf.remaining()) {
                needsFlush = true;
            }
            if (endOfInput) {
                result = decoder.decode(ByteBuffer.allocate(0), charbuffer, true);
                if (!result.isUnderflow()) {
                    result.throwException();
                }
                break;
            }
            wasUnderflow = result.isUnderflow();
        }
        if (needsFlush) {
            CoderResult result = decoder.flush(charbuffer);
            if (!result.isUnderflow()) {
                result.throwException();
            }
        }
        int remainingBytesCount = storeRemainingBytes(buf);
        if (readMode == ReadMode.RETAIN_AFTER_READING) {
            reset();
        } else {
            clear();
        }
        if (remainingBytesCount > 0) {
            pushBackRemainingBytes(remainingBytesCount);
        }
        charbuffer.flip();
        return charbuffer;
    }

    // push back remaining bytes of multi-byte unicode character
    private void pushBackRemainingBytes(int remainingBytesCount) {
        try {
            getOutputStream().write(remainingBytesBuffer, 0, remainingBytesCount);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private int storeRemainingBytes(ByteBuffer buf) {
        int remainingBytesCount = 0;
        if (hasRemaining(buf)) {
            remainingBytesCount = buf.remaining();
            if (remainingBytesBuffer == null || remainingBytesBuffer.length < remainingBytesCount) {
                remainingBytesBuffer = new byte[buf.remaining()];
            }
            buf.get(remainingBytesBuffer, 0, remainingBytesCount);
        }
        return remainingBytesCount;
    }

    private boolean hasRemaining(ByteBuffer nextBuf) {
        return nextBuf != null && nextBuf.hasRemaining();
    }

    public int totalBytesUnread() {
        int total = 0;
        if (readMode == ReadMode.REMOVE_AFTER_READING) {
            total = totalBytesUnreadInList;
        } else if (readMode == ReadMode.RETAIN_AFTER_READING) {
            prepareRetainAfterReading();
            total = totalBytesUnreadInIterator;
        }
        if (currentReadChunk != null) {
            total += currentReadChunk.bytesUnread();
        }
        if (currentWriteChunk != currentReadChunk && currentWriteChunk != null) {
            if (readMode == ReadMode.REMOVE_AFTER_READING) {
                total += currentWriteChunk.bytesUnread();
            } else if (readMode == ReadMode.RETAIN_AFTER_READING) {
                total += currentWriteChunk.bytesUsed();
            }
        }
        return total;
    }

    protected int allocateSpace() {
        int spaceLeft = getCurrentWriteChunk().spaceLeft();
        if (spaceLeft == 0) {
            chunks.add(getCurrentWriteChunk());
            totalBytesUnreadInList += getCurrentWriteChunk().bytesUnread();
            currentWriteChunk = createStreamByteBufferChunk(nextChunkSize);
            if (nextChunkSize < maxChunkSize) {
                nextChunkSize = Math.min(nextChunkSize * 2, maxChunkSize);
            }
            spaceLeft = getCurrentWriteChunk().spaceLeft();
        }
        return spaceLeft;
    }

    protected int prepareRead() {
        prepareRetainAfterReading();
        int bytesUnread = (getCurrentReadChunk() != null) ? getCurrentReadChunk().bytesUnread() : 0;
        if (bytesUnread == 0) {
            if (readMode == ReadMode.REMOVE_AFTER_READING && !chunks.isEmpty()) {
                currentReadChunk = chunks.removeFirst();
                bytesUnread = getCurrentReadChunk().bytesUnread();
                totalBytesUnreadInList -= bytesUnread;
            } else if (readMode == ReadMode.RETAIN_AFTER_READING && readIterator.hasNext()) {
                currentReadChunk = readIterator.next();
                currentReadChunk.reset();
                bytesUnread = currentReadChunk.bytesUnread();
                totalBytesUnreadInIterator -= bytesUnread;
            } else if (getCurrentReadChunk() != getCurrentWriteChunk()) {
                currentReadChunk = getCurrentWriteChunk();
                bytesUnread = getCurrentReadChunk().bytesUnread();
            } else {
                bytesUnread = -1;
            }
        }
        return bytesUnread;
    }

    AbstractBufferChunk getCurrentWriteChunk() {
        return currentWriteChunk;
    }

    AbstractBufferChunk getCurrentReadChunk() {
        return currentReadChunk;
    }

    public void reset() {
        if (readMode == ReadMode.RETAIN_AFTER_READING) {
            readIterator = null;
            prepareRetainAfterReading();
            if (currentWriteChunk != null) {
                currentWriteChunk.reset();
            }
        }
    }

    private void prepareRetainAfterReading() {
        if (readMode == ReadMode.RETAIN_AFTER_READING && readIterator == null) {
            readIterator = chunks.iterator();
            totalBytesUnreadInIterator = totalBytesUnreadInList;
            currentReadChunk = null;
        }
    }

    public ReadMode getReadMode() {
        return readMode;
    }

    public void setReadMode(ReadMode readMode) {
        this.readMode = readMode;
    }

    public void retainAfterReadingMode() {
        setReadMode(ReadMode.RETAIN_AFTER_READING);
    }

    public void clear() {
        chunks.clear();
        currentReadChunk = null;
        totalBytesUnreadInList = 0;
        currentWriteChunk.clear();
        totalBytesUnreadInIterator = 0;
        readIterator = null;
    }
}
