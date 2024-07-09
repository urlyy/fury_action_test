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

package org.apache.fury.format.vectorized;

import static org.apache.fury.format.vectorized.ArrowUtilsTest.createVectorSchemaRoot;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.fury.Fury;
import org.apache.fury.config.Language;
import org.apache.fury.io.MemoryBufferReadableChannel;
import org.apache.fury.io.MemoryBufferWritableChannel;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.apache.fury.resolver.ClassResolver;
import org.apache.fury.serializer.BufferObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ArrowSerializersTest {
  @Test
  public void testRegisterArrowSerializer() throws Exception {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).build();
    ClassResolver classResolver = fury.getClassResolver();
    ArrowSerializers.registerSerializers(fury);
    assertEquals(classResolver.getSerializerClass(ArrowTable.class), ArrowTableSerializer.class);
    assertEquals(
        classResolver.getSerializerClass(VectorSchemaRoot.class),
        ArrowSerializers.VectorSchemaRootSerializer.class);
  }

  @Test
  public void testArrowTableBufferObject() throws IOException {
    int size = 200;
    VectorSchemaRoot root = createVectorSchemaRoot(size);
    Schema schema = root.getSchema();
    List<ArrowRecordBatch> recordBatches = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      VectorUnloader unloader = new VectorUnloader(root);
      recordBatches.add(unloader.getRecordBatch());
    }
    ArrowTable table = new ArrowTable(schema, recordBatches);
    ArrowSerializers.ArrowTableBufferObject o = new ArrowSerializers.ArrowTableBufferObject(table);
    MemoryBuffer buf = o.toBuffer();
    ReadableByteChannel channel = new MemoryBufferReadableChannel(buf);
    ArrowStreamReader reader = new ArrowStreamReader(channel, ArrowUtils.allocator);
    VectorSchemaRoot newRoot = reader.getVectorSchemaRoot();
    while (reader.loadNextBatch()) {
      recordBatches.add(new VectorUnloader(root).getRecordBatch());
    }
    ArrowTable newTable = new ArrowTable(root.getSchema(), recordBatches, ArrowUtils.allocator);
    assertTableEqual(newTable, table);
  }

  @Test
  public void testWriteVectorSchemaRoot() throws IOException {
    Collection<BufferObject> bufferObjects = new ArrayList<>();
    Fury fury = Fury.builder().requireClassRegistration(false).build();
    ArrowSerializers.registerSerializers(fury);
    int size = 2000;
    VectorSchemaRoot root = ArrowUtilsTest.createVectorSchemaRoot(size);
    Assert.assertEquals(
        fury.getClassResolver().getSerializer(VectorSchemaRoot.class).getClass(),
        ArrowSerializers.VectorSchemaRootSerializer.class);
    byte[] serializedBytes = fury.serialize(root, e -> !bufferObjects.add(e));
    Assert.assertFalse(bufferObjects.isEmpty());
    List<MemoryBuffer> buffers =
        bufferObjects.stream().map(BufferObject::toBuffer).collect(Collectors.toList());
    MemoryBuffer buffer = buffers.get(0);

    MemoryBuffer buffer2 = MemoryUtils.buffer(32);
    try (ArrowStreamWriter writer =
        new ArrowStreamWriter(root, null, new MemoryBufferWritableChannel(buffer2))) {
      // this will make root empty.
      writer.writeBatch();
    }
    Assert.assertEquals(buffer.size(), buffer2.writerIndex());
    Assert.assertTrue(buffer.equalTo(buffer2, 0, 0, buffer.size()));

    VectorSchemaRoot newRoot = (VectorSchemaRoot) fury.deserialize(serializedBytes, buffers);
    assertRecordBatchEqual(newRoot, root);

    // test in band serialization.
    fury = Fury.builder().requireClassRegistration(false).build();
    ArrowSerializers.registerSerializers(fury);
    newRoot = (VectorSchemaRoot) fury.deserialize(fury.serialize(root));
    assertRecordBatchEqual(newRoot, root);
  }

  @Test
  public void testWriteArrowTable() throws IOException {
    Collection<BufferObject> bufferObjects = new ArrayList<>();
    Fury fury = Fury.builder().requireClassRegistration(false).build();
    ArrowSerializers.registerSerializers(fury);
    int size = 2000;
    VectorSchemaRoot root = ArrowUtilsTest.createVectorSchemaRoot(size);
    Schema schema = root.getSchema();
    List<ArrowRecordBatch> recordBatches = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      VectorUnloader unloader = new VectorUnloader(root);
      recordBatches.add(unloader.getRecordBatch());
    }
    ArrowTable table = new ArrowTable(schema, recordBatches);
    Assert.assertEquals(
        fury.getClassResolver().getSerializer(ArrowTable.class).getClass(),
        ArrowTableSerializer.class);
    byte[] serializedData = fury.serialize(table, e -> !bufferObjects.add(e));
    List<MemoryBuffer> buffers =
        bufferObjects.stream().map(BufferObject::toBuffer).collect(Collectors.toList());
    Assert.assertEquals(bufferObjects.size(), 1);

    ArrowTable newTable = (ArrowTable) fury.deserialize(serializedData, buffers);
    assertTableEqual(newTable, table);

    // test in band serialization.
    fury = Fury.builder().requireClassRegistration(false).build();
    ArrowSerializers.registerSerializers(fury);
    newTable = (ArrowTable) fury.deserialize(fury.serialize(table));
    assertTableEqual(newTable, table);
  }

  public static void assertRecordBatchEqual(VectorSchemaRoot root1, VectorSchemaRoot root2) {
    Assert.assertEquals(root1.getSchema(), root2.getSchema());
    Assert.assertEquals(root1.getRowCount(), root2.getRowCount());
    for (int i = 0; i < root2.getFieldVectors().size(); i++) {
      for (int j = 0; j < root2.getRowCount(); j += root2.getRowCount() / 100) {
        Assert.assertEquals(
            root1.getFieldVectors().get(i).getObject(j),
            root2.getFieldVectors().get(i).getObject(j));
      }
    }
  }

  public static void assertTableEqual(ArrowTable t1, ArrowTable t2) {
    VectorSchemaRoot root1 = t1.toVectorSchemaRoot(true);
    VectorSchemaRoot root2 = t2.toVectorSchemaRoot(true);
    assertEquals(root1.getSchema(), t2.getSchema());
    while (t1.loadNextBatch()) {
      t2.loadNextBatch();
      Assert.assertEquals(root1.getRowCount(), root2.getRowCount());
      for (int i = 0; i < root2.getFieldVectors().size(); i++) {
        for (int j = 0; j < root2.getRowCount(); j += root2.getRowCount() / 100) {
          Assert.assertEquals(
              root1.getFieldVectors().get(i).getObject(j),
              root2.getFieldVectors().get(i).getObject(j));
        }
      }
    }
  }
}
