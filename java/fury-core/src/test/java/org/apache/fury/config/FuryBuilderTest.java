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

package org.apache.fury.config;

import static org.testng.Assert.*;

import org.apache.fury.meta.MetaCompressor;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FuryBuilderTest {

  @Test
  public void testWithMetaCompressor() {
    MetaCompressor metaCompressor =
        new FuryBuilder()
            .withMetaCompressor(
                new MetaCompressor() {
                  @Override
                  public byte[] compress(byte[] data, int offset, int size) {
                    return new byte[0];
                  }

                  @Override
                  public byte[] decompress(byte[] compressedData, int offset, int size) {
                    return new byte[0];
                  }
                })
            .metaCompressor;
    Assert.assertEquals(metaCompressor.getClass().getSimpleName(), "TypeEqualMetaCompressor");
    new FuryBuilder()
        .withMetaCompressor(
            new MetaCompressor() {
              @Override
              public byte[] compress(byte[] data, int offset, int size) {
                return new byte[0];
              }

              @Override
              public byte[] decompress(byte[] compressedData, int offset, int size) {
                return new byte[0];
              }

              @Override
              public boolean equals(Object o) {
                if (this == o) {
                  return true;
                }
                return o != null && getClass() == o.getClass();
              }

              @Override
              public int hashCode() {
                return getClass().hashCode();
              }
            });
  }
}
