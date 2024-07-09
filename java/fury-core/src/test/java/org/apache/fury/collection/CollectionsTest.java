/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fury.collection;

import static org.testng.Assert.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import org.testng.annotations.Test;

public class CollectionsTest {

  @Test
  public void testOfArrayList() {
    assertEquals(Collections.ofArrayList(1, 2), new ArrayList<>(ImmutableList.of(1, 2)));
  }

  @Test
  public void testOfHashMap() {
    assertEquals(Collections.ofHashMap(1, 2, 3, 4), new HashMap<>(ImmutableMap.of(1, 2, 3, 4)));
  }
}
