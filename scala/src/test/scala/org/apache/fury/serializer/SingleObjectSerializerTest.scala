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

object singleton {}

case class Pair(f1: Any, f2: Any)

class SingleObjectSerializerTest extends AnyWordSpec with Matchers {
  "fury scala object support" should {
    "serialize/deserialize" in {
      val fury = Fury.builder()
        .withLanguage(Language.JAVA)
        .withRefTracking(true)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(false).build()
      fury.deserialize(fury.serialize(singleton)) shouldBe singleton
      fury.deserialize(fury.serialize(Pair(singleton, singleton))) shouldEqual Pair(singleton, singleton)
    }
  }
}
