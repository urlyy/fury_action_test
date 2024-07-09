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

package org.apache.fury.test;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import org.apache.fury.Fury;
import org.apache.fury.collection.Collections;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class FastJsonTest {
  public static class DemoResponse {
    private JSONObject json;
    private List<JSONObject> objects;

    public DemoResponse(JSONObject json) {
      this.json = json;
      objects = Collections.ofArrayList(json);
    }
  }

  @DataProvider
  public static Object[][] config() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // referenceTracking
            ImmutableSet.of(true, false), // compatible mode
            ImmutableSet.of(true, false), // scoped meta share mode
            ImmutableSet.of(true, false) // fury enable codegen
            )
        .stream()
        .map(List::toArray)
        .toArray(Object[][]::new);
  }

  @Test(dataProvider = "config")
  public void testSerializeJson(
      boolean trackingRef, boolean compatible, boolean scoped, boolean codegen) {
    // For issue: https://github.com/apache/fury/issues/1604
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("k1", "v1");
    jsonObject.put("k2", "v2");
    DemoResponse resp = new DemoResponse(jsonObject);
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .withRefTracking(trackingRef)
            .withCompatibleMode(
                compatible ? CompatibleMode.COMPATIBLE : CompatibleMode.SCHEMA_CONSISTENT)
            .withScopedMetaShare(scoped)
            .withCodegen(codegen)
            .registerGuavaTypes(false)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .build();
    byte[] serialized = fury.serialize(resp);
    DemoResponse o = (DemoResponse) fury.deserialize(serialized);
    Assert.assertEquals(o.json, jsonObject);
    Assert.assertEquals(o.objects, Collections.ofArrayList(jsonObject));
  }
}
