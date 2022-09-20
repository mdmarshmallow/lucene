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
package org.apache.lucene.store;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** An {@link IndexOutput} that wraps another instance and tracks the number of bytes written */
public class ByteTrackingIndexOutput extends IndexOutput {

  private final IndexOutput output;
  private final AtomicLong byteTracker;

  protected ByteTrackingIndexOutput(IndexOutput output, AtomicLong byteTracker) {
    super(
        "Byte tracking wrapper for: " + output.getName(),
        "ByteTrackingIndexOutput{" + output.getName() + "}");
    this.output = output;
    this.byteTracker = byteTracker;
  }

  @Override
  public void writeByte(byte b) throws IOException {
    byteTracker.incrementAndGet();
    output.writeByte(b);
  }

  @Override
  public void writeBytes(byte[] b, int offset, int length) throws IOException {
    byteTracker.addAndGet(length);
    output.writeBytes(b, offset, length);
  }

  @Override
  public void close() throws IOException {
    output.close();
  }

  @Override
  public long getFilePointer() {
    return output.getFilePointer();
  }

  @Override
  public long getChecksum() throws IOException {
    return output.getChecksum();
  }

  public String getWrappedName() {
    return output.getName();
  }

  public String getWrappedToString() {
    return output.toString();
  }
}
