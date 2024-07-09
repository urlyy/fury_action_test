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

package org.apache.fury.serializer.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.apache.fury.Fury;
import org.apache.fury.collection.Tuple2;
import org.apache.fury.logging.Logger;
import org.apache.fury.logging.LoggerFactory;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.Platform;
import org.apache.fury.serializer.Serializer;
import org.apache.fury.util.ExceptionUtils;

/** Serializer for synchronized Collections and Maps created via Collections. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SynchronizedSerializers {
  private static final Logger LOG = LoggerFactory.getLogger(SynchronizedSerializers.class);

  private static class Offset {
    // Graalvm unsafe offset substitution support: Make the call followed by a field store
    // directly or by a sign extend node followed directly by a field store.
    private static final long SOURCE_COLLECTION_FIELD_OFFSET;
    private static final long SOURCE_MAP_FIELD_OFFSET;

    static {
      String clsName = "java.util.Collections$SynchronizedCollection";
      try {
        SOURCE_COLLECTION_FIELD_OFFSET =
            Platform.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("c"));
      } catch (Exception e) {
        LOG.info("Could not access source collection field in {}", clsName);
        throw new RuntimeException(e);
      }
      clsName = "java.util.Collections$SynchronizedMap";
      try {
        SOURCE_MAP_FIELD_OFFSET =
            Platform.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("m"));
      } catch (Exception e) {
        LOG.info("Could not access source map field in {}", clsName);
        throw new RuntimeException(e);
      }
    }
  }

  public static final class SynchronizedCollectionSerializer
      extends CollectionSerializer<Collection> {

    private final Function factory;
    private final long offset;

    public SynchronizedCollectionSerializer(Fury fury, Class cls, Function factory, long offset) {
      super(fury, cls, false);
      this.factory = factory;
      this.offset = offset;
    }

    @Override
    public void write(MemoryBuffer buffer, Collection object) {
      // the ordinal could be replaced by s.th. else (e.g. a explicitly managed "id")
      Object unwrapped = Platform.getObject(object, offset);
      synchronized (object) {
        fury.writeRef(buffer, unwrapped);
      }
    }

    @Override
    public Collection read(MemoryBuffer buffer) {
      final Object sourceCollection = fury.readRef(buffer);
      return (Collection) factory.apply(sourceCollection);
    }
  }

  public static final class SynchronizedMapSerializer extends MapSerializer<Map> {
    private final Function factory;
    private final long offset;

    public SynchronizedMapSerializer(Fury fury, Class cls, Function factory, long offset) {
      super(fury, cls, false);
      this.factory = factory;
      this.offset = offset;
    }

    @Override
    public void write(MemoryBuffer buffer, Map object) {
      // the ordinal could be replaced by s.th. else (e.g. a explicitly managed "id")
      Object unwrapped = Platform.getObject(object, offset);
      synchronized (object) {
        fury.writeRef(buffer, unwrapped);
      }
    }

    @Override
    public Map read(MemoryBuffer buffer) {
      final Object sourceCollection = fury.readRef(buffer);
      return (Map) factory.apply(sourceCollection);
    }
  }

  static Serializer createSerializer(Fury fury, Class<?> cls) {
    for (Tuple2<Class<?>, Function> factory : synchronizedFactories()) {
      if (factory.f0 == cls) {
        return createSerializer(fury, factory);
      }
    }
    throw new IllegalArgumentException("Unsupported type " + cls);
  }

  private static Serializer<?> createSerializer(Fury fury, Tuple2<Class<?>, Function> factory) {
    if (Collection.class.isAssignableFrom(factory.f0)) {
      return new SynchronizedCollectionSerializer(
          fury, factory.f0, factory.f1, Offset.SOURCE_COLLECTION_FIELD_OFFSET);
    } else {
      return new SynchronizedMapSerializer(
          fury, factory.f0, factory.f1, Offset.SOURCE_MAP_FIELD_OFFSET);
    }
  }

  static Tuple2<Class<?>, Function>[] synchronizedFactories() {
    Tuple2<Class<?>, Function> collectionFactory =
        Tuple2.of(
            Collections.synchronizedCollection(Arrays.asList("")).getClass(),
            o -> Collections.synchronizedCollection((Collection) o));
    Tuple2<Class<?>, Function> randomListFactory =
        Tuple2.of(
            Collections.synchronizedList(new ArrayList<Void>()).getClass(),
            o -> Collections.synchronizedList((List<?>) o));
    Tuple2<Class<?>, Function> listFactory =
        Tuple2.of(
            Collections.synchronizedList(new LinkedList<Void>()).getClass(),
            o -> Collections.synchronizedList((List<?>) o));
    Tuple2<Class<?>, Function> setFactory =
        Tuple2.of(
            Collections.synchronizedSet(new HashSet<Void>()).getClass(),
            o -> Collections.synchronizedSet((Set<?>) o));
    Tuple2<Class<?>, Function> sortedsetFactory =
        Tuple2.of(
            Collections.synchronizedSortedSet(new TreeSet<>()).getClass(),
            o -> Collections.synchronizedSortedSet((TreeSet<?>) o));
    Tuple2<Class<?>, Function> mapFactory =
        Tuple2.of(
            Collections.synchronizedMap(new HashMap<Void, Void>()).getClass(),
            o -> Collections.synchronizedMap((Map) o));
    Tuple2<Class<?>, Function> sortedmapFactory =
        Tuple2.of(
            Collections.synchronizedSortedMap(new TreeMap<>()).getClass(),
            o -> Collections.synchronizedSortedMap((SortedMap) o));
    return new Tuple2[] {
      collectionFactory,
      randomListFactory,
      listFactory,
      setFactory,
      sortedsetFactory,
      mapFactory,
      sortedmapFactory
    };
  }

  /**
   * Registering serializers for synchronized Collections and Maps created via {@link Collections}.
   *
   * @see Collections#synchronizedCollection(Collection)
   * @see Collections#synchronizedList(List)
   * @see Collections#synchronizedSet(Set)
   * @see Collections#synchronizedSortedSet(SortedSet)
   * @see Collections#synchronizedMap(Map)
   * @see Collections#synchronizedSortedMap(SortedMap)
   */
  public static void registerSerializers(Fury fury) {
    try {
      for (Tuple2<Class<?>, Function> factory : synchronizedFactories()) {
        fury.registerSerializer(factory.f0, createSerializer(fury, factory));
      }
    } catch (Throwable e) {
      ExceptionUtils.ignore(e);
    }
  }
}
