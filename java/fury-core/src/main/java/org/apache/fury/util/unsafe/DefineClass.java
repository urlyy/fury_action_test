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

package org.apache.fury.util.unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import org.apache.fury.annotation.Internal;
import org.apache.fury.memory.Platform;
import org.apache.fury.util.Preconditions;

/** A class to define bytecode as a class. */
@Internal
public class DefineClass {
  private static volatile MethodHandle classloaderDefineClassHandle;

  public static Class<?> defineClass(
      String className,
      Class<?> neighbor,
      ClassLoader loader,
      ProtectionDomain domain,
      byte[] bytecodes) {
    Preconditions.checkNotNull(loader);
    Preconditions.checkArgument(Platform.JAVA_VERSION >= 8);
    if (neighbor != null && Platform.JAVA_VERSION >= 9) {
      // classes in bytecode must be in same package as lookup class.
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      _JDKAccess.addReads(_JDKAccess.getModule(DefineClass.class), _JDKAccess.getModule(neighbor));
      lookup = _Lookup.privateLookupIn(neighbor, lookup);
      return _Lookup.defineClass(lookup, bytecodes);
    }
    if (classloaderDefineClassHandle == null) {
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(ClassLoader.class);
      try {
        classloaderDefineClassHandle =
            lookup.findVirtual(
                ClassLoader.class,
                "defineClass",
                MethodType.methodType(
                    Class.class,
                    String.class,
                    byte[].class,
                    int.class,
                    int.class,
                    ProtectionDomain.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      return (Class<?>)
          classloaderDefineClassHandle.invokeWithArguments(
              loader, className, bytecodes, 0, bytecodes.length, domain);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
