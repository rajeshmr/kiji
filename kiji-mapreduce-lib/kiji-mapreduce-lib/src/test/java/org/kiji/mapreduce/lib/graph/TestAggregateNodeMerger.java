/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.mapreduce.lib.graph;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;

import org.kiji.mapreduce.lib.avro.Node;

public class TestAggregateNodeMerger {
  @Test
  public void testMeanNodeMerger() throws IOException {
    ArrayList<Node> nodes = new ArrayList<Node>();

    nodes.add(new NodeBuilder()
        .setLabel("a")
        .setWeight(2.0)
        .build());
    nodes.add(new NodeBuilder()
        .setLabel("a")
        .setWeight(6.0)
        .build());
    nodes.add(new NodeBuilder()
        .setLabel("a")
        .setWeight(13.0)
        .build());

    MeanNodeMerger nodeMerger = new MeanNodeMerger();
    Node mergedNode = nodeMerger.merge(nodes);
    assertEquals(7.0, mergedNode.getWeight(), 1e-8);
  }
}
