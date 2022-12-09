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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.BinaryRangeDocValues;
import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetCountsWithFilterQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.PriorityQueue;

abstract class RangeOnRangeFacetCounts extends FacetCountsWithFilterQuery {

  private final Range[] ranges;
  private final byte[][] encodedRanges;
  private final int numEncodedValueBytes;
  private final int dims;

  /** Counts, initialized in by subclass. */
  protected final int[] counts;

  /** Our field name. */
  protected final String field;

  /** Total number of hits. */
  protected int totCount;

  private final ArrayUtil.ByteArrayComparator comparator;

  /** Type of "range overlap" we want to count. */
  RangeFieldQuery.QueryType queryType;

  protected RangeOnRangeFacetCounts(
      String field,
      FacetsCollector hits,
      RangeFieldQuery.QueryType queryType,
      Query fastMatchQuery,
      byte[][] encodedRanges,
      Range... ranges)
      throws IOException {
    super(fastMatchQuery);
    this.field = field;
    this.ranges = ranges;
    this.numEncodedValueBytes = ranges[0].getEncodedValueNumBytes();
    this.dims = ranges[0].dims;
    this.queryType = queryType;
    this.comparator = ArrayUtil.getUnsignedComparator(this.numEncodedValueBytes);
    this.encodedRanges = verifyEncodedRange(encodedRanges);
    this.counts = new int[ranges.length];
    count(field, hits.getMatchingDocs());
  }

  /** Counts from the provided field. */
  protected void count(String field, List<FacetsCollector.MatchingDocs> matchingDocs)
      throws IOException {
    // TODO: We currently just exhaustively check the ranges in each document with every range in
    // the ranges array.
    // We might be able to do something more efficient here by grouping the ranges array into a
    // space partitioning
    // data structure of some sort.

    int missingCount = 0;

    for (FacetsCollector.MatchingDocs hits : matchingDocs) {

      BinaryRangeDocValues binaryRangeDocValues =
          new BinaryRangeDocValues(
              DocValues.getBinary(hits.context.reader(), field), dims, numEncodedValueBytes);

      final DocIdSetIterator it = createIterator(hits);
      if (it == null) {
        continue;
      }

      totCount += hits.totalHits;
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; ) {
        if (binaryRangeDocValues.advanceExact(doc)) {
          boolean hasValidRange = false;
          for (int range = 0; range < ranges.length; range++) {
            byte[] encodedRange = encodedRanges[range];
            byte[] packedRange = binaryRangeDocValues.getPackedValue();
            assert encodedRange.length == packedRange.length;
            if (queryType.matches(
                encodedRange, packedRange, dims, numEncodedValueBytes, comparator)) {
              counts[range]++;
              hasValidRange = true;
            }
          }
          if (hasValidRange == false) {
            missingCount++;
          }
        } else {
          missingCount++;
        }
        doc = it.nextDoc();
      }
    }
    totCount -= missingCount;
  }

  /**
   * {@inheritDoc}
   *
   * <p>NOTE: This implementation guarantees that ranges will be returned in the order specified by
   * the user when calling the constructor.
   */
  @Override
  public FacetResult getAllChildren(String dim, String... path) throws IOException {
    validateDimAndPathForGetChildren(dim, path);
    LabelAndValue[] labelValues = new LabelAndValue[counts.length];
    for (int i = 0; i < counts.length; i++) {
      labelValues[i] = new LabelAndValue(ranges[i].label, counts[i]);
    }
    return new FacetResult(dim, path, totCount, labelValues, labelValues.length);
  }

  @Override
  public FacetResult getTopChildren(int topN, String dim, String... path) throws IOException {
    validateTopN(topN);
    validateDimAndPathForGetChildren(dim, path);

    PriorityQueue<Entry> pq =
        new PriorityQueue<>(Math.min(topN, counts.length)) {
          @Override
          protected boolean lessThan(Entry a, Entry b) {
            int cmp = Integer.compare(a.count, b.count);
            if (cmp == 0) {
              cmp = b.label.compareTo(a.label);
            }
            return cmp < 0;
          }
        };

    int childCount = 0;
    Entry e = null;
    for (int i = 0; i < counts.length; i++) {
      if (counts[i] != 0) {
        childCount++;
        if (e == null) {
          e = new Entry();
        }
        e.label = ranges[i].label;
        e.count = counts[i];
        e = pq.insertWithOverflow(e);
      }
    }

    LabelAndValue[] results = new LabelAndValue[pq.size()];
    while (pq.size() != 0) {
      Entry entry = pq.pop();
      assert entry != null;
      results[pq.size()] = new LabelAndValue(entry.label, entry.count);
    }
    return new FacetResult(dim, path, totCount, results, childCount);
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

  private void validateDimAndPathForGetChildren(String dim, String... path) {
    if (dim.equals(field) == false) {
      throw new IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + field + "\"");
    }
    if (path.length != 0) {
      throw new IllegalArgumentException("path.length should be 0");
    }
  }

  private byte[][] verifyEncodedRange(byte[][] encodedRanges) {
    assert encodedRanges.length == ranges.length;
    assert encodedRanges[0].length == numEncodedValueBytes * dims * 2;
    return encodedRanges;
  }

  private static final class Entry {
    int count;
    String label;
  }
}
