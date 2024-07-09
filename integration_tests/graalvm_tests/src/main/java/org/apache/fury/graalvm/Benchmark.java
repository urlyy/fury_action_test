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

package org.apache.fury.graalvm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fury.Fury;
import org.apache.fury.io.ClassLoaderObjectInputStream;
import org.apache.fury.util.Preconditions;

/** Benchmark suite for fury and jdk. */
public class Benchmark {
  static ObjectInputFilter filter =
      ObjectInputFilter.Config.createFilter("org.apache.fury.graalvm.*");

  public static Object testJDKSerialization(Object o) {
    byte[] bytes = jdkSerialize(o);
    return jdkDeserialize(bytes);
  }

  public static byte[] jdkSerialize(Object data) {
    ByteArrayOutputStream bas = new ByteArrayOutputStream();
    jdkSerialize(bas, data);
    return bas.toByteArray();
  }

  public static void jdkSerialize(ByteArrayOutputStream bas, Object data) {
    bas.reset();
    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bas)) {
      objectOutputStream.writeObject(data);
      objectOutputStream.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object jdkDeserialize(byte[] data) {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream objectInputStream =
            new ClassLoaderObjectInputStream(Thread.currentThread().getContextClassLoader(), bis)) {
      return objectInputStream.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final Fury fury1;
  private static final Fury fury2;

  static {
    fury1 = Fury.builder().withNumberCompressed(false).build();
    fury1.register(Foo.class, true);
    fury1.register(Struct.class, true);
    fury2 = Fury.builder().withNumberCompressed(true).build();
    fury2.register(Foo.class, true);
    fury2.register(Struct.class, true);
  }

  public static void main(String[] args) {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      list.add("string" + i);
    }
    Map<String, Long> map = new HashMap<>();
    for (int i = 0; i < 20; i++) {
      map.put("key" + i, (long) i);
    }
    benchmark(true, Struct.create());
    benchmark(false, Struct.create());
    benchmark(true, new Foo(100, "abc", list, map));
    benchmark(false, new Foo(100, "abc", list, map));
  }

  public static void benchmark(boolean compressNumber, Object obj) {
    String furyRepeat = System.getenv("BENCHMARK_REPEAT");
    if (furyRepeat == null) {
      return;
    }
    int n = Integer.parseInt(furyRepeat);
    Fury fury = compressNumber ? fury2 : fury1;
    System.out.println("=========================");
    System.out.println("Benchmark repeat number: " + furyRepeat);
    System.out.println("Object type: " + obj.getClass());
    System.out.println("Compress number: " + compressNumber);
    double furySize = fury.serialize(obj).length;
    System.out.println("Fury size: " + furySize);
    double jdkSize = jdkSerialize(obj).length;
    System.out.println("JDK size: " + jdkSize);
    Object o = fury.deserialize(fury.serialize(obj));
    for (int i = 0; i < n; i++) {
      o = fury.deserialize(fury.serialize(obj));
      testJDKSerialization(o);
    }
    long start = System.nanoTime();
    for (int i = 0; i < n; i++) {
      o = fury.deserialize(fury.serialize(o));
    }
    long duration = (System.nanoTime() - start);
    System.out.println("Fury serialization took mills: " + duration / 1000_000);
    Preconditions.checkArgument(o.equals(obj));

    start = System.nanoTime();
    for (int i = 0; i < n; i++) {
      o = testJDKSerialization(o);
    }
    long duration2 = (System.nanoTime() - start);
    System.out.println("JDK serialization took mills: " + duration2 / 1000_000);
    Preconditions.checkArgument(o.equals(obj));
    System.out.printf("Compare speed: Fury is %.2fx speed of JDK\n", duration2 * 1.0d / duration);
    System.out.printf("Compare size: Fury is %.2fx size of JDK\n", furySize / jdkSize);
  }
}
