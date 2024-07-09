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

package org.apache.fury.io;

import java.nio.ByteBuffer;
import org.apache.fury.memory.MemoryBuffer;

/** An abstract {@link FuryStreamReader} for subclass implementation convenience. */
public abstract class AbstractStreamReader implements FuryStreamReader {
  @Override
  public int fillBuffer(int minFillSize) {
    return 0;
  }

  @Override
  public void readTo(byte[] dst, int dstIndex, int length) {}

  @Override
  public void readToUnsafe(Object target, long targetPointer, int numBytes) {}

  @Override
  public void readToByteBuffer(ByteBuffer dst, int length) {}

  @Override
  public int readToByteBuffer(ByteBuffer dst) {
    return 0;
  }

  @Override
  public MemoryBuffer getBuffer() {
    return null;
  }
}
