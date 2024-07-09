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

package org.apache.fury.serializer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.fury.Fury;
import org.apache.fury.FuryTestBase;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.apache.fury.test.bean.Cyclic;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CodegenSerializerTest extends FuryTestBase {

  @Data
  public static class A {}

  @Data
  public static class B {
    private Object f1;
    public Object f2;
    public String f3;
  }

  @Test
  public void testSimpleBean() {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).requireClassRegistration(false).build();
    // serDe(fury, new A());
    B b = new B();
    b.f1 = "str1";
    b.f2 = 1;
    b.f3 = "str3";
    serDe(fury, b);
  }

  @Test
  public void testSupport() {
    assertTrue(CodegenSerializer.supportCodegenForJavaSerialization(Cyclic.class));
  }

  @Test
  public void testSerializeCircularReference() {
    Cyclic cyclic = Cyclic.create(true);
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);

    fury.serialize(buffer, cyclic);
    fury.deserialize(buffer);

    Serializer<Cyclic> beanSerializer = fury.getClassResolver().getSerializer(Cyclic.class);
    fury.getRefResolver().writeRefOrNull(buffer, cyclic);
    beanSerializer.write(buffer, cyclic);
    fury.getRefResolver().readRefOrNull(buffer);
    fury.getRefResolver().preserveRefId();
    Cyclic cyclic1 = beanSerializer.read(buffer);
    fury.reset();
    assertEquals(cyclic1, cyclic);
  }

  private static final class Circular1 {
    public int f1;
    public Circular1 circular1;
    public Circular2 circular2;
  }

  private static final class Circular2 {
    public int f1;
    public Circular1 circular1;
    public Circular2 circular2;
  }

  @Test
  public void testComplexCircular() {
    Circular1 circular1 = new Circular1();
    Circular2 circular2 = new Circular2();
    circular1.circular1 = circular1;
    circular1.circular2 = circular2;
    circular2.circular1 = circular1;
    circular2.circular2 = circular2;
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    serDe(fury, circular1);
    serDe(fury, circular1);
    serDe(fury, circular2);
    serDe(fury, circular2);
  }

  @Data
  public static class NonFinalPublic {}

  @ToString
  @EqualsAndHashCode(callSuper = true)
  static class NonFinalPackage extends NonFinalPublic {}

  @ToString
  @EqualsAndHashCode(callSuper = true)
  private static class NonFinalPrivate extends NonFinalPublic {}

  public static class TestCacheNonFinalClassInfo {
    public String str;
    public NonFinalPublic nonFinalPublic;
    public List<String> finalList;
    public List<NonFinalPublic> nonFinalPublicList;
    public List<NonFinalPackage> packageList;
    public List<NonFinalPrivate> nonFinalPrivateList;
  }

  @Test
  public void testCacheNonFinalClassInfo() {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).requireClassRegistration(false).build();
    TestCacheNonFinalClassInfo obj = new TestCacheNonFinalClassInfo();
    obj.finalList = new ArrayList<>(ImmutableList.of("a", "b"));
    obj.nonFinalPublicList =
        new ArrayList<>(
            ImmutableList.of(new NonFinalPublic(), new NonFinalPublic(), new NonFinalPrivate()));
    obj.packageList = new ArrayList<>(ImmutableList.of(new NonFinalPackage()));
    obj.nonFinalPrivateList = new ArrayList<>(ImmutableList.of(new NonFinalPrivate()));
    serDe(fury, obj);
  }

  @Data
  private static class CompressTestClass {
    int f1;
    int f2;
    long f3;
    long f4;
    float f5;
    double f6;
    Integer f7;
    Long f8;
  }

  @Test
  public void testCompressInt() {
    CompressTestClass pojo = new CompressTestClass();
    pojo.f1 = 1;
    pojo.f2 = 2;
    pojo.f3 = 2;
    pojo.f4 = 2;
    pojo.f5 = 2;
    pojo.f6 = 2;
    pojo.f7 = 2;
    pojo.f8 = 2L;
    int length =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withNumberCompressed(false)
            .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
            .requireClassRegistration(false)
            .build()
            .serialize(pojo)
            .length;
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withNumberCompressed(true)
            .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
            .requireClassRegistration(false)
            .build();
    serDeCheck(fury, pojo);
    byte[] bytes = fury.serialize(pojo);
    {
      Fury fury1 =
          Fury.builder()
              .withLanguage(Language.JAVA)
              .withIntCompressed(true)
              .withLongCompressed(false)
              .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
              .requireClassRegistration(false)
              .build();
      serDeCheck(fury1, pojo);
      Assert.assertNotSame(
          fury1.getClassResolver().getSerializerClass(CompressTestClass.class),
          fury.getClassResolver().getSerializerClass(CompressTestClass.class));
      Assert.assertTrue(fury1.serialize(pojo).length > bytes.length);
      Assert.assertTrue(fury1.serialize(pojo).length < length);
    }
    {
      Fury fury1 =
          Fury.builder()
              .withLanguage(Language.JAVA)
              .withIntCompressed(false)
              .withLongCompressed(true)
              .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
              .requireClassRegistration(false)
              .build();
      serDeCheck(fury1, pojo);
      Assert.assertTrue(fury1.serialize(pojo).length > bytes.length);
      Assert.assertTrue(fury1.serialize(pojo).length < length);
    }
  }

  @Data
  public static final class Column {
    byte[] family;
    byte[] column;
    Object value;

    public Column(byte[] family, byte[] column, Object value) {
      this.family = family;
      this.column = column;
      this.value = value;
    }
  }

  @Data
  public static class Get {
    List<Column> pkColumns;

    public Get(List<Column> pkColumns) {
      this.pkColumns = pkColumns;
    }
  }

  @Test
  public void testFinalTypeField() {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(false)
            .requireClassRegistration(true)
            .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
            .build();
    fury.register(Column.class);
    fury.register(Get.class);
    Get get = new Get(Lists.newArrayList(new Column(new byte[] {1}, new byte[] {2}, "abc")));
    serDeCheck(fury, get);
  }

  private interface PrivateInterface extends Serializable {
    Object t();
  }

  public static class TestClass1 {
    PrivateInterface a = () -> 1;
    private PrivateInterface b = () -> 2;
  }

  @Test
  public void testPrivateInterfaceField() {
    TestClass1 o = serDe(getJavaFury(), new TestClass1());
    Assert.assertEquals(o.a.t(), 1);
    Assert.assertEquals(o.b.t(), 2);
  }

  private static final Object subclassObject = new B() {};

  @Test
  public void testAnonymousClass() {
    Fury fury = Fury.builder().requireClassRegistration(false).build();
    // anonymous class never static
    serDeCheckSerializer(fury, subclassObject, "Serializer");
  }

  @Test
  public void testLocalClass() {
    class A {
      int f1;
    }
    A a = new A();
    a.f1 = 10;
    Fury fury = Fury.builder().requireClassRegistration(false).build();
    A o = serDe(fury, a);
    assertEquals(o.f1, a.f1);
    // local class never static
    assertSame(fury.getClassResolver().getSerializer(A.class).getClass(), ObjectSerializer.class);
    // TODO how to create a class with `Class#getCanonicalName` returns null and static still.
    // for scala 3:
    // `enum ColorEnum { case Red, Green, Blue }`
    // `case class Colors(set: Set[ColorEnum])`
    // ColorEnum.Green.getClass is a static local class.
    // see https://github.com/apache/fury/issues/1033
  }
}
