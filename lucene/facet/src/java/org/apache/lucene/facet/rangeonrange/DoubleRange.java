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

import java.util.Arrays;
import java.util.Objects;

/** Represents a double range for RangeOnRange faceting */
public class DoubleRange extends Range {
  /** Minimum (inclusive). */
  public final double[] min;

  /** Maximum (inclusive). */
  public final double[] max;

  /**
   * Represents a double range for RangeOnRange faceting
   *
   * @param label the name of the range
   * @param minIn the minimum
   * @param minInclusive if the minimum is inclusive
   * @param maxIn the maximum
   * @param maxInclusive if the maximum is inclusive
   */
  public DoubleRange(
      String label, double minIn, boolean minInclusive, double maxIn, boolean maxInclusive) {
    super(label, 1);

    if (Double.isNaN(minIn)) {
      throw new IllegalArgumentException("min cannot be NaN");
    }
    if (!minInclusive) {
      minIn = Math.nextUp(minIn);
    }

    if (Double.isNaN(maxIn)) {
      throw new IllegalArgumentException("max cannot be NaN");
    }
    if (!maxInclusive) {
      // Why no Math.nextDown?
      maxIn = Math.nextAfter(maxIn, Double.NEGATIVE_INFINITY);
    }

    if (minIn > maxIn) {
      failNoMatch();
    }

    this.min = new double[] {minIn};
    this.max = new double[] {maxIn};
  }

  /**
   * Represents a double range for RangeOnRange faceting
   *
   * @param label the name of the range
   * @param min the minimum
   * @param max the maximum
   */
  public DoubleRange(String label, double[] min, double[] max) {
    super(label, min.length);
    checkArgs(min, max);
    this.min = min;
    this.max = max;
  }

  @Override
  public int getEncodedValueBytes() {
    return Double.BYTES;
  }

  @Override
  public String toString() {
    return "DoubleRange(label: "
        + label
        + ", min: "
        + Arrays.toString(min)
        + ", max: "
        + Arrays.toString(max)
        + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DoubleRange doubleRange = (DoubleRange) o;
    return Arrays.equals(min, doubleRange.min) && Arrays.equals(max, doubleRange.max);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, Arrays.hashCode(min), Arrays.hashCode(max));
  }

  /** validate the arguments */
  private static void checkArgs(final double[] min, final double[] max) {
    if (min == null || max == null || min.length == 0 || max.length == 0) {
      throw new IllegalArgumentException("min/max range values cannot be null or empty");
    }
    if (min.length != max.length) {
      throw new IllegalArgumentException("min/max ranges must agree");
    }

    for (int i = 0; i < min.length; i++) {
      if (min[i] > max[i]) {
        throw new IllegalArgumentException("min should be less than max");
      }
    }
  }
}
