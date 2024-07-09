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

import { Type } from "../../packages/fury";

export const tupleType1 = Type.tuple( [
  Type.object('example.foo.1',{
    a: Type.object('example.foo.1.1',{
      b: Type.string()
    })
  }),
  Type.object('example.foo.2',{
    a: Type.object('example.foo.2.1',{
      c: Type.string()
    })
  })
]);

export const tupleType2 = Type.tuple( [
  Type.object('example.foo.1',{
    a: Type.object('example.foo.1.1',{
      b: Type.string()
    })
  }),
  Type.object('example.bar.1',{
    a: Type.object('example.bar.1.1',{
      b: Type.string()
    })
  }),
  Type.object('example.bar.2',{
    a: Type.object('example.bar.2.1',{
      c: Type.string()
    })
  })
]);

export const tupleType3 = Type.tuple([
  Type.string(),
  Type.bool(),
  Type.int32(),
  Type.tuple([
    Type.binary()
  ])
])

export const tupleObjectTag = 'tuple-object-wrapper';

export const tupleObjectDescription =  Type.object(tupleObjectTag, {
  tuple1: tupleType1,
  tuple1_: tupleType1,
  tuple2: tupleType2,
  tuple2_: tupleType2,
});

export const tupleObjectType3Tag = 'tuple-object-type3-tag';
export const tupleObjectType3Description = Type.object(tupleObjectType3Tag, {
  tuple: tupleType3
})
