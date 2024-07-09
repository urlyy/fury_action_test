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

package org.apache.fury;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import org.apache.fury.config.Language;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.resolver.MetaContext;
import org.apache.fury.serializer.Serializer;
import org.apache.fury.test.bean.BeanA;
import org.apache.fury.test.bean.BeanB;
import org.apache.fury.test.bean.Struct;
import org.apache.fury.util.LoaderBinding.StagingType;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ThreadSafeFuryTest extends FuryTestBase {
  private volatile boolean hasException;

  @Test
  public void testPoolSerialize() {
    BeanA beanA = BeanA.createBeanA(2);
    ThreadSafeFury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withAsyncCompilation(true)
            .buildThreadSafeFuryPool(5, 10);
    for (int i = 0; i < 2000; i++) {
      new Thread(
              () -> {
                for (int j = 0; j < 10; j++) {
                  try {
                    fury.setClassLoader(beanA.getClass().getClassLoader());
                    assertEquals(fury.deserialize(fury.serialize(beanA)), beanA);
                  } catch (Exception e) {
                    hasException = true;
                    e.printStackTrace();
                  }
                }
              })
          .start();
    }
    assertFalse(hasException);
  }

  @Test
  public void testRegistration() throws Exception {
    BeanB bean = BeanB.createBeanB(2);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicReference<Throwable> ex = new AtomicReference<>();
    {
      ThreadSafeFury fury =
          Fury.builder().requireClassRegistration(true).buildThreadSafeFuryPool(2, 4);
      fury.register(BeanB.class);
      Assert.assertEquals(fury.deserialize(fury.serialize(bean)), bean);
      executor.execute(
          () -> {
            try {
              Assert.assertEquals(fury.deserialize(fury.serialize(bean)), bean);
            } catch (Throwable t) {
              ex.set(t);
            }
          });
      Assert.assertNull(ex.get());
    }
    {
      ThreadSafeFury fury = Fury.builder().requireClassRegistration(true).buildThreadLocalFury();
      fury.register(BeanB.class);
      Assert.assertEquals(fury.deserialize(fury.serialize(bean)), bean);
      executor.execute(
          () -> {
            try {
              Assert.assertEquals(fury.deserialize(fury.serialize(bean)), bean);
            } catch (Throwable t) {
              ex.set(t);
            }
          });
      Assert.assertNull(ex.get());
    }
  }

  @Test
  public void testSerialize() throws Exception {
    BeanA beanA = BeanA.createBeanA(2);
    ThreadSafeFury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withAsyncCompilation(true)
            .buildThreadSafeFury();
    ExecutorService executorService = Executors.newFixedThreadPool(12);
    for (int i = 0; i < 2000; i++) {
      executorService.execute(
          () -> {
            for (int j = 0; j < 10; j++) {
              try {
                fury.setClassLoader(beanA.getClass().getClassLoader());
                assertEquals(fury.deserialize(fury.serialize(beanA)), beanA);
              } catch (Exception e) {
                hasException = true;
                e.printStackTrace();
              }
            }
          });
    }
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
    assertFalse(hasException);
  }

  @Test
  public void testSerializeWithMetaShare() throws InterruptedException {
    ThreadSafeFury fury1 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .buildThreadSafeFury();
    ThreadSafeFury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withMetaShare(true)
            .requireClassRegistration(false)
            .buildThreadSafeFury();
    BeanA beanA = BeanA.createBeanA(2);
    ExecutorService executorService = Executors.newFixedThreadPool(12);
    ConcurrentHashMap<Thread, MetaContext> metaMap = new ConcurrentHashMap<>();
    for (int i = 0; i < 2000; i++) {
      executorService.execute(
          () -> {
            for (int j = 0; j < 10; j++) {
              try {
                {
                  fury1.setClassLoader(beanA.getClass().getClassLoader());
                  byte[] serialized = fury1.execute(f -> f.serialize(beanA));
                  Object newObj = fury1.execute(f -> f.deserialize(serialized));
                  assertEquals(newObj, beanA);
                }
                {
                  fury2.setClassLoader(beanA.getClass().getClassLoader());
                  byte[] serialized =
                      fury2.execute(
                          f -> {
                            f.getSerializationContext().setMetaContext(new MetaContext());
                            return f.serialize(beanA);
                          });
                  Object newObj =
                      fury2.execute(
                          f -> {
                            f.getSerializationContext().setMetaContext(new MetaContext());
                            return f.deserialize(serialized);
                          });
                  assertEquals(newObj, beanA);
                }
                {
                  MetaContext metaContext =
                      metaMap.computeIfAbsent(Thread.currentThread(), k -> new MetaContext());
                  fury2.setClassLoader(beanA.getClass().getClassLoader());
                  byte[] serialized =
                      fury2.execute(
                          f -> {
                            f.getSerializationContext().setMetaContext(metaContext);
                            return f.serialize(beanA);
                          });
                  Object newObj =
                      fury2.execute(
                          f -> {
                            f.getSerializationContext().setMetaContext(metaContext);
                            return f.deserialize(serialized);
                          });
                  assertEquals(newObj, beanA);
                }
              } catch (Exception e) {
                hasException = true;
                e.printStackTrace();
                throw e;
              }
            }
          });
    }
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
    assertFalse(hasException);
  }

  @DataProvider(name = "stagingConfig")
  public static Object[][] stagingConfig() {
    return new Object[][] {{StagingType.NO_STAGING}, {StagingType.STRONG_STAGING}};
  }

  @Test(dataProvider = "stagingConfig")
  public void testClassDuplicateName(StagingType staging) {
    ThreadSafeFury fury = Fury.builder().requireClassRegistration(false).buildThreadSafeFury();
    String className = "DuplicateStruct";

    Class<?> structClass1 = Struct.createStructClass(className, 1);
    Object struct1 = Struct.createPOJO(structClass1);
    byte[] bytes1 = fury.serialize(struct1);
    Assert.assertEquals(fury.deserialize(bytes1), struct1);
    Class<? extends Serializer> serializerClass1 =
        fury.execute(f -> f.getClassResolver().getSerializerClass(structClass1));
    Assert.assertTrue(serializerClass1.getName().contains("Codec"));

    Class<?> structClass2 = Struct.createStructClass(className, 2);
    Object struct2 = Struct.createPOJO(structClass2);
    assertEquals(
        struct2.getClass().getDeclaredFields().length,
        struct1.getClass().getDeclaredFields().length * 2);
    AtomicReference<byte[]> bytesReference = new AtomicReference<>();
    CompletableFuture.runAsync(
            () -> {
              fury.setClassLoader(structClass2.getClassLoader(), staging);
              byte[] bytes2 = fury.serialize(struct2);
              bytesReference.set(bytes2);
            })
        .join();
    byte[] bytes2 = bytesReference.get();
    fury.setClassLoader(structClass2.getClassLoader());
    Assert.assertEquals(fury.deserialize(bytes2), struct2);
    Class<? extends Serializer> serializerClass2 =
        fury.execute(f -> f.getClassResolver().getSerializerClass(structClass2));
    Assert.assertTrue(serializerClass2.getName().contains("Codec"));
    Assert.assertNotSame(serializerClass2, serializerClass1);

    byte[] newBytes1 = fury.serialize(struct1);
    CompletableFuture.runAsync(
            () -> {
              fury.setClassLoader(structClass1.getClassLoader(), staging);
              Assert.assertEquals(fury.deserialize(newBytes1), struct1);
            })
        .join();
  }

  @Test(timeOut = 60_000)
  public void testClassGC() throws Exception {
    // Can't inline `generateClassForGC` in current method, generated classes won't be gc.
    WeakHashMap<Class<?>, Boolean> map = generateClassForGC();
    TestUtils.triggerOOMForSoftGC(
        () -> {
          if (!map.isEmpty()) {
            System.out.printf("Wait classes %s gc.\n", map.keySet());
            return true;
          } else {
            return false;
          }
        });
  }

  private WeakHashMap<Class<?>, Boolean> generateClassForGC() {
    ThreadSafeFury fury1 = Fury.builder().requireClassRegistration(false).buildThreadSafeFury();
    ThreadSafeFury fury2 =
        Fury.builder().requireClassRegistration(false).buildThreadSafeFuryPool(1, 2);
    String className = "DuplicateStruct";
    WeakHashMap<Class<?>, Boolean> map = new WeakHashMap<>();
    {
      Class<?> structClass1 = Struct.createStructClass(className, 1, false);
      Object struct1 = Struct.createPOJO(structClass1);
      for (ThreadSafeFury fury : new ThreadSafeFury[] {fury1, fury2}) {
        fury.setClassLoader(structClass1.getClassLoader());
        byte[] bytes = fury.serialize(struct1);
        Assert.assertEquals(fury.deserialize(bytes), struct1);
        map.put(structClass1, true);
        System.out.printf(
            "structClass1 %s %s\n",
            structClass1.hashCode(), structClass1.getClassLoader().hashCode());
        fury.clearClassLoader(structClass1.getClassLoader());
      }
    }
    {
      Class<?> structClass2 = Struct.createStructClass(className, 2, false);
      map.put(structClass2, true);
      System.out.printf(
          "structClass2 %s %s\n ",
          structClass2.hashCode(), structClass2.getClassLoader().hashCode());
      for (ThreadSafeFury fury : new ThreadSafeFury[] {fury1, fury2}) {
        fury.setClassLoader(structClass2.getClassLoader());
        Object struct2 = Struct.createPOJO(structClass2);
        byte[] bytes2 = fury.serialize(struct2);
        Assert.assertEquals(fury.deserialize(bytes2), struct2);
        fury.clearClassLoader(structClass2.getClassLoader());
      }
    }
    return map;
  }

  @Test
  public void testSerializeJavaObject() {
    for (ThreadSafeFury fury :
        new ThreadSafeFury[] {
          Fury.builder().requireClassRegistration(false).buildThreadSafeFury(),
          Fury.builder().requireClassRegistration(false).buildThreadSafeFuryPool(2, 2)
        }) {
      byte[] bytes = fury.serializeJavaObject("abc");
      Assert.assertEquals(fury.deserializeJavaObject(bytes, String.class), "abc");
      MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(8);
      fury.serializeJavaObject(buffer, "abc");
      Assert.assertEquals(fury.deserializeJavaObject(buffer, String.class), "abc");
    }
  }

  @Data
  static class Foo {
    int f1;
  }

  public static class FooSerializer extends Serializer<Foo> {
    public FooSerializer(Fury fury, Class<Foo> type) {
      super(fury, type);
    }

    @Override
    public void write(MemoryBuffer buffer, Foo value) {
      buffer.writeInt32(value.f1);
    }

    @Override
    public Foo read(MemoryBuffer buffer) {
      final Foo foo = new Foo();
      foo.f1 = buffer.readInt32();
      return foo;
    }
  }

  public static class CustomClassLoader extends ClassLoader {
    public CustomClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  @Test
  public void testSerializerRegister() {
    final ThreadSafeFury threadSafeFury =
        Fury.builder().requireClassRegistration(false).buildThreadSafeFuryPool(0, 2);
    threadSafeFury.registerSerializer(Foo.class, FooSerializer.class);
    // create a new classLoader
    threadSafeFury.setClassLoader(new CustomClassLoader(ClassLoader.getSystemClassLoader()));
    threadSafeFury.execute(
        fury -> {
          Assert.assertEquals(
              fury.getClassResolver().getSerializer(Foo.class).getClass(), FooSerializer.class);
          return null;
        });
  }
}
