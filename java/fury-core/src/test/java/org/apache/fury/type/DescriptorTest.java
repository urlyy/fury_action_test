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

package org.apache.fury.type;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;
import org.apache.fury.codegen.CodeGenerator;
import org.apache.fury.reflect.TypeRef;
import org.apache.fury.test.bean.BeanA;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DescriptorTest {

  static class A {
    int f1;
  }

  static class B extends A {
    long f2;
  }

  @Test
  public void testBuildBeanedDescriptorsMap() throws Exception {
    Assert.assertEquals(BeanA.class.getDeclaredField("f1"), BeanA.class.getDeclaredField("f1"));
    Assert.assertNotSame(BeanA.class.getDeclaredField("f1"), BeanA.class.getDeclaredField("f1"));
    SortedMap<Field, Descriptor> map = Descriptor.buildBeanedDescriptorsMap(BeanA.class, true);
    Assert.assertTrue(map.containsKey(BeanA.class.getDeclaredField("f1")));
    Assert.assertEquals(
        map.get(BeanA.class.getDeclaredField("doubleList")).getTypeRef(),
        new TypeRef<List<Double>>() {});
    Assert.assertNotNull(map.get(BeanA.class.getDeclaredField("longStringField")).getReadMethod());
    Assert.assertEquals(
        map.get(BeanA.class.getDeclaredField("longStringField")).getWriteMethod(),
        BeanA.class.getDeclaredMethod("setLongStringField", String.class));

    SortedMap<Field, Descriptor> map2 = Descriptor.buildBeanedDescriptorsMap(B.class, false);
    Assert.assertEquals(map2.size(), 1);
  }

  @Test
  public void getDescriptorsTest() throws IntrospectionException {
    Class<?> clz = BeanA.class;
    TypeRef<?> typeRef = TypeRef.of(clz);
    // sort to fix field order
    List<?> descriptors =
        Arrays.stream(Introspector.getBeanInfo(clz).getPropertyDescriptors())
            .filter(d -> !d.getName().equals("class"))
            .filter(d -> !d.getName().equals("declaringClass"))
            .filter(d -> d.getReadMethod() != null && d.getWriteMethod() != null)
            .map(
                p -> {
                  TypeRef<?> returnType = TypeRef.of(p.getReadMethod().getReturnType());
                  return Arrays.asList(
                      p.getName(),
                      returnType,
                      p.getReadMethod().getName(),
                      p.getWriteMethod().getName());
                })
            .collect(Collectors.toList());

    Descriptor.getDescriptors(clz);
  }

  @Test
  public void testWarmField() throws Exception {
    Assert.assertEquals(int.class.getName(), "int");
    Assert.assertEquals(Integer.class.getName(), "java.lang.Integer");
    Descriptor.warmField(
        BeanA.class, BeanA.class.getDeclaredField("beanB"), CodeGenerator.getCompilationService());
    Descriptor.getAllDescriptorsMap(BeanA.class);
    Descriptor.clearDescriptorCache();
    Descriptor.getAllDescriptorsMap(BeanA.class);
  }
}
