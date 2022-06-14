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

/**
 * A {@link FacetSetMatcher} which considers a set as a match only if all dimension values are equal
 * to the given one.
 *
 * @lucene.experimental
 */
public class ExactFacetSetMatcher extends FacetSetMatcher {

  private final long[] values;

  /** Constructs an instance to match the given facet set. */
  public ExactFacetSetMatcher(String label, FacetSet facetSet) {
    super(label, facetSet.values.length);
    this.values = facetSet.values;
  }

  @Override
  public boolean matches(long[] dimValues) {
    assert dimValues.length == dims
        : "Encoded dimensions (dims="
            + dimValues.length
            + ") is incompatible with FacetSet dimensions (dims="
            + dims
            + ")";

    for (int i = 0; i < dimValues.length; i++) {
      if (dimValues[i] != values[i]) {
        // Field's dimension value is not equal to given dimension, the entire set is rejected
        return false;
      }
    }
    return true;
  }
}
