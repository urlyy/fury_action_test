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

import static org.apache.fury.serializer.ClassUtils.loadClass;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fury.Fury;
import org.apache.fury.FuryTestBase;
import org.apache.fury.builder.MetaSharedCodecBuilder;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.apache.fury.reflect.ReflectionUtils;
import org.apache.fury.resolver.MetaContext;
import org.apache.fury.serializer.collection.UnmodifiableSerializersTest;
import org.apache.fury.test.bean.BeanA;
import org.apache.fury.test.bean.BeanB;
import org.apache.fury.test.bean.CollectionFields;
import org.apache.fury.test.bean.Foo;
import org.apache.fury.test.bean.MapFields;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for {@link MetaSharedCodecBuilder} and {@link MetaSharedSerializer}, and protocol
 * interoperability between them.
 */
public class MetaSharedCompatibleTest extends FuryTestBase {

  public static Object serDeCheck(Fury fury, Object obj) {
    Object newObj = serDeMetaShared(fury, obj);
    Assert.assertEquals(newObj, obj);
    return newObj;
  }

  @DataProvider
  public static Object[][] config1() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // referenceTracking
            ImmutableSet.of(true, false), // compress number
            ImmutableSet.of(true, false)) // enable codegen
        .stream()
        .map(List::toArray)
        .toArray(Object[][]::new);
  }

  @DataProvider
  public static Object[][] config2() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // referenceTracking
            ImmutableSet.of(true, false), // compress number
            ImmutableSet.of(true, false), // fury1 enable codegen
            ImmutableSet.of(true, false) // fury2 enable codegen
            )
        .stream()
        .map(List::toArray)
        .toArray(Object[][]::new);
  }

  @DataProvider
  public static Object[][] config3() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // referenceTracking
            ImmutableSet.of(true, false), // compress number
            ImmutableSet.of(true, false), // fury1 enable codegen
            ImmutableSet.of(true, false), // fury2 enable codegen
            ImmutableSet.of(true, false) // fury3 enable codegen
            )
        .stream()
        .map(List::toArray)
        .toArray(Object[][]::new);
  }

  @Test(dataProvider = "config1")
  public void testWrite(boolean referenceTracking, boolean compressNumber, boolean enableCodegen) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withNumberCompressed(compressNumber)
            .withRefTracking(referenceTracking)
            .withCodegen(enableCodegen)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    serDeCheck(fury, Foo.create());
    serDeCheck(fury, BeanB.createBeanB(2));
    serDeCheck(fury, BeanA.createBeanA(2));
  }

  @Test(dataProvider = "config2")
  public void testWriteCompatibleBasic(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2)
      throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    Object foo = Foo.create();
    for (Class<?> fooClass :
        new Class<?>[] {
          Foo.createCompatibleClass1(), Foo.createCompatibleClass2(), Foo.createCompatibleClass3(),
        }) {
      Object newFoo = fooClass.newInstance();
      ReflectionUtils.unsafeCopy(foo, newFoo);
      MetaContext context = new MetaContext();
      Fury newFury =
          Fury.builder()
              .withLanguage(Language.JAVA)
              .withRefTracking(referenceTracking)
              .withNumberCompressed(compressNumber)
              .withCodegen(enableCodegen2)
              .withMetaShare(true)
              .withCompatibleMode(CompatibleMode.COMPATIBLE)
              .requireClassRegistration(false)
              .withClassLoader(fooClass.getClassLoader())
              .build();
      MetaContext context1 = new MetaContext();
      {
        newFury.getSerializationContext().setMetaContext(context1);
        byte[] foo1Bytes = newFury.serialize(newFoo);
        fury.getSerializationContext().setMetaContext(context);
        Object deserialized = fury.deserialize(foo1Bytes);
        Assert.assertEquals(deserialized.getClass(), Foo.class);
        Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(deserialized, newFoo));
        fury.getSerializationContext().setMetaContext(context);
        byte[] fooBytes = fury.serialize(deserialized);
        newFury.getSerializationContext().setMetaContext(context1);
        Assert.assertTrue(
            ReflectionUtils.objectFieldsEquals(newFury.deserialize(fooBytes), newFoo));
      }
      {
        fury.getSerializationContext().setMetaContext(context);
        byte[] bytes1 = fury.serialize(foo);
        newFury.getSerializationContext().setMetaContext(context1);
        Object o1 = newFury.deserialize(bytes1);
        Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(o1, foo));
        fury.getSerializationContext().setMetaContext(context);
        newFury.getSerializationContext().setMetaContext(context1);
        Object o2 = fury.deserialize(newFury.serialize(o1));
        List<String> fields =
            Arrays.stream(fooClass.getDeclaredFields())
                .map(f -> f.getDeclaringClass().getSimpleName() + f.getType() + f.getName())
                .collect(Collectors.toList());
        Assert.assertTrue(ReflectionUtils.objectFieldsEquals(new HashSet<>(fields), o2, foo));
      }
      {
        fury.getSerializationContext().setMetaContext(context);
        newFury.getSerializationContext().setMetaContext(context1);
        Object o3 = fury.deserialize(newFury.serialize(foo));
        Assert.assertTrue(ReflectionUtils.objectFieldsEquals(o3, foo));
      }
    }
  }

  @Test
  public void testWriteCompatibleCollectionSimple() throws Exception {
    BeanA beanA = BeanA.createBeanA(2);
    String pkg = BeanA.class.getPackage().getName();
    String code =
        ""
            + "package "
            + pkg
            + ";\n"
            + "import java.util.*;\n"
            + "import java.math.*;\n"
            + "public class BeanA {\n"
            + "  private List<Double> doubleList;\n"
            + "  private Iterable<BeanB> beanBIterable;\n"
            + "  private List<BeanB> beanBList;\n"
            + "}";
    Class<?> cls1 =
        loadClass(
            BeanA.class,
            code,
            MetaSharedCompatibleTest.class + "testWriteCompatibleCollectionBasic_1");
    Fury fury1 =
        Fury.builder()
            .withCodegen(false)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls1.getClassLoader())
            .build();
    code =
        ""
            + "package "
            + pkg
            + ";\n"
            + "import java.util.*;\n"
            + "import java.math.*;\n"
            + "public class BeanA {\n"
            + "  private List<Double> doubleList;\n"
            + "  private Iterable<BeanB> beanBIterable;\n"
            + "}";
    Class<?> cls2 =
        loadClass(
            BeanA.class,
            code,
            MetaSharedCompatibleTest.class + "testWriteCompatibleCollectionBasic_2");
    Object o2 = cls2.newInstance();
    ReflectionUtils.unsafeCopy(beanA, o2);
    Fury fury2 =
        Fury.builder()
            .withCodegen(false)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls2.getClassLoader())
            .build();

    MetaContext context1 = new MetaContext();
    MetaContext context2 = new MetaContext();
    fury1.getSerializationContext().setMetaContext(context1);
    byte[] objBytes = fury1.serialize(beanA);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj2 = fury2.deserialize(objBytes);
    Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(obj2, o2));
  }

  @Test(dataProvider = "config3")
  public void testWriteCompatibleCollectionBasic(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2,
      boolean enableCodegen3)
      throws Exception {
    BeanA beanA = BeanA.createBeanA(2);
    String pkg = BeanA.class.getPackage().getName();
    String code =
        ""
            + "package "
            + pkg
            + ";\n"
            + "import java.util.*;\n"
            + "import java.math.*;\n"
            + "public class BeanA {\n"
            + "  private List<Double> doubleList;\n"
            + "  private Iterable<BeanB> beanBIterable;\n"
            + "  private List<BeanB> beanBList;\n"
            + "}";
    Class<?> cls1 =
        loadClass(
            BeanA.class,
            code,
            MetaSharedCompatibleTest.class + "testWriteCompatibleCollectionBasic_1");
    Fury fury1 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen2)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls1.getClassLoader())
            .build();
    code =
        ""
            + "package "
            + pkg
            + ";\n"
            + "import java.util.*;\n"
            + "import java.math.*;\n"
            + "public class BeanA {\n"
            + "  private List<Double> doubleList;\n"
            + "  private Iterable<BeanB> beanBIterable;\n"
            + "}";
    Class<?> cls2 =
        loadClass(
            BeanA.class,
            code,
            MetaSharedCompatibleTest.class + "testWriteCompatibleCollectionBasic_2");
    Object o2 = cls2.newInstance();
    ReflectionUtils.unsafeCopy(beanA, o2);
    Fury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen3)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls2.getClassLoader())
            .build();

    MetaContext context1 = new MetaContext();
    MetaContext context2 = new MetaContext();
    fury2.getSerializationContext().setMetaContext(context2);
    byte[] bytes2 = fury2.serialize(o2);
    fury1.getSerializationContext().setMetaContext(context1);
    Object deserialized = fury1.deserialize(bytes2);
    Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(deserialized, o2));
    Assert.assertEquals(deserialized.getClass(), cls1);
    fury1.getSerializationContext().setMetaContext(context1);
    byte[] beanABytes = fury1.serialize(deserialized);
    fury2.getSerializationContext().setMetaContext(context2);
    Assert.assertTrue(ReflectionUtils.objectFieldsEquals(fury2.deserialize(beanABytes), o2));

    fury1.getSerializationContext().setMetaContext(context1);
    byte[] objBytes = fury1.serialize(beanA);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj2 = fury2.deserialize(objBytes);
    Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(obj2, o2));

    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    // fury <-> fury2 is a new channel, which needs a new context.
    MetaContext context = new MetaContext();
    MetaContext ctx2 = new MetaContext();
    fury.getSerializationContext().setMetaContext(context);
    fury2.getSerializationContext().setMetaContext(ctx2);
    Assert.assertEquals(fury.deserialize(fury2.serialize(beanA)), beanA);
  }

  @Test(dataProvider = "config2")
  public void testWriteCompatibleContainer(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2)
      throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    MetaContext context = new MetaContext();
    BeanA beanA = BeanA.createBeanA(2);
    serDeMetaShared(fury, beanA);
    Class<?> cls = ClassUtils.createCompatibleClass1();
    Object newBeanA = cls.newInstance();
    ReflectionUtils.unsafeCopy(beanA, newBeanA);
    Fury newFury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen2)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls.getClassLoader())
            .build();
    MetaContext context1 = new MetaContext();
    newFury.getSerializationContext().setMetaContext(context1);
    byte[] newBeanABytes = newFury.serialize(newBeanA);
    fury.getSerializationContext().setMetaContext(context);
    Object deserialized = fury.deserialize(newBeanABytes);
    Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(deserialized, newBeanA));
    Assert.assertEquals(deserialized.getClass(), BeanA.class);
    fury.getSerializationContext().setMetaContext(context);
    byte[] beanABytes = fury.serialize(deserialized);
    newFury.getSerializationContext().setMetaContext(context1);
    Assert.assertTrue(
        ReflectionUtils.objectFieldsEquals(newFury.deserialize(beanABytes), newBeanA));

    fury.getSerializationContext().setMetaContext(context);
    byte[] objBytes = fury.serialize(beanA);
    newFury.getSerializationContext().setMetaContext(context1);
    Object obj2 = newFury.deserialize(objBytes);
    Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(obj2, newBeanA));

    newFury.getSerializationContext().setMetaContext(context1);
    fury.getSerializationContext().setMetaContext(context);
    Assert.assertEquals(fury.deserialize(newFury.serialize(beanA)), beanA);
  }

  @Test(dataProvider = "config2")
  public void testWriteCompatibleCollection(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2)
      throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    CollectionFields collectionFields = UnmodifiableSerializersTest.createCollectionFields();
    {
      Object o = serDeMetaShared(fury, collectionFields);
      Object o1 = CollectionFields.copyToCanEqual(o, o.getClass().newInstance());
      Object o2 =
          CollectionFields.copyToCanEqual(
              collectionFields, collectionFields.getClass().newInstance());
      Assert.assertEquals(o1, o2);
    }
    Class<?> cls2 = ClassUtils.createCompatibleClass2();
    Object newObj = cls2.newInstance();
    ReflectionUtils.unsafeCopy(collectionFields, newObj);
    Fury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen2)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls2.getClassLoader())
            .build();
    MetaContext context2 = new MetaContext();
    fury2.getSerializationContext().setMetaContext(context2);
    byte[] bytes1 = fury2.serialize(newObj);
    MetaContext context = new MetaContext();
    fury.getSerializationContext().setMetaContext(context);
    Object deserialized = fury.deserialize(bytes1);
    Assert.assertTrue(
        ReflectionUtils.objectCommonFieldsEquals(
            CollectionFields.copyToCanEqual(deserialized, deserialized.getClass().newInstance()),
            CollectionFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));
    Assert.assertEquals(deserialized.getClass(), CollectionFields.class);

    fury.getSerializationContext().setMetaContext(context);
    byte[] bytes2 = fury.serialize(deserialized);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj2 = fury2.deserialize(bytes2);
    Assert.assertTrue(
        ReflectionUtils.objectFieldsEquals(
            CollectionFields.copyToCanEqual(obj2, obj2.getClass().newInstance()),
            CollectionFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));

    fury.getSerializationContext().setMetaContext(context);
    byte[] objBytes = fury.serialize(collectionFields);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj3 = fury2.deserialize(objBytes);
    Assert.assertTrue(
        ReflectionUtils.objectCommonFieldsEquals(
            CollectionFields.copyToCanEqual(obj3, obj3.getClass().newInstance()),
            CollectionFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));

    fury.getSerializationContext().setMetaContext(context);
    fury2.getSerializationContext().setMetaContext(context2);
    Assert.assertEquals(
        ((CollectionFields) (fury.deserialize(fury2.serialize(collectionFields)))).toCanEqual(),
        collectionFields.toCanEqual());
  }

  @Test(dataProvider = "config2")
  public void testWriteCompatibleMap(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2)
      throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    MetaContext context = new MetaContext();
    MapFields mapFields = UnmodifiableSerializersTest.createMapFields();
    {
      Object o = serDeMetaShared(fury, mapFields);
      Object o1 = MapFields.copyToCanEqual(o, o.getClass().newInstance());
      Object o2 = MapFields.copyToCanEqual(mapFields, mapFields.getClass().newInstance());
      Assert.assertEquals(o1, o2);
    }
    Class<?> cls = ClassUtils.createCompatibleClass3();
    Object newObj = cls.newInstance();
    ReflectionUtils.unsafeCopy(mapFields, newObj);
    Fury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen2)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .build();
    MetaContext context2 = new MetaContext();
    fury2.getSerializationContext().setMetaContext(context2);
    byte[] bytes1 = fury2.serialize(newObj);
    fury.getSerializationContext().setMetaContext(context);
    Object deserialized = fury.deserialize(bytes1);
    Assert.assertTrue(
        ReflectionUtils.objectCommonFieldsEquals(
            MapFields.copyToCanEqual(deserialized, deserialized.getClass().newInstance()),
            MapFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));
    Assert.assertEquals(deserialized.getClass(), MapFields.class);

    fury.getSerializationContext().setMetaContext(context);
    byte[] bytes2 = fury.serialize(deserialized);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj2 = fury2.deserialize(bytes2);
    Assert.assertTrue(
        ReflectionUtils.objectCommonFieldsEquals(
            MapFields.copyToCanEqual(obj2, obj2.getClass().newInstance()),
            MapFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));

    fury.getSerializationContext().setMetaContext(context);
    byte[] objBytes = fury.serialize(mapFields);
    fury2.getSerializationContext().setMetaContext(context2);
    Object obj3 = fury2.deserialize(objBytes);
    Assert.assertTrue(
        ReflectionUtils.objectCommonFieldsEquals(
            MapFields.copyToCanEqual(obj3, obj3.getClass().newInstance()),
            MapFields.copyToCanEqual(newObj, newObj.getClass().newInstance())));

    fury2.getSerializationContext().setMetaContext(context2);
    fury.getSerializationContext().setMetaContext(context);
    Assert.assertEquals(
        ((MapFields) (fury.deserialize(fury2.serialize(mapFields)))).toCanEqual(),
        mapFields.toCanEqual());
  }

  public static class DuplicateFieldsClass1 {
    int intField1;
    int intField2;
  }

  @Test(dataProvider = "config2")
  public void testDuplicateFields(
      boolean referenceTracking,
      boolean compressNumber,
      boolean enableCodegen1,
      boolean enableCodegen2)
      throws Exception {
    String pkg = DuplicateFieldsClass1.class.getPackage().getName();
    Class<?> cls1 =
        loadClass(
            pkg,
            "DuplicateFieldsClass2",
            ""
                + "package "
                + pkg
                + ";\n"
                + "import java.util.*;\n"
                + "import java.math.*;\n"
                + "public class DuplicateFieldsClass2 extends MetaSharedCompatibleTest.DuplicateFieldsClass1 {\n"
                + "  int intField1;\n"
                + "}");
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen1)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls1.getClassLoader())
            .build();
    MetaContext context = new MetaContext();
    Object o1 = cls1.newInstance();
    for (Field field : ReflectionUtils.getFields(cls1, true)) {
      field.setAccessible(true);
      if (field.getDeclaringClass() == DuplicateFieldsClass1.class) {
        field.setInt(o1, 10);
      } else {
        field.setInt(o1, 100);
      }
    }
    {
      Object o = serDeMetaShared(fury, o1);
      Assert.assertTrue(ReflectionUtils.objectFieldsEquals(o, o1));
    }

    Class<?> cls2 =
        loadClass(
            pkg,
            "DuplicateFieldsClass2",
            ""
                + "package "
                + pkg
                + ";\n"
                + "import java.util.*;\n"
                + "import java.math.*;\n"
                + "public class DuplicateFieldsClass2 extends MetaSharedCompatibleTest.DuplicateFieldsClass1 {\n"
                + "  int intField1;\n"
                + "  int intField2;\n"
                + "}");
    Fury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen2)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls2.getClassLoader())
            .build();
    MetaContext context2 = new MetaContext();
    Object o2 = cls2.newInstance();
    for (Field field : ReflectionUtils.getFields(cls2, true)) {
      field.setAccessible(true);
      if (field.getDeclaringClass() == DuplicateFieldsClass1.class) {
        field.setInt(o2, 10);
      } else {
        field.setInt(o2, 100);
      }
    }
    {
      Object o = serDeMetaShared(fury2, o2);
      Assert.assertTrue(ReflectionUtils.objectFieldsEquals(o, o2));
    }
    {
      fury2.getSerializationContext().setMetaContext(context2);
      fury.getSerializationContext().setMetaContext(context);
      byte[] bytes2 = fury2.serialize(o2);
      Object o = fury.deserialize(bytes2);
      Assert.assertTrue(ReflectionUtils.objectCommonFieldsEquals(o, o2));
      fury2.getSerializationContext().setMetaContext(context2);
      fury.getSerializationContext().setMetaContext(context);
      Object newObj2 = fury2.deserialize(fury.serialize(o));
      // `DuplicateFieldsClass2.intField2` of `newObj2` will be 0.
      Assert.assertFalse(ReflectionUtils.objectCommonFieldsEquals(newObj2, o2));
      for (Field field : ReflectionUtils.getFields(DuplicateFieldsClass1.class, true)) {
        field.setAccessible(true);
        Assert.assertEquals(field.get(newObj2), field.get(o2));
      }
    }
  }

  @Test(dataProvider = "config1")
  void testEmptySubClass(boolean referenceTracking, boolean compressNumber, boolean enableCodegen)
      throws Exception {
    String pkg = DuplicateFieldsClass1.class.getPackage().getName();
    Class<?> cls1 =
        loadClass(
            pkg,
            "DuplicateFieldsClass2",
            ""
                + "package "
                + pkg
                + ";\n"
                + "import java.util.*;\n"
                + "import java.math.*;\n"
                + "public class DuplicateFieldsClass2 extends MetaSharedCompatibleTest.DuplicateFieldsClass1 {\n"
                + "}");
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .withNumberCompressed(compressNumber)
            .withCodegen(enableCodegen)
            .withMetaShare(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withClassLoader(cls1.getClassLoader())
            .build();
    Object o1 = cls1.newInstance();
    for (Field field : ReflectionUtils.getFields(cls1, true)) {
      field.setAccessible(true);
      field.setInt(o1, 10);
    }
    Object o = serDeMetaShared(fury, o1);
    Assert.assertEquals(o.getClass(), o1.getClass());
    Assert.assertTrue(ReflectionUtils.objectFieldsEquals(o, o1));
  }
}
