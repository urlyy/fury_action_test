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

package org.apache.fury.exception;

import java.util.List;

public class DeserializationException extends FuryException {

  private List<Object> readObjects;

  public DeserializationException(String message) {
    super(message);
  }

  public DeserializationException(Throwable cause) {
    super(cause);
  }

  public DeserializationException(String message, Throwable cause) {
    super(message, cause);
  }

  // if `readObjects` too big, generate message lazily to avoid big string creation cost.
  public DeserializationException(List<Object> readObjects, Throwable cause) {
    super(cause);
    this.readObjects = readObjects;
  }

  @Override
  public String getMessage() {
    if (readObjects == null) {
      return super.getMessage();
    } else {
      return "Deserialize failed, read objects are: " + readObjects;
    }
  }
}
