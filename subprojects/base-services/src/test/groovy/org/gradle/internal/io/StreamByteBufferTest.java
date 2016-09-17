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

package org.gradle.internal.io;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamByteBufferTest {
    private static final int TESTROUNDS = 10000;
    private static final String TEST_STRING = "Hello \u00f6\u00e4\u00e5\u00d6\u00c4\u00c5";

    static byte[] testbuffer = new byte[256 * TESTROUNDS];

    protected void setUp() throws Exception {
        if (testbuffer == null) {
            for (int i = 0; i < TESTROUNDS; i++) {
                for (int j = 0; j < 256; j++) {
                    testbuffer[i * 256 + j] = (byte) (j & 0xff);
                }
            }
        }
    }

    @Test
    public void testToByteArray() throws IOException {
        StreamByteBuffer byteBuffer = createTestInstance();
        byte[] result = byteBuffer.readAsByteArray();
        assertTrue(Arrays.equals(testbuffer, result));
    }

    @Test
    public void testReadFully() throws IOException {
        StreamByteBuffer byteBuffer = new StreamByteBuffer();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(testbuffer);
        byteBuffer.readFully(byteArrayInputStream);
        assertEquals(testbuffer.length, byteBuffer.totalBytesUnread());
        byte[] result = byteBuffer.readAsByteArray();
        assertTrue(Arrays.equals(testbuffer, result));
    }

    @Test
    public void testReadFrom() throws IOException {
        StreamByteBuffer byteBuffer = new StreamByteBuffer();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(testbuffer);
        byteBuffer.readFrom(byteArrayInputStream, 8193);
        assertEquals(8193, byteBuffer.totalBytesUnread());
        byte[] result = byteBuffer.readAsByteArray();
        byte[] testBufferPart = new byte[8193];
        System.arraycopy(testbuffer, 0, testBufferPart, 0, 8193);
        assertTrue(Arrays.equals(testBufferPart, result));
    }

    @Test
    public void testToString() throws IOException {
        StreamByteBuffer byteBuffer = new StreamByteBuffer();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(byteBuffer.getOutputStream(), "UTF-8"));
        pw.print(TEST_STRING);
        pw.close();
        assertEquals(TEST_STRING, byteBuffer.readAsString("UTF-8"));
    }

    @Test
    public void testToInputStream() throws IOException {
        StreamByteBuffer byteBuffer = createTestInstance();
        InputStream input = byteBuffer.getInputStream();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(byteBuffer
                .totalBytesUnread());
        copy(input, bytesOut, 2048);
        byte[] result = bytesOut.toByteArray();
        assertTrue(Arrays.equals(testbuffer, result));
    }

    @Test
    public void testStreamByteBuffer() throws Exception {
        StreamByteBuffer streamBuf = new StreamByteBuffer(32000);
        OutputStream output = streamBuf.getOutputStream();
        output.write(1);
        output.write(2);
        output.write(3);
        output.write(255);
        output.close();
        InputStream input = streamBuf.getInputStream();
        assertEquals(1, input.read());
        assertEquals(2, input.read());
        assertEquals(3, input.read());
        assertEquals(255, input.read());
        assertEquals(-1, input.read());
        input.close();
    }

    @Test
    public void testStreamByteBuffer2() throws Exception {
        StreamByteBuffer streamBuf = new StreamByteBuffer(32000);
        OutputStream output = streamBuf.getOutputStream();
        byte[] bytes = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        output.write(bytes);
        output.close();
        InputStream input = streamBuf.getInputStream();
        assertEquals(1, input.read());
        assertEquals(2, input.read());
        assertEquals(3, input.read());
        assertEquals(-1, input.read());
        input.close();
    }

    @Test
    public void testStreamByteBuffer3() throws Exception {
        bufferTest(10000, 10000);
        bufferTest(1, 10000);
        bufferTest(2, 10000);
        bufferTest(10000, 2);
        bufferTest(10000, 1);

        bufferTest2(10000, 10000);
        bufferTest2(1, 10000);
        bufferTest2(2, 10000);
        bufferTest2(10000, 2);
        bufferTest2(10000, 1);
    }

    private void bufferTest(int streamByteBufferSize, int testBufferSize) throws IOException {
        StreamByteBuffer streamBuf = new StreamByteBuffer(streamByteBufferSize);
        OutputStream output = streamBuf.getOutputStream();
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2));
        }
        output.close();
        byte[] buffer = new byte[testBufferSize];
        InputStream input = streamBuf.getInputStream();
        assertEquals(testBufferSize, input.available());
        int readBytes = input.read(buffer);
        assertEquals(readBytes, testBufferSize);
        for (int i = 0; i < buffer.length; i++) {
            assertEquals((byte) (i % (Byte.MAX_VALUE * 2)), buffer[i]);
        }
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        input.close();
    }

    private void bufferTest2(int streamByteBufferSize, int testBufferSize) throws IOException {
        StreamByteBuffer streamBuf = new StreamByteBuffer(streamByteBufferSize);
        OutputStream output = streamBuf.getOutputStream();
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2));
        }
        output.close();
        InputStream input = streamBuf.getInputStream();
        assertEquals(testBufferSize, input.available());
        for (int i = 0; i < testBufferSize; i++) {
            assertEquals(i % (Byte.MAX_VALUE * 2), input.read());
        }
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        assertEquals(-1, input.read());
        input.close();
    }

    @Test
    public void testWriteTo() throws IOException {
        StreamByteBuffer byteBuffer = createTestInstance();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(byteBuffer.totalBytesUnread());
        byteBuffer.writeTo(bytesOut);
        byte[] result = bytesOut.toByteArray();
        assertTrue(Arrays.equals(testbuffer, result));
    }

    @Test
    public void testToInputStreamOneByOne() throws IOException {
        StreamByteBuffer byteBuffer = createTestInstance();
        InputStream input = byteBuffer.getInputStream();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(byteBuffer.totalBytesUnread());
        copyOneByOne(input, bytesOut);
        byte[] result = bytesOut.toByteArray();
        assertTrue(Arrays.equals(testbuffer, result));
    }

    private int copy(InputStream input, OutputStream output, int bufSize) throws IOException {
        byte[] buffer = new byte[bufSize];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private int copyOneByOne(InputStream input, OutputStream output) throws IOException {
        int count = 0;
        int b;
        while (-1 != (b = input.read())) {
            output.write(b);
            count++;
        }
        return count;
    }

    private StreamByteBuffer createTestInstance() throws IOException {
        StreamByteBuffer byteBuffer = new StreamByteBuffer();
        OutputStream output = byteBuffer.getOutputStream();
        copyAllFromTestBuffer(output, 27);
        return byteBuffer;
    }

    private void copyAllFromTestBuffer(OutputStream output, int partsize) throws IOException {
        int position = 0;
        int bytesLeft = testbuffer.length;
        while (bytesLeft > 0) {
            output.write(testbuffer, position, partsize);
            position += partsize;
            bytesLeft -= partsize;
            if (bytesLeft < partsize) {
                partsize = bytesLeft;
            }
        }
    }
}