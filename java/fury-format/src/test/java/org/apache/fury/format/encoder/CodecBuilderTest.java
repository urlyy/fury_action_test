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

package org.apache.fury.format.encoder;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.fury.test.bean.BeanA;
import org.apache.fury.test.bean.BeanB;
import org.apache.fury.test.bean.Foo;
import org.testng.annotations.Test;

public class CodecBuilderTest {
  @Test
  public void genCode() {
    new RowEncoderBuilder(Foo.class).genCode();
    new RowEncoderBuilder(BeanA.class).genCode();
    new RowEncoderBuilder(BeanB.class).genCode();
  }

  @Test
  public void loadOrGenRowCodecClass() {
    Class<?> codecClass = Encoders.loadOrGenRowCodecClass(BeanA.class);
    assertTrue(GeneratedRowEncoder.class.isAssignableFrom(codecClass));
    assertTrue(
        GeneratedRowEncoder.class.isAssignableFrom(Encoders.loadOrGenRowCodecClass(BeanB.class)));
    assertTrue(
        GeneratedRowEncoder.class.isAssignableFrom(
            Encoders.loadOrGenRowCodecClass(AtomicLong.class)));
  }
}
