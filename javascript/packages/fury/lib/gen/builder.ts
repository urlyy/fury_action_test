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

import { Scope } from "./scope";
import { getMeta } from "../meta";
import { TypeDescription } from "../description";
import Fury from "../fury";

class BinaryReaderBuilder {
  constructor(private holder: string) {

  }

  ownName() {
    return this.holder;
  }

  getCursor() {
    return `${this.holder}.getCursor()`;
  }

  setCursor(v: number | string) {
    return `${this.holder}.setCursor(${v})`;
  }

  varInt32() {
    return `${this.holder}.varInt32()`;
  }

  varInt64() {
    return `${this.holder}.varInt64()`;
  }

  varUInt32() {
    return `${this.holder}.varUInt32()`;
  }

  varUInt64() {
    return `${this.holder}.varUInt64()`;
  }

  int8() {
    return `${this.holder}.int8()`;
  }

  buffer(len: string | number) {
    return `${this.holder}.buffer(${len})`;
  }

  bufferRef() {
    return `${this.holder}.bufferRef()`;
  }

  uint8() {
    return `${this.holder}.uint8()`;
  }

  stringUtf8At() {
    return `${this.holder}.stringUtf8At()`;
  }

  stringUtf8() {
    return `${this.holder}.stringUtf8()`;
  }

  stringLatin1() {
    return `${this.holder}.stringLatin1()`;
  }

  stringOfVarUInt32() {
    return `${this.holder}.stringOfVarUInt32()`;
  }

  float64() {
    return `${this.holder}.float64()`;
  }

  float32() {
    return `${this.holder}.float32()`;
  }

  float16() {
    return `${this.holder}.float16()`;
  }

  uint16() {
    return `${this.holder}.uint16()`;
  }

  int16() {
    return `${this.holder}.int16()`;
  }

  uint64() {
    return `${this.holder}.uint64()`;
  }

  skip(v: number) {
    return `${this.holder}.skip(${v})`;
  }

  int64() {
    return `${this.holder}.int64()`;
  }

  sliInt64() {
    return `${this.holder}.sliInt64()`;
  }

  uint32() {
    return `${this.holder}.uint32()`;
  }

  int32() {
    return `${this.holder}.int32()`;
  }
}

class BinaryWriterBuilder {
  constructor(private holder: string) {

  }

  ownName() {
    return this.holder;
  }

  skip(v: number | string) {
    return `${this.holder}.skip(${v})`;
  }

  getByteLen() {
    return `${this.holder}.getByteLen()`;
  }

  getReserved() {
    return `${this.holder}.getReserved()`;
  }

  reserve(v: number | string) {
    return `${this.holder}.reserve(${v})`;
  }

  uint16(v: number | string) {
    return `${this.holder}.uint16(${v})`;
  }

  int8(v: number | string) {
    return `${this.holder}.int8(${v})`;
  }

  int24(v: number | string) {
    return `${this.holder}.int24(${v})`;
  }

  uint8(v: number | string) {
    return `${this.holder}.uint8(${v})`;
  }

  int16(v: number | string) {
    return `${this.holder}.int16(${v})`;
  }

  varInt32(v: number | string) {
    return `${this.holder}.varInt32(${v})`;
  }

  varUInt32(v: number | string) {
    return `${this.holder}.varUInt32(${v})`;
  }

  varUInt64(v: number | string) {
    return `${this.holder}.varUInt64(${v})`;
  }

  varInt64(v: number | string) {
    return `${this.holder}.varInt64(${v})`;
  }

  stringOfVarUInt32(str: string) {
    return `${this.holder}.stringOfVarUInt32(${str})`;
  }

  bufferWithoutMemCheck(v: string) {
    return `${this.holder}.bufferWithoutMemCheck(${v})`;
  }

  uint64(v: number | string) {
    return `${this.holder}.uint64(${v})`;
  }

  buffer(v: string) {
    return `${this.holder}.buffer(${v})`;
  }

  float64(v: number | string) {
    return `${this.holder}.float64(${v})`;
  }

  float32(v: number | string) {
    return `${this.holder}.float32(${v})`;
  }

  int64(v: number | string) {
    return `${this.holder}.int64(${v})`;
  }

  sliInt64(v: number | string) {
    return `${this.holder}.sliInt64(${v})`;
  }

  uint32(v: number | string) {
    return `${this.holder}.uint32(${v})`;
  }

  int32(v: number | string) {
    return `${this.holder}.int32(${v})`;
  }

  getCursor() {
    return `${this.holder}.getCursor()`;
  }

  setUint32Position(offset: number | string, v: number | string) {
    return `${this.holder}.setUint32Position(${offset}, ${v})`;
  }

  setUint8Position(offset: number | string, v: number | string) {
    return `${this.holder}.setUint8Position(${offset}, ${v})`;
  }

  setUint16Position(offset: number | string, v: number | string) {
    return `${this.holder}.setUint16Position(${offset}, ${v})`;
  }
}

class ReferenceResolverBuilder {
  constructor(private holder: string) {

  }

  ownName() {
    return this.holder;
  }

  getReadObject(id: string | number) {
    return `${this.holder}.getReadObject(${id})`;
  }

  reference(obj: string) {
    return `${this.holder}.reference(${obj})`;
  }

  writeRef(obj: string) {
    return `${this.holder}.writeRef(${obj})`;
  }

  existsWriteObject(obj: string) {
    return `${this.holder}.existsWriteObject(${obj})`;
  }
}

class ClassResolverBuilder {
  constructor(private holder: string) {

  }

  ownName() {
    return this.holder;
  }

  getSerializerById(id: string | number) {
    return `${this.holder}.getSerializerById(${id})`;
  }

  getSerializerByTag(tag: string) {
    return `${this.holder}.getSerializerByTag(${tag})`;
  }

  createTagWriter(tag: string) {
    return `${this.holder}.createTagWriter("${tag}")`;
  }

  readTag(binaryReader: string) {
    return `${this.holder}.readTag(${binaryReader})`;
  }

  getSerializerByData(v: string) {
    return `${this.holder}.readTag(${v})`;
  }
}

export class CodecBuilder {
  reader: BinaryReaderBuilder;
  writer: BinaryWriterBuilder;
  referenceResolver: ReferenceResolverBuilder;
  classResolver: ClassResolverBuilder;

  constructor(scope: Scope, public fury: Fury) {
    const br = scope.declareByName("br", "fury.binaryReader");
    const bw = scope.declareByName("bw", "fury.binaryWriter");
    const cr = scope.declareByName("cr", "fury.classResolver");
    const rr = scope.declareByName("rr", "fury.referenceResolver");
    this.reader = new BinaryReaderBuilder(br);
    this.writer = new BinaryWriterBuilder(bw);
    this.classResolver = new ClassResolverBuilder(cr);
    this.referenceResolver = new ReferenceResolverBuilder(rr);
  }

  furyName() {
    return "fury";
  }

  meta(description: TypeDescription) {
    return getMeta(description, this.fury);
  }

  config() {
    return this.fury.config;
  }

  static isReserved(key: string) {
    return /^(?:do|if|in|for|let|new|try|var|case|else|enum|eval|false|null|this|true|void|with|break|catch|class|const|super|throw|while|yield|delete|export|import|public|return|static|switch|typeof|default|extends|finally|package|private|continue|debugger|function|arguments|interface|protected|implements|instanceof)$/.test(key);
  }

  static isDotPropAccessor(prop: string) {
    return /^[a-zA-Z_$][0-9a-zA-Z_$]*$/.test(prop);
  }

  static replaceBackslashAndQuote(v: string) {
    return v.replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
  }

  static safeString(target: string) {
    if (!CodecBuilder.isDotPropAccessor(target) || CodecBuilder.isReserved(target)) {
      return `"${CodecBuilder.replaceBackslashAndQuote(target)}"`;
    }
    return `"${target}"`;
  }

  static safePropAccessor(prop: string) {
    if (!CodecBuilder.isDotPropAccessor(prop) || CodecBuilder.isReserved(prop)) {
      return `["${CodecBuilder.replaceBackslashAndQuote(prop)}"]`;
    }
    return `.${prop}`;
  }

  static safePropName(prop: string) {
    if (!CodecBuilder.isDotPropAccessor(prop) || CodecBuilder.isReserved(prop)) {
      return `["${CodecBuilder.replaceBackslashAndQuote(prop)}"]`;
    }
    return prop;
  }

  getExternal(key: string) {
    return `external.${key}`;
  }
}
