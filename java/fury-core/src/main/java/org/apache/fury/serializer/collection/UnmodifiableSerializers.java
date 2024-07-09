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
import org.apache.fury.util.Preconditions;

/** Serializer for unmodifiable Collections and Maps created via Collections. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class UnmodifiableSerializers {
  private static final Logger LOG = LoggerFactory.getLogger(UnmodifiableSerializers.class);

  private static class Offset {
    // Graalvm unsafe offset substitution support: Make the call followed by a field store
    // directly or by a sign extend node followed directly by a field store.
    private static final long SOURCE_COLLECTION_FIELD_OFFSET;
    private static final long SOURCE_MAP_FIELD_OFFSET;

    static {
      // UnmodifiableList/Set/Etc.. extends UnmodifiableCollection
      String clsName = "java.util.Collections$UnmodifiableCollection";
      try {
        SOURCE_COLLECTION_FIELD_OFFSET =
            Platform.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("c"));
      } catch (Exception e) {
        LOG.info("Could not access source collection field in {}", clsName);
        throw new RuntimeException(e);
      }
      clsName = "java.util.Collections$UnmodifiableMap";
      try {
        // UnmodifiableSortedMap/UnmodifiableNavigableMap extends UnmodifiableMap
        SOURCE_MAP_FIELD_OFFSET =
            Platform.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("m"));
      } catch (Exception e) {
        LOG.info("Could not access source map field in {}", clsName);
        throw new RuntimeException(e);
      }
    }
  }

  public static final class UnmodifiableCollectionSerializer
      extends CollectionSerializer<Collection> {
    private final Function factory;
    private final long offset;

    public UnmodifiableCollectionSerializer(Fury fury, Class cls, Function factory, long offset) {
      super(fury, cls, false);
      this.factory = factory;
      this.offset = offset;
    }

    @Override
    public void write(MemoryBuffer buffer, Collection value) {
      Preconditions.checkArgument(value.getClass() == type);
      Object fieldValue = Platform.getObject(value, offset);
      fury.writeRef(buffer, fieldValue);
    }

    @Override
    public Collection read(MemoryBuffer buffer) {
      final Object sourceCollection = fury.readRef(buffer);
      return (Collection) factory.apply(sourceCollection);
    }
  }

  public static final class UnmodifiableMapSerializer extends MapSerializer<Map> {
    private final Function factory;
    private final long offset;

    public UnmodifiableMapSerializer(Fury fury, Class cls, Function factory, long offset) {
      super(fury, cls, false);
      this.factory = factory;
      this.offset = offset;
    }

    @Override
    public void write(MemoryBuffer buffer, Map value) {
      Preconditions.checkArgument(value.getClass() == type);
      Object fieldValue = Platform.getObject(value, offset);
      fury.writeRef(buffer, fieldValue);
    }

    @Override
    public Map read(MemoryBuffer buffer) {
      final Object sourceCollection = fury.readRef(buffer);
      return (Map) factory.apply(sourceCollection);
    }
  }

  static Serializer createSerializer(Fury fury, Class<?> cls) {
    for (Tuple2<Class<?>, Function> factory : unmodifiableFactories()) {
      if (factory.f0 == cls) {
        return createSerializer(fury, factory);
      }
    }
    throw new IllegalArgumentException("Unsupported type " + cls);
  }

  private static Serializer<?> createSerializer(Fury fury, Tuple2<Class<?>, Function> factory) {
    if (Collection.class.isAssignableFrom(factory.f0)) {
      return new UnmodifiableCollectionSerializer(
          fury, factory.f0, factory.f1, Offset.SOURCE_COLLECTION_FIELD_OFFSET);
    } else {
      return new UnmodifiableMapSerializer(
          fury, factory.f0, factory.f1, Offset.SOURCE_MAP_FIELD_OFFSET);
    }
  }

  static Tuple2<Class<?>, Function>[] unmodifiableFactories() {
    Tuple2<Class<?>, Function> collectionFactory =
        Tuple2.of(
            Collections.unmodifiableCollection(Collections.singletonList("")).getClass(),
            o -> Collections.unmodifiableCollection((Collection) o));
    Tuple2<Class<?>, Function> randomAccessListFactory =
        Tuple2.of(
            Collections.unmodifiableList(new ArrayList<Void>()).getClass(),
            o -> Collections.unmodifiableList((List<?>) o));
    Tuple2<Class<?>, Function> listFactory =
        Tuple2.of(
            Collections.unmodifiableList(new LinkedList<Void>()).getClass(),
            o -> Collections.unmodifiableList((List<?>) o));
    Tuple2<Class<?>, Function> setFactory =
        Tuple2.of(
            Collections.unmodifiableSet(new HashSet<Void>()).getClass(),
            o -> Collections.unmodifiableSet((Set<?>) o));
    Tuple2<Class<?>, Function> sortedsetFactory =
        Tuple2.of(
            Collections.unmodifiableSortedSet(new TreeSet<>()).getClass(),
            o -> Collections.unmodifiableSortedSet((SortedSet<?>) o));
    Tuple2<Class<?>, Function> mapFactory =
        Tuple2.of(
            Collections.unmodifiableMap(new HashMap<>()).getClass(),
            o -> Collections.unmodifiableMap((Map) o));
    Tuple2<Class<?>, Function> sortedmapFactory =
        Tuple2.of(
            Collections.unmodifiableSortedMap(new TreeMap<>()).getClass(),
            o -> Collections.unmodifiableSortedMap((SortedMap) o));
    return new Tuple2[] {
      collectionFactory,
      randomAccessListFactory,
      listFactory,
      setFactory,
      sortedsetFactory,
      mapFactory,
      sortedmapFactory
    };
  }

  /**
   * Registers serializers for unmodifiable Collections created via {@link Collections}, including
   * {@link Map}s.
   *
   * @see Collections#unmodifiableCollection(Collection)
   * @see Collections#unmodifiableList(List)
   * @see Collections#unmodifiableSet(Set)
   * @see Collections#unmodifiableSortedSet(SortedSet)
   * @see Collections#unmodifiableMap(Map)
   * @see Collections#unmodifiableSortedMap(SortedMap)
   */
  public static void registerSerializers(Fury fury) {
    try {
      for (Tuple2<Class<?>, Function> factory : unmodifiableFactories()) {
        fury.registerSerializer(factory.f0, createSerializer(fury, factory));
      }
    } catch (Throwable e) {
      ExceptionUtils.ignore(e);
    }
  }
}
