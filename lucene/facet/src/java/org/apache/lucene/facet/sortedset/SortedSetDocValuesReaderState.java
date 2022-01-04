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
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.FixedBitSet;

/**
 * Wraps a {@link IndexReader} and resolves ords using existing {@link SortedSetDocValues} APIs
 * without a separate taxonomy index. This only supports flat facets (dimension + label), and it
 * makes faceting a bit slower, adds some cost at reopen time, but avoids managing the separate
 * taxonomy index. It also requires less RAM than the taxonomy index, as it manages the flat
 * (2-level) hierarchy more efficiently. In addition, the tie-break during faceting is now
 * meaningful (in label sorted order).
 *
 * <p><b>NOTE</b>: creating an instance of this class is somewhat costly, as it computes per-segment
 * ordinal maps, so you should create it once and re-use that one instance for a given {@link
 * IndexReader}.
 */
public abstract class SortedSetDocValuesReaderState implements Accountable {

  /** Holds start/end range of ords, which maps to one dimension. Only used for flat hierarchies. */
  public static final class OrdRange {
    /** Start of range, inclusive: */
    public final int start;
    /** End of range, inclusive: */
    public final int end;

    /** Start and end are inclusive. */
    public OrdRange(int start, int end) {
      this.start = start;
      this.end = end;
    }

    /** Iterates from start to end ord (inclusive) * */
    public PrimitiveIterator.OfInt iterator() {
      return new PrimitiveIterator.OfInt() {
        int current = start;

        @Override
        public int nextInt() {
          if (current > end) {
            return INVALID_ORDINAL;
          }
          return current++;
        }

        @Override
        public boolean hasNext() {
          return current <= end;
        }
      };
    }
  }

  /**
   * Holds children and sibling information for a single dimension. Only used with hierarchical
   * dimensions.
   */
  public static final class DimTree {
    private final FixedBitSet hasChildren;
    // TODO: This array can take up a lot of space. Change type based on input size maybe?
    private final int[] siblings;

    /** The first ord of the dimension * */
    public final int dimStartOrd;

    /** Sibling and children must be of same length * */
    public DimTree(int dimStartOrd, List<Integer> sibling, List<Boolean> hasChildren) {
      assert sibling.size() == hasChildren.size();
      this.hasChildren = new FixedBitSet(hasChildren.size());
      this.siblings = new int[sibling.size()];
      for (int i = 0; i < sibling.size(); i++) {
        if (hasChildren.get(i)) {
          assert i < sibling.size() - 1;
          this.hasChildren.set(i);
        }
        assert this.siblings[i] < sibling.size();
        this.siblings[i] = sibling.get(i);
      }
      this.dimStartOrd = dimStartOrd;
    }

    /** Iterates through all first level children of dimension * */
    public PrimitiveIterator.OfInt iterator() {
      return iterator(dimStartOrd);
    }

    /** Iterates through all children of given pathOrd * */
    public PrimitiveIterator.OfInt iterator(int pathOrd) {
      return new PrimitiveIterator.OfInt() {

        boolean atStart = true;
        int currentOrd = pathOrd - dimStartOrd;

        @Override
        public int nextInt() {
          if (atStart) {
            if (currentOrd < 0 || currentOrd >= hasChildren.length()) {
              return INVALID_ORDINAL;
            }
            atStart = false;
            if (hasChildren.get(currentOrd)) {
              currentOrd++;
              return currentOrd + dimStartOrd;
            } else {
              return INVALID_ORDINAL;
            }
          } else {
            currentOrd = siblings[currentOrd];
            return currentOrd + dimStartOrd;
          }
        }

        @Override
        public boolean hasNext() {
          if (atStart) {
            if (currentOrd < 0 || currentOrd >= hasChildren.length()) {
              return false;
            }
            return hasChildren.get(currentOrd);
          } else {
            return siblings[currentOrd] != INVALID_ORDINAL;
          }
        }
      };
    }
  }

  /** Invalid ordinal const */
  public static final int INVALID_ORDINAL = -1;

  /** Sole constructor. */
  protected SortedSetDocValuesReaderState() {}

  /** Return top-level doc values. */
  public abstract SortedSetDocValues getDocValues() throws IOException;

  /** Indexed field we are reading. */
  public abstract String getField();

  /** Returns top-level index reader. */
  public abstract IndexReader getReader();

  /** Number of unique labels. */
  public abstract int getSize();

  /** Returns if dim is configured as hierarchical ** */
  public abstract boolean isHierarchicalDim(String dim);

  /*** Only used for flat facets (dim/value) ***/

  /** Returns the {@link OrdRange} for this dimension. */
  public abstract OrdRange getOrdRange(String dim);

  /** Returns mapping from prefix to {@link OrdRange}. */
  public abstract Map<String, OrdRange> getPrefixToOrdRange();

  /*** Only used for hierarchical facets ***/

  public abstract DimTree getDimTree(String dim);

  /** Returns a list of all dimensions and their respective ordinals */
  public abstract Iterable<String> getDims();
}
