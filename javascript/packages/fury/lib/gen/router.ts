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

import { TypeDescription } from "../description";
import { SerializerGenerator } from "./serializer";
import { InternalSerializerType } from "../type";
import { CodecBuilder } from "./builder";
import { Scope } from "./scope";

type SerializerGeneratorConstructor = new (description: TypeDescription, builder: CodecBuilder, scope: Scope) => SerializerGenerator;

export class CodegenRegistry {
  static map = new Map<string, SerializerGeneratorConstructor>();
  static external = new Map<string, any>();

  static register(type: InternalSerializerType, generator: SerializerGeneratorConstructor) {
    this.map.set(InternalSerializerType[type], generator);
    this.external.set(generator.name, generator);
  }

  static registerExternal(object: { name: string }) {
    this.external.set(object.name, object);
  }

  static get(type: InternalSerializerType) {
    return this.map.get(InternalSerializerType[type]);
  }

  static getExternal() {
    return Object.fromEntries(Array.from(CodegenRegistry.external.entries()).map(([key, value]) => [key, value]));
  }
}
