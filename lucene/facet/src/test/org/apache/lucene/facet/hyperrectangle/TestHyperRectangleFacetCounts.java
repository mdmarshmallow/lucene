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
package org.apache.lucene.facet.hyperrectangle;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetTestCase;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;

public class TestHyperRectangleFacetCounts extends FacetTestCase {

  public void testBasicLong() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);

    for (long l = 0; l < 100; l++) {
      Document doc = new Document();
      LongPointFacetField field = new LongPointFacetField("field", l, l + 1L, l + 2L);
      doc.add(field);
      w.addDocument(doc);
    }

    // Also add point with Long.MAX_VALUE
    Document doc = new Document();
    LongPointFacetField field =
        new LongPointFacetField("field", Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE);
    doc.add(field);
    w.addDocument(doc);

    IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());

    Facets facets =
        new HyperRectangleFacetCounts(
            "field",
            fc,
            new LongHyperRectangle(
                "less than (10, 11, 12)",
                new LongHyperRectangle.LongRangePair(0L, true, 10L, false),
                new LongHyperRectangle.LongRangePair(0L, true, 11L, false),
                new LongHyperRectangle.LongRangePair(0L, true, 12L, false)),
            new LongHyperRectangle(
                "less than or equal to (10, 11, 12)",
                new LongHyperRectangle.LongRangePair(0L, true, 10L, true),
                new LongHyperRectangle.LongRangePair(0L, true, 11L, true),
                new LongHyperRectangle.LongRangePair(0L, true, 12L, true)),
            new LongHyperRectangle(
                "over (90, 91, 92)",
                new LongHyperRectangle.LongRangePair(90L, false, 100L, false),
                new LongHyperRectangle.LongRangePair(91L, false, 101L, false),
                new LongHyperRectangle.LongRangePair(92L, false, 102L, false)),
            new LongHyperRectangle(
                "(90, 91, 92) or above",
                new LongHyperRectangle.LongRangePair(90L, true, 100L, false),
                new LongHyperRectangle.LongRangePair(91L, true, 101L, false),
                new LongHyperRectangle.LongRangePair(92L, true, 102L, false)),
            new LongHyperRectangle(
                "over (1000, 1000, 1000)",
                new LongHyperRectangle.LongRangePair(1000L, false, Long.MAX_VALUE - 2L, true),
                new LongHyperRectangle.LongRangePair(1000L, false, Long.MAX_VALUE - 1L, true),
                new LongHyperRectangle.LongRangePair(1000L, false, Long.MAX_VALUE, true)));

    FacetResult result = facets.getTopChildren(10, "field");
    assertEquals(
        """
                        dim=field path=[] value=22 childCount=5
                          less than (10, 11, 12) (10)
                          less than or equal to (10, 11, 12) (11)
                          over (90, 91, 92) (9)
                          (90, 91, 92) or above (10)
                          over (1000, 1000, 1000) (1)
                        """,
        result.toString());

    // test getTopChildren(0, dim)
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          facets.getTopChildren(0, "field");
        });

    r.close();
    d.close();
  }

  public void testBasicDouble() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);

    for (double l = 0; l < 100; l++) {
      Document doc = new Document();
      DoublePointFacetField field = new DoublePointFacetField("field", l, l + 1.0, l + 2.0);
      doc.add(field);
      w.addDocument(doc);
    }

    // Also add point with Long.MAX_VALUE
    Document doc = new Document();
    DoublePointFacetField field =
        new DoublePointFacetField(
            "field", Double.MAX_VALUE - 2.0, Double.MAX_VALUE - 1.0, Double.MAX_VALUE);
    doc.add(field);
    w.addDocument(doc);

    IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());

    Facets facets =
        new HyperRectangleFacetCounts(
            "field",
            fc,
            new DoubleHyperRectangle(
                "less than (10, 11, 12)",
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 10.0, false),
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 11.0, false),
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 12.0, false)),
            new DoubleHyperRectangle(
                "less than or equal to (10, 11, 12)",
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 10.0, true),
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 11.0, true),
                new DoubleHyperRectangle.DoubleRangePair(0.0, true, 12.0, true)),
            new DoubleHyperRectangle(
                "over (90, 91, 92)",
                new DoubleHyperRectangle.DoubleRangePair(90.0, false, 100.0, false),
                new DoubleHyperRectangle.DoubleRangePair(91.0, false, 101.0, false),
                new DoubleHyperRectangle.DoubleRangePair(92.0, false, 102.0, false)),
            new DoubleHyperRectangle(
                "(90, 91, 92) or above",
                new DoubleHyperRectangle.DoubleRangePair(90.0, true, 100.0, false),
                new DoubleHyperRectangle.DoubleRangePair(91.0, true, 101.0, false),
                new DoubleHyperRectangle.DoubleRangePair(92.0, true, 102.0, false)),
            new DoubleHyperRectangle(
                "over (1000, 1000, 1000)",
                new DoubleHyperRectangle.DoubleRangePair(
                    1000.0, false, Double.MAX_VALUE - 2.0, true),
                new DoubleHyperRectangle.DoubleRangePair(
                    1000.0, false, Double.MAX_VALUE - 1.0, true),
                new DoubleHyperRectangle.DoubleRangePair(1000.0, false, Double.MAX_VALUE, true)));

    FacetResult result = facets.getTopChildren(10, "field");
    assertEquals(
        """
                        dim=field path=[] value=22 childCount=5
                          less than (10, 11, 12) (10)
                          less than or equal to (10, 11, 12) (11)
                          over (90, 91, 92) (9)
                          (90, 91, 92) or above (10)
                          over (1000, 1000, 1000) (1)
                        """,
        result.toString());

    // test getTopChildren(0, dim)
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          facets.getTopChildren(0, "field");
        });

    r.close();
    d.close();
  }
}
