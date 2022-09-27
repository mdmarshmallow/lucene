/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.misc.store;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FlushInfo;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MergeInfo;
import org.apache.lucene.tests.store.BaseDirectoryTestCase;

public class TestByteWritesTrackingDirectoryWrapper extends BaseDirectoryTestCase {

  public void testEmptyDir() throws Exception {
    ByteWritesTrackingDirectoryWrapper dir =
        new ByteWritesTrackingDirectoryWrapper(new ByteBuffersDirectory());
    assertEquals(1.0, dir.getApproximateWriteAmplificationFactor(), 0.0);
    assertEquals(1.0, dir.getRealTimeApproximateWriteAmplificationFactor(), 0.0);
  }

  public void testRandomOutput() throws Exception {
    ByteWritesTrackingDirectoryWrapper dir =
        new ByteWritesTrackingDirectoryWrapper(new ByteBuffersDirectory());

    int flushBytes = random().nextInt(100);
    int mergeBytes = random().nextInt(100);
    double expectedWriteAmplification =
        ((double) flushBytes + (double) mergeBytes) / (double) flushBytes;

    IndexOutput output = dir.createOutput("write", new IOContext(new FlushInfo(10, flushBytes)));
    byte[] flushBytesArr = new byte[flushBytes];
    for (int i = 0; i < flushBytes; i++) {
      flushBytesArr[i] = (byte) random().nextInt(127);
    }
    output.writeBytes(flushBytesArr, flushBytesArr.length);
    assertEquals(1.0, dir.getApproximateWriteAmplificationFactor(), 0.0);
    assertEquals(1.0, dir.getRealTimeApproximateWriteAmplificationFactor(), 0.0);
    output.close();

    // now merge bytes
    output = dir.createOutput("merge", new IOContext(new MergeInfo(10, mergeBytes, false, 2)));
    byte[] mergeBytesArr = new byte[mergeBytes];
    for (int i = 0; i < mergeBytes; i++) {
      mergeBytesArr[i] = (byte) random().nextInt(127);
    }
    output.writeBytes(mergeBytesArr, mergeBytesArr.length);
    assertEquals(1.0, dir.getApproximateWriteAmplificationFactor(), 0.0);
    assertEquals(
        expectedWriteAmplification, dir.getRealTimeApproximateWriteAmplificationFactor(), 0.0);
    output.close();

    assertEquals(expectedWriteAmplification, dir.getApproximateWriteAmplificationFactor(), 0.0);
  }

  public void testRandomTempOutput() throws Exception {
    ByteWritesTrackingDirectoryWrapper dir =
        new ByteWritesTrackingDirectoryWrapper(new ByteBuffersDirectory(), true);

    int flushBytes = random().nextInt(100);
    int mergeBytes = random().nextInt(100);
    double expectedWriteAmplification =
        ((double) flushBytes + (double) mergeBytes) / (double) flushBytes;

    IndexOutput output =
        dir.createTempOutput("temp", "write", new IOContext(new FlushInfo(10, flushBytes)));
    byte[] flushBytesArr = new byte[flushBytes];
    for (int i = 0; i < flushBytes; i++) {
      flushBytesArr[i] = (byte) random().nextInt(127);
    }
    output.writeBytes(flushBytesArr, flushBytesArr.length);
    assertEquals(1.0, dir.getApproximateWriteAmplificationFactor(), 0.0);
    assertEquals(1.0, dir.getRealTimeApproximateWriteAmplificationFactor(), 0.0);
    output.close();

    // now merge bytes
    output =
        dir.createTempOutput(
            "temp", "merge", new IOContext(new MergeInfo(10, mergeBytes, false, 2)));
    byte[] mergeBytesArr = new byte[mergeBytes];
    for (int i = 0; i < mergeBytes; i++) {
      mergeBytesArr[i] = (byte) random().nextInt(127);
    }
    output.writeBytes(mergeBytesArr, mergeBytesArr.length);
    assertEquals(1.0, dir.getApproximateWriteAmplificationFactor(), 0.0);
    assertEquals(
        expectedWriteAmplification, dir.getRealTimeApproximateWriteAmplificationFactor(), 0.0);
    output.close();

    assertEquals(expectedWriteAmplification, dir.getApproximateWriteAmplificationFactor(), 0.0);
  }

  @Override
  protected Directory getDirectory(Path path) throws IOException {
    return new ByteWritesTrackingDirectoryWrapper(new ByteBuffersDirectory());
  }
}
