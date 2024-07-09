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

import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.fury.format.encoder.Encoders;
import org.apache.fury.format.encoder.RowEncoder;
import org.apache.fury.format.row.binary.BinaryRow;
import org.apache.fury.format.type.TypeInference;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.apache.fury.test.bean.BeanA;
import org.testng.annotations.Test;

public class ArrowWriterTest {

  @Test(enabled = false)
  public void benchmarkArrowWriter() {
    Schema schema = TypeInference.inferSchema(BeanA.class);
    ArrowWriter arrowWriter = ArrowUtils.createArrowWriter(schema);
    RowEncoder<BeanA> encoder = Encoders.bean(BeanA.class);
    BeanA beanA = BeanA.createBeanA(2);
    BinaryRow row = encoder.toRow(beanA);
    for (int i = 0; i < 100; i++) {
      for (int j = 0; j < 100000; j++) {
        arrowWriter.write(row);
      }
      arrowWriter.finishAsRecordBatch();
    }
  }

  private ArrowRecordBatch createArrowRecordBatch() {
    Schema schema = TypeInference.inferSchema(BeanA.class);
    ArrowWriter arrowWriter = ArrowUtils.createArrowWriter(schema);
    RowEncoder<BeanA> encoder = Encoders.bean(BeanA.class);
    for (int i = 0; i < 10; i++) {
      BeanA beanA = BeanA.createBeanA(2);
      arrowWriter.write(encoder.toRow(beanA));
    }
    return arrowWriter.finishAsRecordBatch();
  }

  @Test
  public void testWrite() {
    ArrowRecordBatch recordBatch = createArrowRecordBatch();
    System.out.println("recordBatch " + recordBatch);
    recordBatch.close();
  }

  @Test
  public void testSerializeArrowRecordBatch() {
    ArrowRecordBatch recordBatch = createArrowRecordBatch();
    System.out.println("recordBatch serialized body size " + recordBatch.computeBodyLength());
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    ArrowUtils.serializeRecordBatch(recordBatch, buffer);
    System.out.println("IPC recordBatch size " + buffer.writerIndex());
    ArrowRecordBatch newRecordBatch = ArrowUtils.deserializeRecordBatch(buffer);
    System.out.println("newRecordBatch " + newRecordBatch);
    recordBatch.close();
    newRecordBatch.close();
  }
}
