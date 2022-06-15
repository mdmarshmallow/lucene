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
package org.apache.lucene.facet.facetset;

import java.util.stream.IntStream;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.util.NumericUtils;

/** A {@link FacetSet} which encodes float dimension values. */
public class FloatFacetSet extends FacetSet {

  /** The raw dimension values of this facet set. */
  public final float[] floatValues;

  /** Constructs a new instance of a facet set which stores {@code float} dimension values. */
  public FloatFacetSet(float... values) {
    super(toLongValues());

    this.floatValues = values;
  }

  @Override
  public int packValues(byte[] buf, int start) {
    for (int i = 0, offset = start; i < floatValues.length; i++, offset += Float.BYTES) {
      FloatPoint.encodeDimension(floatValues[i], buf, offset);
    }
    return floatValues.length * Float.BYTES;
  }

  @Override
  public int sizePackedBytes() {
    return values.length * Float.BYTES;
  }

  // Unfortunately there's no FloatStream to convert this in one line.
  private static long[] toLongValues(float... values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("values must not be null or empty");
    }
    return IntStream.range(0, values.length).mapToLong(NumericUtils::floatToSortableInt).toArray();
  }
}
