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

package org.apache.fury.builder;

import static org.apache.fury.codegen.Code.LiteralValue.FalseLiteral;
import static org.apache.fury.codegen.Expression.Invoke.inlineInvoke;
import static org.apache.fury.codegen.ExpressionUtils.add;
import static org.apache.fury.collection.Collections.ofHashSet;
import static org.apache.fury.type.TypeUtils.OBJECT_ARRAY_TYPE;
import static org.apache.fury.type.TypeUtils.OBJECT_TYPE;
import static org.apache.fury.type.TypeUtils.PRIMITIVE_BYTE_ARRAY_TYPE;
import static org.apache.fury.type.TypeUtils.PRIMITIVE_INT_TYPE;
import static org.apache.fury.type.TypeUtils.PRIMITIVE_LONG_TYPE;
import static org.apache.fury.type.TypeUtils.PRIMITIVE_VOID_TYPE;
import static org.apache.fury.type.TypeUtils.getRawType;
import static org.apache.fury.type.TypeUtils.getSizeOfPrimitiveType;
import static org.apache.fury.type.TypeUtils.isPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.fury.Fury;
import org.apache.fury.codegen.Code;
import org.apache.fury.codegen.CodegenContext;
import org.apache.fury.codegen.Expression;
import org.apache.fury.codegen.Expression.Inlineable;
import org.apache.fury.codegen.Expression.Invoke;
import org.apache.fury.codegen.Expression.ListExpression;
import org.apache.fury.codegen.Expression.Literal;
import org.apache.fury.codegen.Expression.NewInstance;
import org.apache.fury.codegen.Expression.Reference;
import org.apache.fury.codegen.Expression.ReplaceStub;
import org.apache.fury.codegen.Expression.StaticInvoke;
import org.apache.fury.codegen.ExpressionVisitor;
import org.apache.fury.memory.Platform;
import org.apache.fury.reflect.TypeRef;
import org.apache.fury.serializer.ObjectSerializer;
import org.apache.fury.serializer.PrimitiveSerializers.LongSerializer;
import org.apache.fury.type.Descriptor;
import org.apache.fury.type.DescriptorGrouper;
import org.apache.fury.util.Preconditions;
import org.apache.fury.util.function.SerializableSupplier;
import org.apache.fury.util.record.RecordUtils;

/**
 * Generate sequential read/write code for java serialization to speed up performance. It also
 * reduces space overhead introduced by aligning. Codegen only for time-consuming field, others
 * delegate to fury.
 *
 * <p>In order to improve jit-compile and inline, serialization code should be spilt groups to avoid
 * huge/big methods.
 *
 * <p>With meta context share enabled and compatible mode, this serializer will take all non-inner
 * final types as non-final, so that fury can write class definition when write class info for those
 * types.
 *
 * @see ObjectCodecOptimizer for code stats and split heuristics.
 */
public class ObjectCodecBuilder extends BaseObjectCodecBuilder {
  public static final String BUFFER_NAME = "buffer";
  private final Literal classVersionHash;
  protected ObjectCodecOptimizer objectCodecOptimizer;
  protected Map<String, Integer> recordReversedMapping;

  public ObjectCodecBuilder(Class<?> beanClass, Fury fury) {
    super(TypeRef.of(beanClass), fury, Generated.GeneratedObjectSerializer.class);
    Collection<Descriptor> descriptors;
    boolean shareMeta = fury.getConfig().isMetaShareEnabled();
    if (shareMeta) {
      descriptors =
          visitFury(
              f ->
                  f.getClassResolver()
                      .getClassDef(beanClass, true)
                      .getDescriptors(classResolver, beanClass));
    } else {
      descriptors = fury.getClassResolver().getAllDescriptorsMap(beanClass, true).values();
    }
    classVersionHash =
        new Literal(ObjectSerializer.computeVersionHash(descriptors), PRIMITIVE_INT_TYPE);
    DescriptorGrouper grouper =
        DescriptorGrouper.createDescriptorGrouper(
            fury.getClassResolver()::isMonomorphic,
            descriptors,
            false,
            fury.compressInt(),
            fury.compressLong());
    objectCodecOptimizer =
        new ObjectCodecOptimizer(beanClass, grouper, !fury.isBasicTypesRefIgnored(), ctx);
    if (isRecord) {
      if (!recordCtrAccessible) {
        buildRecordComponentDefaultValues();
      }
      recordReversedMapping = RecordUtils.buildFieldToComponentMapping(beanClass);
    }
  }

  protected ObjectCodecBuilder(TypeRef<?> beanType, Fury fury, Class<?> superSerializerClass) {
    super(beanType, fury, superSerializerClass);
    this.classVersionHash = null;
    if (isRecord) {
      if (!recordCtrAccessible) {
        buildRecordComponentDefaultValues();
      }
      recordReversedMapping = RecordUtils.buildFieldToComponentMapping(beanClass);
    }
  }

  @Override
  protected String codecSuffix() {
    return "";
  }

  @Override
  protected void addCommonImports() {
    super.addCommonImports();
    ctx.addImport(Generated.GeneratedObjectSerializer.class);
  }

  /** Mark non-inner registered final types as non-final to write class def for those types. */
  @Override
  protected boolean isMonomorphic(Class<?> clz) {
    return visitFury(f -> f.getClassResolver().isMonomorphic(clz));
  }

  /**
   * Return an expression that serialize java bean of type {@link CodecBuilder#beanClass} to buffer.
   */
  @Override
  public Expression buildEncodeExpression() {
    Reference inputObject = new Reference(ROOT_OBJECT_NAME, OBJECT_TYPE, false);
    Reference buffer = new Reference(BUFFER_NAME, bufferTypeRef, false);

    ListExpression expressions = new ListExpression();
    Expression bean = tryCastIfPublic(inputObject, beanType, ctx.newName(beanClass));
    expressions.add(bean);
    if (fury.checkClassVersion()) {
      expressions.add(new Invoke(buffer, "writeInt32", classVersionHash));
    }
    expressions.addAll(serializePrimitives(bean, buffer, objectCodecOptimizer.primitiveGroups));
    int numGroups = getNumGroups(objectCodecOptimizer);
    addGroupExpressions(
        objectCodecOptimizer.boxedWriteGroups, numGroups, expressions, bean, buffer);
    addGroupExpressions(
        objectCodecOptimizer.finalWriteGroups, numGroups, expressions, bean, buffer);
    addGroupExpressions(
        objectCodecOptimizer.otherWriteGroups, numGroups, expressions, bean, buffer);
    for (Descriptor descriptor :
        objectCodecOptimizer.descriptorGrouper.getCollectionDescriptors()) {
      expressions.add(serializeGroup(Collections.singletonList(descriptor), bean, buffer, false));
    }
    for (Descriptor d : objectCodecOptimizer.descriptorGrouper.getMapDescriptors()) {
      expressions.add(serializeGroup(Collections.singletonList(d), bean, buffer, false));
    }
    return expressions;
  }

  private void addGroupExpressions(
      List<List<Descriptor>> writeGroup,
      int numGroups,
      ListExpression expressions,
      Expression bean,
      Reference buffer) {
    for (List<Descriptor> group : writeGroup) {
      if (group.isEmpty()) {
        continue;
      }
      boolean inline = group.size() == 1 && numGroups < 10;
      expressions.add(serializeGroup(group, bean, buffer, inline));
    }
  }

  private int getNumGroups(ObjectCodecOptimizer objectCodecOptimizer) {
    return objectCodecOptimizer.boxedWriteGroups.size()
        + objectCodecOptimizer.finalWriteGroups.size()
        + objectCodecOptimizer.otherWriteGroups.size()
        + objectCodecOptimizer.descriptorGrouper.getCollectionDescriptors().size()
        + objectCodecOptimizer.descriptorGrouper.getMapDescriptors().size();
  }

  private Expression serializeGroup(
      List<Descriptor> group, Expression bean, Expression buffer, boolean inline) {
    SerializableSupplier<Expression> expressionSupplier =
        () -> {
          ListExpression groupExpressions = new ListExpression();
          for (Descriptor d : group) {
            // `bean` will be replaced by `Reference` to cut-off expr dependency.
            Expression fieldValue = getFieldValue(bean, d);
            walkPath.add(d.getDeclaringClass() + d.getName());
            Expression fieldExpr = serializeFor(fieldValue, buffer, d.getTypeRef());
            walkPath.removeLast();
            groupExpressions.add(fieldExpr);
          }
          return groupExpressions;
        };
    if (inline) {
      return expressionSupplier.get();
    }
    return objectCodecOptimizer.invokeGenerated(expressionSupplier, "writeFields");
  }

  /**
   * Return a list of expressions that serialize all primitive fields. This can reduce unnecessary
   * grow call and increment writerIndex in writeXXX.
   */
  private List<Expression> serializePrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    int totalSize = getTotalSizeOfPrimitives(primitiveGroups);
    if (totalSize == 0) {
      return new ArrayList<>();
    }
    if (fury.compressInt() || fury.compressLong()) {
      return serializePrimitivesCompressed(bean, buffer, primitiveGroups, totalSize);
    } else {
      return serializePrimitivesUnCompressed(bean, buffer, primitiveGroups, totalSize);
    }
  }

  protected int getNumPrimitiveFields(List<List<Descriptor>> primitiveGroups) {
    return primitiveGroups.stream().mapToInt(List::size).sum();
  }

  private List<Expression> serializePrimitivesUnCompressed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = new Literal(totalSize, PRIMITIVE_INT_TYPE);
    // After this grow, following writes can be unsafe without checks.
    expressions.add(new Invoke(buffer, "grow", totalSizeLiteral));
    // Must grow first, otherwise may get invalid address.
    Expression base = new Invoke(buffer, "getHeapMemory", "base", PRIMITIVE_BYTE_ARRAY_TYPE);
    Expression writerAddr =
        new Invoke(buffer, "_unsafeWriterAddress", "writerAddr", PRIMITIVE_LONG_TYPE);
    expressions.add(base);
    expressions.add(writerAddr);
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      // use Reference to cut-off expr dependency.
      for (Descriptor descriptor : group) {
        Class<?> clz = descriptor.getRawType();
        Preconditions.checkArgument(isPrimitive(clz));
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (clz == byte.class) {
          groupExpressions.add(unsafePut(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (clz == boolean.class) {
          groupExpressions.add(unsafePutBoolean(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (clz == char.class) {
          groupExpressions.add(unsafePutChar(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (clz == short.class) {
          groupExpressions.add(unsafePutShort(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (clz == int.class) {
          groupExpressions.add(unsafePutInt(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (clz == long.class) {
          groupExpressions.add(unsafePutLong(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else if (clz == float.class) {
          groupExpressions.add(unsafePutFloat(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (clz == double.class) {
          groupExpressions.add(unsafePutDouble(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else {
          throw new IllegalStateException("impossible");
        }
      }
      if (numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, base, writerAddr), groupExpressions, "writeFields"));
      }
    }
    Expression increaseWriterIndex =
        new Invoke(
            buffer,
            "_increaseWriterIndexUnsafe",
            new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseWriterIndex);
    return expressions;
  }

  private List<Expression> serializePrimitivesCompressed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    // int/long may need extra one-byte for writing.
    int extraSize = 0;
    for (List<Descriptor> group : primitiveGroups) {
      for (Descriptor d : group) {
        if (d.getRawType() == int.class) {
          // varint may be written as 5bytes, use 8bytes for written as long to reduce cost.
          extraSize += 4;
        } else if (d.getRawType() == long.class) {
          extraSize += 1; // long use 1~9 bytes.
        }
      }
    }
    int growSize = totalSize + extraSize;
    // After this grow, following writes can be unsafe without checks.
    expressions.add(new Invoke(buffer, "grow", Literal.ofInt(growSize)));
    // Must grow first, otherwise may get invalid address.
    Expression base = new Invoke(buffer, "getHeapMemory", "base", PRIMITIVE_BYTE_ARRAY_TYPE);
    expressions.add(base);
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      Expression writerAddr =
          new Invoke(buffer, "_unsafeWriterAddress", "writerAddr", PRIMITIVE_LONG_TYPE);
      // use Reference to cut-off expr dependency.
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        Class<?> clz = descriptor.getRawType();
        Preconditions.checkArgument(isPrimitive(clz));
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (clz == byte.class) {
          groupExpressions.add(unsafePut(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (clz == boolean.class) {
          groupExpressions.add(unsafePutBoolean(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (clz == char.class) {
          groupExpressions.add(unsafePutChar(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (clz == short.class) {
          groupExpressions.add(unsafePutShort(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (clz == float.class) {
          groupExpressions.add(unsafePutFloat(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (clz == double.class) {
          groupExpressions.add(unsafePutDouble(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else if (clz == int.class) {
          if (!fury.compressInt()) {
            groupExpressions.add(unsafePutInt(base, getWriterPos(writerAddr, acc), fieldValue));
            acc += 4;
          } else {
            if (!compressStarted) {
              // int/long are sorted in the last.
              addIncWriterIndexExpr(groupExpressions, buffer, acc);
              compressStarted = true;
            }
            groupExpressions.add(new Invoke(buffer, "_unsafeWriteVarInt32", fieldValue));
            acc += 0;
          }
        } else if (clz == long.class) {
          if (!fury.compressLong()) {
            groupExpressions.add(unsafePutLong(base, getWriterPos(writerAddr, acc), fieldValue));
            acc += 8;
          } else {
            if (!compressStarted) {
              // int/long are sorted in the last.
              addIncWriterIndexExpr(groupExpressions, buffer, acc);
              compressStarted = true;
            }
            groupExpressions.add(
                LongSerializer.writeInt64(buffer, fieldValue, fury.longEncoding(), false));
          }
        } else {
          throw new IllegalStateException("impossible");
        }
      }
      if (!compressStarted) {
        // int/long are sorted in the last.
        addIncWriterIndexExpr(groupExpressions, buffer, acc);
      }
      if (numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, base), groupExpressions, "writeFields"));
      }
    }
    return expressions;
  }

  private void addIncWriterIndexExpr(ListExpression expressions, Expression buffer, int diff) {
    if (diff != 0) {
      expressions.add(new Invoke(buffer, "_increaseWriterIndexUnsafe", Literal.ofInt(diff)));
    }
  }

  private int getTotalSizeOfPrimitives(List<List<Descriptor>> primitiveGroups) {
    return primitiveGroups.stream()
        .flatMap(Collection::stream)
        .mapToInt(d -> getSizeOfPrimitiveType(d.getRawType()))
        .sum();
  }

  private Expression getWriterPos(Expression writerPos, long acc) {
    if (acc == 0) {
      return writerPos;
    }
    return add(writerPos, Literal.ofLong(acc));
  }

  public Expression buildDecodeExpression() {
    Reference buffer = new Reference(BUFFER_NAME, bufferTypeRef, false);
    ListExpression expressions = new ListExpression();
    if (fury.checkClassVersion()) {
      expressions.add(checkClassVersion(buffer));
    }
    Expression bean;
    if (!isRecord) {
      bean = newBean();
      Expression referenceObject =
          new Invoke(refResolverRef, "reference", PRIMITIVE_VOID_TYPE, bean);
      expressions.add(bean);
      expressions.add(referenceObject);
    } else {
      if (recordCtrAccessible) {
        bean = new FieldsCollector();
      } else {
        bean = buildComponentsArray();
      }
    }
    expressions.addAll(deserializePrimitives(bean, buffer, objectCodecOptimizer.primitiveGroups));
    int numGroups = getNumGroups(objectCodecOptimizer);
    deserializeReadGroup(
        objectCodecOptimizer.boxedReadGroups, numGroups, expressions, bean, buffer);
    deserializeReadGroup(
        objectCodecOptimizer.finalReadGroups, numGroups, expressions, bean, buffer);
    deserializeReadGroup(
        objectCodecOptimizer.otherReadGroups, numGroups, expressions, bean, buffer);
    for (Descriptor d : objectCodecOptimizer.descriptorGrouper.getCollectionDescriptors()) {
      expressions.add(deserializeGroup(Collections.singletonList(d), bean, buffer, false));
    }
    for (Descriptor d : objectCodecOptimizer.descriptorGrouper.getMapDescriptors()) {
      expressions.add(deserializeGroup(Collections.singletonList(d), bean, buffer, false));
    }
    if (isRecord) {
      if (recordCtrAccessible) {
        assert bean instanceof FieldsCollector;
        FieldsCollector collector = (FieldsCollector) bean;
        bean = createRecord(collector.recordValuesMap);
      } else {
        bean =
            new StaticInvoke(
                RecordUtils.class,
                "invokeRecordCtrHandle",
                OBJECT_TYPE,
                getRecordCtrHandle(),
                bean);
      }
    }
    expressions.add(new Expression.Return(bean));
    return expressions;
  }

  private void deserializeReadGroup(
      List<List<Descriptor>> readGroups,
      int numGroups,
      ListExpression expressions,
      Expression bean,
      Reference buffer) {
    for (List<Descriptor> group : readGroups) {
      if (group.isEmpty()) {
        continue;
      }
      boolean inline = group.size() == 1 && numGroups < 10;
      expressions.add(deserializeGroup(group, bean, buffer, inline));
    }
  }

  protected Expression buildComponentsArray() {
    return new StaticInvoke(
        Platform.class, "copyObjectArray", OBJECT_ARRAY_TYPE, recordComponentDefaultValues);
  }

  protected Expression createRecord(SortedMap<Integer, Expression> recordComponents) {
    Expression[] params = recordComponents.values().toArray(new Expression[0]);
    return new NewInstance(beanType, params);
  }

  private class FieldsCollector implements Expression {
    private final TreeMap<Integer, Expression> recordValuesMap = new TreeMap<>();

    @Override
    public TypeRef<?> type() {
      return beanType;
    }

    @Override
    public Code.ExprCode doGenCode(CodegenContext ctx) {
      return new Code.ExprCode(FalseLiteral, Code.variable(getRawType(beanType), "null"));
    }
  }

  @Override
  protected Expression setFieldValue(Expression bean, Descriptor d, Expression value) {
    if (isRecord) {
      if (recordCtrAccessible) {
        if (value instanceof Inlineable) {
          ((Inlineable) value).inline(false);
        }
        int index = recordReversedMapping.get(d.getName());
        FieldsCollector collector = (FieldsCollector) bean;
        collector.recordValuesMap.put(index, value);
        return value;
      } else {
        int index = recordReversedMapping.get(d.getName());
        return new Expression.AssignArrayElem(bean, value, Literal.ofInt(index));
      }
    }
    return super.setFieldValue(bean, d, value);
  }

  protected Expression deserializeGroup(
      List<Descriptor> group, Expression bean, Expression buffer, boolean inline) {
    if (isRecord) {
      return deserializeGroupForRecord(group, bean, buffer);
    }
    SerializableSupplier<Expression> exprSupplier =
        () -> {
          ListExpression groupExpressions = new ListExpression();
          // use Reference to cut-off expr dependency.
          for (Descriptor d : group) {
            ExpressionVisitor.ExprHolder exprHolder = ExpressionVisitor.ExprHolder.of("bean", bean);
            walkPath.add(d.getDeclaringClass() + d.getName());
            Expression action =
                deserializeFor(
                    buffer,
                    d.getTypeRef(),
                    // `bean` will be replaced by `Reference` to cut-off expr
                    // dependency.
                    expr ->
                        setFieldValue(
                            exprHolder.get("bean"), d, tryInlineCast(expr, d.getTypeRef())));
            walkPath.removeLast();
            groupExpressions.add(action);
          }
          return groupExpressions;
        };
    if (inline) {
      return exprSupplier.get();
    } else {
      return objectCodecOptimizer.invokeGenerated(exprSupplier, "readFields");
    }
  }

  protected Expression deserializeGroupForRecord(
      List<Descriptor> group, Expression bean, Expression buffer) {
    ListExpression groupExpressions = new ListExpression();
    // use Reference to cut-off expr dependency.
    for (Descriptor d : group) {
      Expression v = deserializeFor(buffer, d.getTypeRef(), expr -> expr);
      Expression action = setFieldValue(bean, d, tryInlineCast(v, d.getTypeRef()));
      groupExpressions.add(action);
    }
    return groupExpressions;
  }

  private Expression checkClassVersion(Expression buffer) {
    return new StaticInvoke(
        ObjectSerializer.class,
        "checkClassVersion",
        PRIMITIVE_VOID_TYPE,
        false,
        furyRef,
        inlineInvoke(buffer, readIntFunc(), PRIMITIVE_INT_TYPE),
        Objects.requireNonNull(classVersionHash));
  }

  /**
   * Return a list of expressions that deserialize all primitive fields. This can reduce unnecessary
   * check call and increment readerIndex in writeXXX.
   */
  private List<Expression> deserializePrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    int totalSize = getTotalSizeOfPrimitives(primitiveGroups);
    if (totalSize == 0) {
      return new ArrayList<>();
    }
    if (fury.compressInt() || fury.compressLong()) {
      return deserializeCompressedPrimitives(bean, buffer, primitiveGroups);
    } else {
      return deserializeUnCompressedPrimitives(bean, buffer, primitiveGroups, totalSize);
    }
  }

  private List<Expression> deserializeUnCompressedPrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = Literal.ofInt(totalSize);
    Expression heapBuffer =
        new Invoke(buffer, "getHeapMemory", "heapBuffer", PRIMITIVE_BYTE_ARRAY_TYPE);
    Expression readerAddr =
        new Invoke(buffer, "getUnsafeReaderAddress", "readerAddr", PRIMITIVE_LONG_TYPE);
    expressions.add(heapBuffer);
    expressions.add(readerAddr);
    // After this check, following read can be totally unsafe without checks
    expressions.add(new Invoke(buffer, "checkReadableBytes", totalSizeLiteral));
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      for (Descriptor descriptor : group) {
        TypeRef<?> type = descriptor.getTypeRef();
        Class<?> clz = getRawType(type);
        Preconditions.checkArgument(isPrimitive(clz));
        Expression fieldValue;
        if (clz == byte.class) {
          fieldValue = unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (clz == boolean.class) {
          fieldValue = unsafeGetBoolean(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (clz == char.class) {
          fieldValue = unsafeGetChar(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (clz == short.class) {
          fieldValue = unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (clz == int.class) {
          fieldValue = unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (clz == long.class) {
          fieldValue = unsafeGetLong(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else if (clz == float.class) {
          fieldValue = unsafeGetFloat(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (clz == double.class) {
          fieldValue = unsafeGetDouble(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else {
          throw new IllegalStateException("impossible");
        }
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, heapBuffer, readerAddr), groupExpressions, "readFields"));
      }
    }
    Expression increaseReaderIndex =
        new Invoke(
            buffer, "increaseReaderIndex", new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseReaderIndex);
    return expressions;
  }

  private List<Expression> deserializeCompressedPrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Expression heapBuffer =
        new Invoke(buffer, "getHeapMemory", "heapBuffer", PRIMITIVE_BYTE_ARRAY_TYPE);
    expressions.add(heapBuffer);
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      Expression readerAddr =
          new Invoke(buffer, "getUnsafeReaderAddress", "readerAddr", PRIMITIVE_LONG_TYPE);
      // After this check, following read can be totally unsafe without checks.
      ReplaceStub checkReadableBytesStub = new ReplaceStub();
      expressions.add(checkReadableBytesStub);
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        TypeRef<?> type = descriptor.getTypeRef();
        Class<?> clz = getRawType(type);
        Preconditions.checkArgument(isPrimitive(clz));
        Expression fieldValue;
        if (clz == byte.class) {
          fieldValue = unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (clz == boolean.class) {
          fieldValue = unsafeGetBoolean(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (clz == char.class) {
          fieldValue = unsafeGetChar(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (clz == short.class) {
          fieldValue = unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (clz == float.class) {
          fieldValue = unsafeGetFloat(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (clz == double.class) {
          fieldValue = unsafeGetDouble(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else if (clz == int.class) {
          if (!fury.compressInt()) {
            fieldValue = unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc));
            acc += 4;
          } else {
            if (!compressStarted) {
              compressStarted = true;
              addIncReaderIndexExpr(groupExpressions, buffer, acc);
            }
            fieldValue = readVarInt32(buffer);
          }
        } else if (clz == long.class) {
          if (!fury.compressLong()) {
            fieldValue = unsafeGetLong(heapBuffer, getReaderAddress(readerAddr, acc));
            acc += 8;
          } else {
            if (!compressStarted) {
              compressStarted = true;
              addIncReaderIndexExpr(groupExpressions, buffer, acc);
            }
            fieldValue = LongSerializer.readInt64(buffer, fury.longEncoding());
          }
        } else {
          throw new IllegalStateException("impossible");
        }
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (acc != 0) {
        checkReadableBytesStub.setTargetObject(
            new Invoke(buffer, "checkReadableBytes", Literal.ofInt(acc)));
      }
      if (!compressStarted) {
        addIncReaderIndexExpr(groupExpressions, buffer, acc);
      }
      if (numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, heapBuffer), groupExpressions, "readFields"));
      }
    }
    return expressions;
  }

  private void addIncReaderIndexExpr(ListExpression expressions, Expression buffer, int diff) {
    if (diff != 0) {
      expressions.add(new Invoke(buffer, "increaseReaderIndex", Literal.ofInt(diff)));
    }
  }

  private Expression getReaderAddress(Expression readerPos, long acc) {
    if (acc == 0) {
      return readerPos;
    }
    return add(readerPos, new Literal(acc, PRIMITIVE_LONG_TYPE));
  }
}
