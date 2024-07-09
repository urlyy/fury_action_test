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
import org.apache.fury.serializer.scala.ScalaDispatcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CollectionSerializerTest extends AnyWordSpec with Matchers {
  val params: Seq[(Boolean, Boolean)] = List((false, false), (false, true), (true, false), (true, true))
  params.foreach{case (setOpt, setFactory) => {
    val fury1: Fury = Fury.builder()
      .withLanguage(Language.JAVA)
      .withRefTracking(true)
      .withScalaOptimizationEnabled(setOpt)
      .requireClassRegistration(false).build()
    if (setFactory) {
      fury1.getClassResolver.setSerializerFactory(new ScalaDispatcher())
    }
    s"fury scala collection support: setOpt $setOpt, setFactory $setFactory" should {
      "serialize/deserialize Seq" in {
        val seq = Seq(100, 10000L)
        fury1.deserialize(fury1.serialize(seq)) shouldEqual seq
      }
      "serialize/deserialize List" in {
        val list = List(100, 10000L)
        fury1.deserialize(fury1.serialize(list)) shouldEqual list
      }
      "serialize/deserialize empty List" in {
        fury1.deserialize(fury1.serialize(List.empty)) shouldEqual List.empty
        fury1.deserialize(fury1.serialize(Nil)) shouldEqual Nil
      }
      "serialize/deserialize Set" in {
        val set = Set(100, 10000L)
        fury1.deserialize(fury1.serialize(set)) shouldEqual set
      }
      "serialize/deserialize CollectionStruct1" in {
        val struct = CollectionStruct1(List("a", "b"))
        fury1.deserialize(fury1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize CollectionStruct1 with empty List" in {
        val struct1 = CollectionStruct1(List.empty)
        fury1.deserialize(fury1.serialize(struct1)) shouldEqual struct1
        val struct2 = CollectionStruct1(Nil)
        fury1.deserialize(fury1.serialize(struct2)) shouldEqual struct2
      }
      "serialize/deserialize NestedCollectionStruct" in {
        val struct = NestedCollectionStruct(List(List("a", "b"), List("a", "b")), Set(Set("c", "d")))
        fury1.deserialize(fury1.serialize(struct)) shouldEqual struct
      }
    }
    s"fury scala map support: setOpt $setOpt, setFactory $setFactory" should {
      "serialize/deserialize Map" in {
        val map = Map("a" -> 100, "b" -> 10000L)
        fury1.deserialize(fury1.serialize(map)) shouldEqual map
      }
      "serialize/deserialize MapStruct1" in {
        val struct = MapStruct1(Map("k1" -> "v1", "k2" -> "v2"))
        fury1.deserialize(fury1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize MapStruct1 with empty map" in {
        val struct = MapStruct1(Map.empty)
        fury1.deserialize(fury1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize NestedMapStruct" in {
        val struct = NestedMapStruct(Map("K1" -> Map("k1" -> "v1", "k2" -> "v2"), "K2" -> Map("k1" -> "v1")))
        fury1.deserialize(fury1.serialize(struct)) shouldEqual struct
      }
    }
  }}
}

case class CollectionStruct1(list: List[String])

case class NestedCollectionStruct(list: List[List[String]], set: Set[Set[String]])

case class MapStruct1(map: Map[String, String])

case class NestedMapStruct(map: Map[String, Map[String, String]])
