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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

/**
 * Returns the countBytes for each given {@link FacetSet}
 *
 * @lucene.experimental
 */
public class MatchingFacetSetsCounts extends Facets {

  private final FacetSetMatcher[] facetSetMatchers;
  private final int[] counts;
  private final String field;

  private int totCount;

  /**
   * Constructs a new instance of matching facet set counts which calculates the countBytes for each
   * given facet set matcher.
   */
  public MatchingFacetSetsCounts(
      String field, FacetsCollector hits, boolean countBytes, FacetSetMatcher... facetSetMatchers)
      throws IOException {
    if (facetSetMatchers == null || facetSetMatchers.length == 0) {
      throw new IllegalArgumentException("facetSetMatchers cannot be null or empty");
    }
    if (areFacetSetMatcherDimensionsInconsistent(facetSetMatchers)) {
      throw new IllegalArgumentException("All facet set matchers must be the same dimensionality");
    }
    this.field = field;
    this.facetSetMatchers = facetSetMatchers;
    this.counts = new int[facetSetMatchers.length];
    if (countBytes) {
      countBytes(field, hits.getMatchingDocs());
    } else {
      countLongs(field, hits.getMatchingDocs());
    }
  }

  /** Counts from the provided field. */
  private void countBytes(String field, List<FacetsCollector.MatchingDocs> matchingDocs)
      throws IOException {

    for (FacetsCollector.MatchingDocs hits : matchingDocs) {

      BinaryDocValues binaryDocValues = DocValues.getBinary(hits.context.reader(), field);

      final DocIdSetIterator it =
          ConjunctionUtils.intersectIterators(Arrays.asList(hits.bits.iterator(), binaryDocValues));
      if (it == null) {
        continue;
      }

      int expectedNumDims = -1;
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        boolean shouldCountDoc = false;
        BytesRef bytesRef = binaryDocValues.binaryValue();
        byte[] packedValue = bytesRef.bytes;
        int numDims = (int) LongPoint.decodeDimension(packedValue, 0);
        if (expectedNumDims == -1) {
          expectedNumDims = numDims;
        } else {
          // Verify that the number of indexed dimensions for all matching documents is the same
          // (since we cannot verify that at indexing time).
          assert numDims == expectedNumDims
              : "Expected ("
                  + expectedNumDims
                  + ") dimensions, found ("
                  + numDims
                  + ") for doc ("
                  + doc
                  + ")";
        }
        for (int start = Long.BYTES;
            start < bytesRef.length;
            start += numDims * Long.BYTES) { // for each facet set
          for (int j = 0; j < facetSetMatchers.length; j++) { // for each facet set matcher
            if (facetSetMatchers[j].matches(packedValue, start, numDims)) {
              counts[j]++;
              shouldCountDoc = true;
            }
          }
        }
        if (shouldCountDoc) {
          totCount++;
        }
      }
    }
  }

  /** Counts from the provided field. */
  private void countLongs(String field, List<FacetsCollector.MatchingDocs> matchingDocs)
      throws IOException {

    for (FacetsCollector.MatchingDocs hits : matchingDocs) {

      BinaryDocValues binaryDocValues = DocValues.getBinary(hits.context.reader(), field);

      final DocIdSetIterator it =
          ConjunctionUtils.intersectIterators(Arrays.asList(hits.bits.iterator(), binaryDocValues));
      if (it == null) {
        continue;
      }

      long[] dimValues = null; // dimension values buffer
      int expectedNumDims = -1;
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        boolean shouldCountDoc = false;
        BytesRef bytesRef = binaryDocValues.binaryValue();
        byte[] packedValue = bytesRef.bytes;
        int numDims = (int) LongPoint.decodeDimension(packedValue, 0);
        if (expectedNumDims == -1) {
          expectedNumDims = numDims;
          dimValues = new long[numDims];
        } else {
          // Verify that the number of indexed dimensions for all matching documents is the same
          // (since we cannot verify that at indexing time).
          assert numDims == expectedNumDims
              : "Expected ("
                  + expectedNumDims
                  + ") dimensions, found ("
                  + numDims
                  + ") for doc ("
                  + doc
                  + ")";
        }
        for (int start = Long.BYTES;
            start < bytesRef.length;
            start += numDims * Long.BYTES) { // for each facet set
          for (int i = 0, offset = start; i < dimValues.length; i++, offset += Long.BYTES) {
            dimValues[i] = LongPoint.decodeDimension(packedValue, offset);
          }
          for (int j = 0; j < facetSetMatchers.length; j++) { // for each facet set matcher
            if (facetSetMatchers[j].matches(dimValues, numDims)) {
              counts[j]++;
              shouldCountDoc = true;
            }
          }
        }
        if (shouldCountDoc) {
          totCount++;
        }
      }
    }
  }

  // TODO: This does not really provide "top children" functionality yet but provides "all
  // children". This is being worked on in LUCENE-10550
  @Override
  public FacetResult getTopChildren(int topN, String dim, String... path) throws IOException {
    validateTopN(topN);
    if (!field.equals(dim)) {
      throw new IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + field + "\"");
    }
    if (path != null && path.length != 0) {
      throw new IllegalArgumentException("path.length should be 0");
    }
    LabelAndValue[] labelValues = new LabelAndValue[counts.length];
    for (int i = 0; i < counts.length; i++) {
      labelValues[i] = new LabelAndValue(facetSetMatchers[i].label, counts[i]);
    }
    return new FacetResult(dim, path, totCount, labelValues, labelValues.length);
  }

  @Override
  public Number getSpecificValue(String dim, String... path) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<FacetResult> getAllDims(int topN) throws IOException {
    validateTopN(topN);
    return Collections.singletonList(getTopChildren(topN, field));
  }

  private static boolean areFacetSetMatcherDimensionsInconsistent(
      FacetSetMatcher[] facetSetMatchers) {
    int dims = facetSetMatchers[0].dims;
    return Arrays.stream(facetSetMatchers)
        .anyMatch(facetSetMatcher -> facetSetMatcher.dims != dims);
  }
}
