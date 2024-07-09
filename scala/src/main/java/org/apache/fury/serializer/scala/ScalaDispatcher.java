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

package org.apache.fury.serializer.scala;

import com.google.common.base.Preconditions;
import org.apache.fury.Fury;
import org.apache.fury.serializer.JavaSerializer;
import org.apache.fury.serializer.Serializer;
import org.apache.fury.serializer.SerializerFactory;
import scala.collection.generic.DefaultSerializable;

import java.lang.reflect.Method;

/**
 * Serializer dispatcher for scala types.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ScalaDispatcher implements SerializerFactory {

  /**
   * Get Serializer for scala type.
   *
   * @see DefaultSerializable
   * @see scala.collection.generic.DefaultSerializationProxy
   */
  @Override
  public Serializer createSerializer(Fury fury, Class<?> clz) {
    // Many map/seq/set types doesn't extends DefaultSerializable.
    if (scala.collection.SortedMap.class.isAssignableFrom(clz)) {
      return new ScalaSortedMapSerializer(fury, clz);
    } else if (scala.collection.Map.class.isAssignableFrom(clz)) {
      return new ScalaMapSerializer(fury, clz);
    } else if (scala.collection.SortedSet.class.isAssignableFrom(clz)) {
      return new ScalaSortedSetSerializer(fury, clz);
    } else if (scala.collection.Seq.class.isAssignableFrom(clz)) {
      return new ScalaSeqSerializer(fury, clz);
    }
    if (DefaultSerializable.class.isAssignableFrom(clz)) {
      Method replaceMethod = JavaSerializer.getWriteReplaceMethod(clz);
      Preconditions.checkNotNull(replaceMethod);
      return new ScalaCollectionSerializer(fury, clz);
    }
    return null;
  }
}
