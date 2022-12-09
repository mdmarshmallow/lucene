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
package org.apache.lucene.facet.rangeonrange;

/**
 * Base class for a single labeled range.
 *
 * @lucene.experimental
 */
public abstract class Range {

  /** Label that identifies this range. */
  public final String label;

  /** Number of dims per range */
  public final int dims;

  /** Sole constructor. */
  protected Range(String label, int dims) {
    if (label == null) {
      throw new NullPointerException("label must not be null");
    }
    this.label = label;
    this.dims = dims;
  }

  /**
   * Gets the number of bytes it takes to represent an encoded value in a range
   *
   * @return the number of bytes to represent an encoded value in a range
   */
  public abstract int getEncodedValueNumBytes();

  /** Invoke this for a useless range. */
  protected void failNoMatch() {
    throw new IllegalArgumentException("range \"" + label + "\" matches nothing");
  }
}
