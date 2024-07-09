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

package org.apache.fury.serializer

import org.apache.fury.Fury
import org.apache.fury.config.Language
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

case class Struct(tuple2: (String, String), tuple4: (String, String, String, String))

class TupleTest extends AnyWordSpec with Matchers {
  val fury: Fury = Fury.builder()
    .withLanguage(Language.JAVA)
    .withRefTracking(true)
    .withScalaOptimizationEnabled(true)
    .requireClassRegistration(false).build()

  "fury scala tuple support" should {
    "serialize/deserialize tuple1" in {
      val tuple = Tuple1("str")
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple2" in {
      val tuple = Tuple2(100, 10000L)
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple3" in {
      val tuple = Tuple3(100, 10000L, 10000L)
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple4" in {
      val tuple = Tuple4(100, 10000L, 10000L, "str")
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple5" in {
      val tuple = Tuple5(100, 10000L, 10000L, "str", "str")
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple6" in {
      val tuple = Tuple6(100, 10000L, 10000L, "str", "str", "a")
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple20" in {
      val tuple = Tuple20(100, 10000L, 10000L, "str", "str", "a", "str", "str", "a",
        "str", "str", "a", "str", "str", "a", "str", "str", "a", "str", "str")
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple21" in {
      val tuple = Tuple21(100, 10000L, 10000L, "str", "str", "a", "str", "str", "a",
        "str", "str", "a", "str", "str", "a", "str", "str", "a", "str", "str", 10)
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
    "serialize/deserialize tuple22" in {
      val tuple = Tuple22(100, 10000L, 10000L, "str", "str", "a", "str", "str", "a",
        "str", "str", "a", "str", "str", "a", "str", "str", "a", "str", "str", 10, 20)
      fury.deserialize(fury.serialize(tuple)) shouldEqual tuple
    }
  }
  "fury scala tuple struct" should {
    "serialize/deserialize Struct" in {
      val struct = Struct(("a", "b"), ("a", "b", "c", "d"))
      fury.deserialize(fury.serialize(struct)) shouldEqual struct
    }
  }
}
