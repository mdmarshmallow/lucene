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
package org.apache.lucene.facet.sortedset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetUtils;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.TopOrdAndIntQueue;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState.DimAndOrd;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiDocValues.MultiSortedSetDocValues;
import org.apache.lucene.index.OrdinalMap;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LongValues;

/**
 * Compute facets counts from previously indexed {@link SortedSetDocValuesFacetField}, without
 * require a separate taxonomy index. Faceting is a bit slower (~25%), and there is added cost on
 * every {@link IndexReader} open to create a new {@link SortedSetDocValuesReaderState}.
 * Furthermore, this does not support hierarchical facets; only flat (dimension + label) facets, but
 * it uses quite a bit less RAM to do so.
 *
 * <p><b>NOTE</b>: this class should be instantiated and then used from a single thread, because it
 * holds a thread-private instance of {@link SortedSetDocValues}.
 *
 * <p><b>NOTE:</b>: tie-break is by unicode sort order
 *
 * @lucene.experimental
 */
public class SortedSetDocValuesFacetCounts extends Facets {

  final SortedSetDocValuesReaderState state;
  final SortedSetDocValues dv;
  final String field;
  final int[] counts;

  /** Returns all facet counts, same result as searching on {@link MatchAllDocsQuery} but faster. */
  public SortedSetDocValuesFacetCounts(SortedSetDocValuesReaderState state) throws IOException {
    this(state, null);
  }

  /** Counts all facet dimensions across the provided hits. */
  public SortedSetDocValuesFacetCounts(SortedSetDocValuesReaderState state, FacetsCollector hits)
      throws IOException {
    this.state = state;
    this.field = state.getField();
    dv = state.getDocValues();
    counts = new int[state.getSize()];
    if (hits == null) {
      // browse only
      countAll();
    } else {
      count(hits.getMatchingDocs());
    }
  }

  @Override
  public FacetResult getTopChildren(int topN, String dim, String... path) throws IOException {
    if (topN <= 0) {
      throw new IllegalArgumentException("topN must be > 0 (got: " + topN + ")");
    }

    int pathOrd = (int) dv.lookupTerm(new BytesRef(FacetsConfig.pathToString(dim, path)));
    if (pathOrd == -1) {
      // path was never indexed
      return null;
    }

    Iterable<Integer> childOrds = state.childOrds(pathOrd);

    return getDim(dim, path, pathOrd, childOrds, topN);
  }

  private FacetResult getDim(
      String dim, String[] path, int pathOrd, Iterable<Integer> childOrds, int topN)
      throws IOException {

    TopOrdAndIntQueue q = null;

    int bottomCount = 0;

    int childCount = 0;

    TopOrdAndIntQueue.OrdAndValue reuse = null;
    for (int ord : childOrds) {
      if (counts[ord] > 0) {
        childCount++;
        if (counts[ord] > bottomCount) {
          if (reuse == null) {
            reuse = new TopOrdAndIntQueue.OrdAndValue();
          }
          reuse.ord = ord;
          reuse.value = counts[ord];
          if (q == null) {
            // Lazy init, so we don't create this for the
            // sparse case unnecessarily
            q = new TopOrdAndIntQueue(topN);
          }
          reuse = q.insertWithOverflow(reuse);
          if (q.size() == topN) {
            bottomCount = q.top().value;
          }
        }
      }
    }

    if (q == null) {
      return null;
    }

    LabelAndValue[] labelValues = new LabelAndValue[q.size()];
    for (int i = labelValues.length - 1; i >= 0; i--) {
      TopOrdAndIntQueue.OrdAndValue ordAndValue = q.pop();
      assert ordAndValue != null;
      final BytesRef term = dv.lookupOrd(ordAndValue.ord);
      String[] parts = FacetsConfig.stringToPath(term.utf8ToString());
      labelValues[i] = new LabelAndValue(parts[parts.length - 1], ordAndValue.value);
    }

    return new FacetResult(dim, path, counts[pathOrd], labelValues, childCount);
  }

  private void countOneSegment(
      OrdinalMap ordinalMap, LeafReader reader, int segOrd, MatchingDocs hits, Bits liveDocs)
      throws IOException {
    SortedSetDocValues multiValues = DocValues.getSortedSet(reader, field);
    if (multiValues == null) {
      // nothing to count
      return;
    }

    // It's slightly more efficient to work against SortedDocValues if the field is actually
    // single-valued (see: LUCENE-5309)
    SortedDocValues singleValues = DocValues.unwrapSingleton(multiValues);
    DocIdSetIterator valuesIt = singleValues != null ? singleValues : multiValues;

    DocIdSetIterator it;
    if (hits == null) {
      it = (liveDocs != null) ? FacetUtils.liveDocsDISI(valuesIt, liveDocs) : valuesIt;
    } else {
      it = ConjunctionUtils.intersectIterators(Arrays.asList(hits.bits.iterator(), valuesIt));
    }

    // TODO: yet another option is to count all segs
    // first, only in seg-ord space, and then do a
    // merge-sort-PQ in the end to only "resolve to
    // global" those seg ords that can compete, if we know
    // we just want top K?  ie, this is the same algo
    // that'd be used for merging facets across shards
    // (distributed faceting).  but this has much higher
    // temp ram req'ts (sum of number of ords across all
    // segs)
    if (ordinalMap != null) {
      final LongValues ordMap = ordinalMap.getGlobalOrds(segOrd);

      int numSegOrds = (int) multiValues.getValueCount();

      if (hits != null && hits.totalHits < numSegOrds / 10) {
        // Remap every ord to global ord as we iterate:
        if (singleValues != null) {
          for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
            counts[(int) ordMap.get(singleValues.ordValue())]++;
          }
        } else {
          for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
            int term = (int) multiValues.nextOrd();
            while (term != SortedSetDocValues.NO_MORE_ORDS) {
              counts[(int) ordMap.get(term)]++;
              term = (int) multiValues.nextOrd();
            }
          }
        }
      } else {
        // First count in seg-ord space:
        final int[] segCounts = new int[numSegOrds];
        if (singleValues != null) {
          for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
            segCounts[singleValues.ordValue()]++;
          }
        } else {
          for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
            int term = (int) multiValues.nextOrd();
            while (term != SortedSetDocValues.NO_MORE_ORDS) {
              segCounts[term]++;
              term = (int) multiValues.nextOrd();
            }
          }
        }

        // Then, migrate to global ords:
        for (int ord = 0; ord < numSegOrds; ord++) {
          int count = segCounts[ord];
          if (count != 0) {
            // ordinalMap.getGlobalOrd(segOrd, ord));
            counts[(int) ordMap.get(ord)] += count;
          }
        }
      }
    } else {
      // No ord mapping (e.g., single segment index):
      // just aggregate directly into counts:
      if (singleValues != null) {
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          counts[singleValues.ordValue()]++;
        }
      } else {
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          int term = (int) multiValues.nextOrd();
          while (term != SortedSetDocValues.NO_MORE_ORDS) {
            counts[term]++;
            term = (int) multiValues.nextOrd();
          }
        }
      }
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private void count(List<MatchingDocs> matchingDocs) throws IOException {

    OrdinalMap ordinalMap;

    // TODO: is this right?  really, we need a way to
    // verify that this ordinalMap "matches" the leaves in
    // matchingDocs...
    if (dv instanceof MultiDocValues.MultiSortedSetDocValues && matchingDocs.size() > 1) {
      ordinalMap = ((MultiSortedSetDocValues) dv).mapping;
    } else {
      ordinalMap = null;
    }

    IndexReader reader = state.getReader();

    for (MatchingDocs hits : matchingDocs) {

      // LUCENE-5090: make sure the provided reader context "matches"
      // the top-level reader passed to the
      // SortedSetDocValuesReaderState, else cryptic
      // AIOOBE can happen:
      if (ReaderUtil.getTopLevelContext(hits.context).reader() != reader) {
        throw new IllegalStateException(
            "the SortedSetDocValuesReaderState provided to this class does not match the reader being searched; you must create a new SortedSetDocValuesReaderState every time you open a new IndexReader");
      }

      countOneSegment(ordinalMap, hits.context.reader(), hits.context.ord, hits, null);
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private void countAll() throws IOException {

    OrdinalMap ordinalMap;

    // TODO: is this right?  really, we need a way to
    // verify that this ordinalMap "matches" the leaves in
    // matchingDocs...
    if (dv instanceof MultiDocValues.MultiSortedSetDocValues) {
      ordinalMap = ((MultiSortedSetDocValues) dv).mapping;
    } else {
      ordinalMap = null;
    }

    for (LeafReaderContext context : state.getReader().leaves()) {

      countOneSegment(
          ordinalMap, context.reader(), context.ord, null, context.reader().getLiveDocs());
    }
  }

  @Override
  public Number getSpecificValue(String dim, String... path) throws IOException {
    if (path.length != 1) {
      throw new IllegalArgumentException("path must be length=1");
    }
    int ord = (int) dv.lookupTerm(new BytesRef(FacetsConfig.pathToString(dim, path)));
    if (ord < 0) {
      return -1;
    }

    return counts[ord];
  }

  @Override
  public List<FacetResult> getAllDims(int topN) throws IOException {

    List<FacetResult> results = new ArrayList<>();
    for (DimAndOrd dim : state.getDims()) {
      Iterable<Integer> childOrds = state.childOrds(dim.ord);
      FacetResult fr = getDim(dim.dim, new String[0], dim.ord, childOrds, topN);
      if (fr != null) {
        results.add(fr);
      }
    }

    // Sort by highest count:
    results.sort(
        (a, b) -> {
          if (a.value.intValue() > b.value.intValue()) {
            return -1;
          } else if (b.value.intValue() > a.value.intValue()) {
            return 1;
          } else {
            return a.dim.compareTo(b.dim);
          }
        });

    return results;
  }
}
