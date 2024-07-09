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

import {
  RefFlags,
} from "./type";
import { BinaryReader } from "./reader";

export class ReferenceResolver {
  private readObjects: any[] = [];
  private writeObjects: any[] = [];

  constructor(private binaryReader: BinaryReader) {

  }

  reset() {
    this.readObjects = [];
    this.writeObjects = [];
  }

  getReadObject(refId: number) {
    return this.readObjects[refId];
  }

  readRefFlag() {
    return this.binaryReader.int8() as RefFlags;
  }

  reference(object: any) {
    this.readObjects.push(object);
  }

  writeRef(object: any) {
    this.writeObjects.push(object);
  }

  existsWriteObject(obj: any) {
    for (let index = 0; index < this.writeObjects.length; index++) {
      if (this.writeObjects[index] === obj) {
        return index;
      }
    }
  }
}
