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

package org.apache.fury.serializer.test;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import lombok.Data;
import org.apache.fury.util.Preconditions;

/**
 * Package-private class for testing {@link org.apache.fury.serializer.ExternalizableSerializer}
 * when the serialized class is inaccessible to the serializer.
 */
@Data
class Inaccessible implements Externalizable {
  private int x;
  private int y;
  private byte[] bytes;

  static Inaccessible create(int x, int y, byte[] bytes) {
    Inaccessible i = new Inaccessible();
    i.x = x;
    i.y = y;
    i.bytes = bytes;
    return i;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(x);
    out.writeInt(y);
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    this.x = in.readInt();
    this.y = in.readInt();
    int len = in.readInt();
    byte[] arr = new byte[len];
    Preconditions.checkArgument(in.read(arr) == len);
    this.bytes = arr;
  }
}
