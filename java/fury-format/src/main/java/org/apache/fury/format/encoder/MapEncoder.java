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

package org.apache.fury.format.encoder;

import org.apache.arrow.vector.types.pojo.Field;
import org.apache.fury.format.row.binary.BinaryArray;
import org.apache.fury.format.row.binary.BinaryMap;

/** Encoder to encode/decode object in the map container by toMap/fromMap row. */
public interface MapEncoder<T> extends Encoder<T> {
  Field keyField();

  Field valueField();

  T fromMap(BinaryArray keyArray, BinaryArray valueArray);

  default T fromMap(BinaryMap binaryMap) {
    return fromMap(binaryMap.keyArray(), binaryMap.valueArray());
  }

  BinaryMap toMap(T obj);
}
