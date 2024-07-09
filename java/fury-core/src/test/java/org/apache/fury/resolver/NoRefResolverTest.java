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

package org.apache.fury.resolver;

import static org.testng.Assert.*;

import org.apache.fury.memory.MemoryBuffer;
import org.testng.annotations.Test;

public class NoRefResolverTest {

  @Test
  public void testWriteRefOrNull() {
    NoRefResolver referenceResolver = new NoRefResolver();
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(32);
    assertTrue(referenceResolver.writeRefOrNull(buffer, null));
    assertFalse(referenceResolver.writeRefOrNull(buffer, new Object()));
    Object o = new Object();
    assertFalse(referenceResolver.writeRefOrNull(buffer, o));
    assertFalse(referenceResolver.writeRefOrNull(buffer, o));
    assertFalse(referenceResolver.writeNullFlag(buffer, o));
    assertTrue(referenceResolver.writeNullFlag(buffer, null));
  }
}
