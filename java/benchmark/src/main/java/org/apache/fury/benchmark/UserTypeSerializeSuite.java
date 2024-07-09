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

package org.apache.fury.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.fury.benchmark.data.MediaContent;
import org.apache.fury.benchmark.data.Sample;
import org.apache.fury.benchmark.state.FlatBuffersState;
import org.apache.fury.benchmark.state.FstState;
import org.apache.fury.benchmark.state.FuryState;
import org.apache.fury.benchmark.state.HessionState;
import org.apache.fury.benchmark.state.JDKState;
import org.apache.fury.benchmark.state.JsonbState;
import org.apache.fury.benchmark.state.KryoState;
import org.apache.fury.benchmark.state.ObjectType;
import org.apache.fury.benchmark.state.ProtoBuffersState;
import org.apache.fury.benchmark.state.ProtostuffState;
import org.apache.fury.memory.ByteBufferUtil;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@CompilerControl(value = CompilerControl.Mode.INLINE)
public class UserTypeSerializeSuite {

  @org.openjdk.jmh.annotations.Benchmark
  public Object kryo_serialize(KryoState.KryoUserTypeState state) {
    state.output.setPosition(0);
    state.kryo.writeClassAndObject(state.output, state.object);
    return state.output;
  }

  @org.openjdk.jmh.annotations.Benchmark
  public Object kryo_serialize_compatible(KryoState.KryoCompatibleState state) {
    state.output.setPosition(0);
    state.kryo.writeClassAndObject(state.output, state.object);
    return state.output;
  }

  @Benchmark
  public Object fury_serialize(FuryState.FuryUserTypeState state) {
    state.buffer.writerIndex(0);
    state.fury.serialize(state.buffer, state.object);
    return state.buffer;
  }

  @Benchmark
  public Object fury_serialize_compatible(FuryState.FuryCompatibleState state) {
    state.buffer.writerIndex(0);
    state.fury.serialize(state.buffer, state.object);
    return state.buffer;
  }

  @Benchmark
  public Object furymetashared_serialize_compatible(FuryState.FuryMetaSharedState state) {
    state.buffer.writerIndex(0);
    state.fury.getSerializationContext().setMetaContext(state.writerMetaContext);
    state.fury.serialize(state.buffer, state.object);
    return state.buffer;
  }

  @Benchmark
  public byte[] fst_serialize(FstState.FstUserTypeState state, Blackhole bh) {
    return FstState.FstBenchmarkState.serialize(bh, state, state.object);
  }

  @Benchmark
  public ByteArrayOutputStream hession_serialize(HessionState.HessionUserTypeState state) {
    state.bos.reset();
    state.out.reset();
    HessionState.serialize(state.out, state.object);
    return state.bos;
  }

  @Benchmark
  public ByteArrayOutputStream hession_serialize_compatible(
      HessionState.HessianCompatibleState state) {
    state.bos.reset();
    state.out.reset();
    HessionState.serialize(state.out, state.object);
    return state.bos;
  }

  @Benchmark
  public byte[] protostuff_serialize(ProtostuffState.ProtostuffUserTypeState state) {
    return ProtostuffState.serialize(state.object, state.schema, state.buffer);
  }

  @Benchmark
  public ByteArrayOutputStream jdk_serialize(JDKState.JDKUserTypeState state) {
    state.bos.reset();
    JDKState.serialize(state.bos, state.object);
    return state.bos;
  }

  // @Benchmark
  public byte[] jsonb_serialize(JsonbState.JsonbUserTypeState state, Blackhole bh) {
    return JsonbState.serialize(bh, state, state.object);
  }

  @Benchmark
  public byte[] protobuffers_serialize(ProtoBuffersState.ProtoBuffersUserTypeState state) {
    if (state.objectType == ObjectType.SAMPLE) {
      return ProtoBuffersState.serializeSample((Sample) state.object);
    } else {
      return ProtoBuffersState.serializeMediaContent((MediaContent) state.object);
    }
  }

  @Benchmark
  public Object flatbuffers_serialize(FlatBuffersState.FlatBuffersUserTypeState state) {
    ByteBufferUtil.clearBuffer(state.directBuffer);
    if (state.objectType == ObjectType.SAMPLE) {
      return FlatBuffersState.serializeSample((Sample) state.object, state.directBuffer);
    } else {
      return FlatBuffersState.serializeMediaContent((MediaContent) state.object);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      String commandLine =
          "org.apache.fury.*UserTypeSerializeSuite.fury -f 3 -wi 5 -i 10 -t 1 -w 2s -r 2s -rf csv "
              + "-p objectType=MEDIA_CONTENT -p bufferType=array -p references=false";
      System.out.println(commandLine);
      args = commandLine.split(" ");
    }
    Main.main(args);
  }
}
