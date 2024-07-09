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

package org.apache.fury.serializer;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.fury.Fury;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.reflect.ReflectionUtils;
import org.apache.fury.util.Preconditions;

/**
 * Serializer for java serializable lambda. Use fury to serialize java lambda instead of JDK
 * serialization to avoid serialization captured values in closure using JDK, which mis slow and not
 * secure(will work around type white-list).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class LambdaSerializer extends Serializer {
  private static final Class<SerializedLambda> SERIALIZED_LAMBDA = SerializedLambda.class;
  private static final Method READ_RESOLVE_METHOD =
      (Method)
          Objects.requireNonNull(
              ReflectionUtils.getObjectFieldValue(
                  ObjectStreamClass.lookup(SERIALIZED_LAMBDA), "readResolveMethod"));
  private static final boolean SERIALIZED_LAMBDA_HAS_JDK_WRITE =
      JavaSerializer.getWriteObjectMethod(SERIALIZED_LAMBDA) != null;
  private static final boolean SERIALIZED_LAMBDA_HAS_JDK_READ =
      JavaSerializer.getReadObjectMethod(SERIALIZED_LAMBDA) != null;
  private final Method writeReplaceMethod;
  private Serializer dataSerializer;

  public LambdaSerializer(Fury fury, Class cls) {
    super(fury, cls);
    if (cls != ReplaceStub.class) {
      if (!Serializable.class.isAssignableFrom(cls)) {
        String msg =
            String.format(
                "Lambda %s needs to implement %s for serialization",
                cls, Serializable.class.getName());
        throw new UnsupportedOperationException(msg);
      }
      writeReplaceMethod =
          (Method)
              ReflectionUtils.getObjectFieldValue(
                  ObjectStreamClass.lookup(cls), "writeReplaceMethod");
    } else {
      writeReplaceMethod = null;
    }
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    assert value.getClass() != ReplaceStub.class;
    try {
      Object replacement = writeReplaceMethod.invoke(value);
      Preconditions.checkArgument(SERIALIZED_LAMBDA.isInstance(replacement));
      getDataSerializer().write(buffer, replacement);
    } catch (Exception e) {
      throw new RuntimeException("Can't serialize lambda " + value, e);
    }
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    try {
      Object replacement = getDataSerializer().read(buffer);
      return READ_RESOLVE_METHOD.invoke(replacement);
    } catch (Exception e) {
      throw new RuntimeException("Can't deserialize lambda", e);
    }
  }

  private Serializer getDataSerializer() {
    // Create serializer lazily to avoid creation cost if no lambda to be serialized.
    Serializer dataSerializer = this.dataSerializer;
    if (dataSerializer == null) {
      Class<? extends Serializer> sc;
      if (SERIALIZED_LAMBDA_HAS_JDK_WRITE || SERIALIZED_LAMBDA_HAS_JDK_READ) {
        sc = fury.getDefaultJDKStreamSerializerType();
      } else {
        sc =
            fury.getJITContext()
                .registerSerializerJITCallback(
                    () -> ObjectSerializer.class,
                    () -> CodegenSerializer.loadCodegenSerializer(fury, SERIALIZED_LAMBDA),
                    c -> {
                      this.dataSerializer = Serializers.newSerializer(fury, SERIALIZED_LAMBDA, c);
                      fury.getClassResolver().clearSerializer(SERIALIZED_LAMBDA);
                    });
      }
      this.dataSerializer = dataSerializer = Serializers.newSerializer(fury, SERIALIZED_LAMBDA, sc);
      fury.getClassResolver().clearSerializer(SERIALIZED_LAMBDA);
    }
    return dataSerializer;
  }

  /**
   * Class name of dynamic generated class is not fixed, so we use a stub class to mock dynamic
   * class.
   */
  public static class ReplaceStub {}
}
