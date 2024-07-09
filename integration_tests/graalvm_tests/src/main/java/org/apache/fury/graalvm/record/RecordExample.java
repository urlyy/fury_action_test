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

package org.apache.fury.graalvm.record;

import java.util.List;
import java.util.Map;
import org.apache.fury.Fury;
import org.apache.fury.util.Preconditions;

public class RecordExample {
  public record Record(int f1, String f2, List<String> f3, Map<String, Long> f4) {}

  static Fury fury;

  static {
    fury = Fury.builder().requireClassRegistration(true).build();
    // register and generate serializer code.
    fury.register(Record.class, true);
  }

  static void test(Fury fury) {
    Record record = new Record(10, "abc", List.of("str1", "str2"), Map.of("k1", 10L, "k2", 20L));
    System.out.println(record);
    byte[] bytes = fury.serialize(record);
    Object o = fury.deserialize(bytes);
    System.out.println(o);
    Preconditions.checkArgument(record.equals(o));
  }

  public static void main(String[] args) {
    test(fury);
    System.out.println("RecordExample succeed");
  }
}
