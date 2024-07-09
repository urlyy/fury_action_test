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

package org.apache.fury.reflect;

import static org.apache.fury.type.TypeUtils.OBJECT_TYPE;
import static org.apache.fury.type.TypeUtils.getRawType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.fury.annotation.CodegenInvoke;
import org.apache.fury.annotation.Internal;
import org.apache.fury.collection.Tuple3;
import org.apache.fury.memory.Platform;
import org.apache.fury.util.GraalvmSupport;
import org.apache.fury.util.Preconditions;
import org.apache.fury.util.StringUtils;
import org.apache.fury.util.function.Functions;
import org.apache.fury.util.unsafe._JDKAccess;

/** Reflection util. */
@Internal
@SuppressWarnings({"unchecked", "rawtypes"})
public class ReflectionUtils {
  public static boolean isAbstract(Class<?> clazz) {
    if (clazz.isArray()) {
      return false;
    }
    return Modifier.isAbstract(clazz.getModifiers());
  }

  public static boolean hasNoArgConstructor(Class<?> clazz) {
    return getNoArgConstructor(clazz) != null;
  }

  public static boolean hasPublicNoArgConstructor(Class<?> clazz) {
    Constructor<?> constructor = getNoArgConstructor(clazz);
    return constructor != null && Modifier.isPublic(constructor.getModifiers());
  }

  static <T> Constructor<T> getNoArgConstructor(Class<T> clazz) {
    if (clazz.isInterface()) {
      return null;
    }
    if (Modifier.isAbstract(clazz.getModifiers())) {
      return null;
    }
    Constructor[] constructors = clazz.getDeclaredConstructors();
    if (constructors.length == 0) {
      return null;
    } else {
      return Stream.of(constructors)
          .filter((c) -> c.getParameterCount() == 0)
          .findAny()
          .orElse(null);
    }
  }

  private static final ClassValue<MethodHandle> ctrHandleCache =
      new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
          return createNoArgCtrHandle(type);
        }
      };

  private static MethodHandle createNoArgCtrHandle(Class<?> cls) {
    Constructor<?> ctr = getNoArgConstructor(cls);
    if (ctr == null) {
      return null;
    }
    MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(ctr.getDeclaringClass());
    try {
      return lookup.findConstructor(ctr.getDeclaringClass(), MethodType.methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      Platform.throwException(e);
      throw new IllegalStateException("unreachable");
    }
  }

  /**
   * Returns no-arg constructor handle for provided class. Returns null if class doesn't have a
   * no-arg constructor if `checked` not enabled, throws exception if `check` enabled.
   */
  public static MethodHandle getCtrHandle(Class<?> cls, boolean checked) {
    MethodHandle methodHandle = ctrHandleCache.get(cls);
    if (checked && methodHandle == null) {
      throw new RuntimeException(String.format("Class %s doesn't have a no-arg constructor", cls));
    }
    return methodHandle;
  }

  private static final ClassValue<ConcurrentMap<List<Class<?>>, MethodHandle>>
      ctrHandleParamsCache =
          new ClassValue<ConcurrentMap<List<Class<?>>, MethodHandle>>() {
            @Override
            protected ConcurrentMap<List<Class<?>>, MethodHandle> computeValue(Class<?> type) {
              return new ConcurrentHashMap<>();
            }
          };

  public static MethodHandle getCtrHandle(Class<?> cls, Class<?>... types) {
    MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(cls);
    ConcurrentMap<List<Class<?>>, MethodHandle> map = ctrHandleParamsCache.get(cls);
    return map.computeIfAbsent(
        Arrays.asList(types),
        k -> {
          try {
            return lookup.findConstructor(cls, MethodType.methodType(void.class, types));
          } catch (NoSuchMethodException | IllegalAccessException e) {
            Platform.throwException(e);
            throw new IllegalStateException("unreachable");
          }
        });
  }

  /**
   * Returns all methods named by {@code methodName}, for covariant return type, return the most
   * specific method.
   */
  public static List<Method> findMethods(Class<?> cls, String methodName) {
    List<Class<?>> classes = new ArrayList<>();
    Class<?> clazz = cls;
    while (clazz != null) {
      classes.add(clazz);
      clazz = clazz.getSuperclass();
    }
    classes.addAll(getAllInterfaces(cls));
    if (classes.indexOf(Object.class) == -1) {
      classes.add(Object.class);
    }

    LinkedHashMap<List<Class<?>>, Method> methods = new LinkedHashMap<>();
    for (Class<?> superClz : classes) {
      for (Method m : superClz.getDeclaredMethods()) {
        if (m.getName().equals(methodName)) {
          List<Class<?>> params = Arrays.asList(m.getParameterTypes());
          Method method = methods.get(params);
          if (method == null) {
            methods.put(params, m);
          } else {
            // for covariant return type, use the most specific method
            if (method.getReturnType().isAssignableFrom(m.getReturnType())) {
              methods.put(params, m);
            }
          }
        }
      }
    }
    return new ArrayList<>(methods.values());
  }

  /**
   * Gets a <code>List</code> of all interfaces implemented by the given class and its superclasses.
   *
   * <p>The order is determined by looking through each interface in turn as declared in the source
   * file and following its hierarchy up.
   */
  public static List<Class<?>> getAllInterfaces(Class<?> cls) {
    if (cls == null) {
      return null;
    }

    LinkedHashSet<Class<?>> interfacesFound = new LinkedHashSet<>();
    getAllInterfaces(cls, interfacesFound);
    return new ArrayList<>(interfacesFound);
  }

  private static void getAllInterfaces(Class<?> cls, LinkedHashSet<Class<?>> interfacesFound) {
    while (cls != null) {
      Class[] interfaces = cls.getInterfaces();
      for (Class anInterface : interfaces) {
        if (!interfacesFound.contains(anInterface)) {
          interfacesFound.add(anInterface);
          getAllInterfaces(anInterface, interfacesFound);
        }
      }

      cls = cls.getSuperclass();
    }
  }

  /** Get all classes from leaf to {@link Object}. */
  public static List<Class<?>> getAllClasses(Class<?> cls) {
    List<Class<?>> classes = new ArrayList<>();
    Class<?> clz = cls;
    while (clz != null) {
      classes.add(clz);
      clz = clz.getSuperclass();
    }
    return classes;
  }

  public static List<Class<?>> getAllClasses(Class<?> cls, boolean topToLeaf) {
    List<Class<?>> classes = getAllClasses(cls);
    if (topToLeaf) {
      Collections.reverse(classes);
    }
    return classes;
  }

  /** Returns true if any method named {@code methodName} has exception. */
  public static boolean hasException(Class<?> cls, String methodName) {
    List<Method> methods = findMethods(cls, methodName);
    if (methods.isEmpty()) {
      String msg = String.format("class %s doesn't have method %s", cls, methodName);
      throw new IllegalArgumentException(msg);
    }
    return methods.get(0).getExceptionTypes().length > 0;
  }

  /** Returns true if any method named {@code methodName} has checked exception. */
  public static boolean hasCheckedException(Class<?> cls, String methodName) {
    List<Method> methods = findMethods(cls, methodName);
    if (methods.isEmpty()) {
      String msg = String.format("class %s doesn't have method %s", cls, methodName);
      throw new IllegalArgumentException(msg);
    }
    for (Class<?> exceptionType : methods.get(0).getExceptionTypes()) {
      if (!RuntimeException.class.isAssignableFrom(exceptionType)) {
        return true;
      }
    }
    return false;
  }

  public static Class<?> getReturnType(Class<?> cls, String methodName) {
    List<Method> methods = findMethods(cls, methodName);
    if (methods.isEmpty()) {
      String msg = String.format("class %s doesn't have method %s", cls, methodName);
      throw new IllegalArgumentException(msg);
    }
    Set<? extends Class<?>> returnTypes =
        methods.stream().map(Method::getReturnType).collect(Collectors.toSet());
    Preconditions.checkArgument(returnTypes.size() == 1);
    return methods.get(0).getReturnType();
  }

  public static boolean hasDeclaredField(Class<?> cls, String fieldName) {
    try {
      cls.getDeclaredField(fieldName);
      return true;
    } catch (NoSuchFieldException e) {
      return false;
    }
  }

  public static Field getDeclaredField(Class<?> cls, String fieldName) {
    try {
      return cls.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Platform.throwException(e);
      throw new IllegalStateException("Unreachable");
    }
  }

  /**
   * Return a field named <code>fieldName</code> from <code>cls</code>. Search parent class if not
   * found.
   */
  public static Field getField(Class<?> cls, String fieldName) {
    Field field = getFieldNullable(cls, fieldName);
    if (field == null) {
      String msg = String.format("class %s doesn't have field %s", cls, fieldName);
      throw new IllegalArgumentException(msg);
    }
    return field;
  }

  /**
   * Gets the only field in the class matching the required type.
   *
   * @param clazz the class in which the field should be declared
   * @param fieldType the required type of the field
   * @return the field with specified type
   * @throws IllegalStateException if there are multiple fields of the required type
   * @throws IllegalArgumentException if there are no fields of the required type
   */
  public static Field getField(Class<?> clazz, Class<?> fieldType) {
    Field f = null;
    Class<?> cls = clazz;
    while (cls != null) {
      for (Field fi : cls.getDeclaredFields()) {
        if (fieldType.equals(fi.getType())) {
          if (f != null) {
            throw new IllegalStateException(
                "Found multiple field s matching "
                    + fieldType
                    + " in "
                    + cls
                    + ": "
                    + f
                    + " and "
                    + fi);
          }
          f = fi;
        }
      }
      cls = cls.getSuperclass();
    }
    if (f == null) {
      throw new IllegalArgumentException(
          "Found no field matching " + fieldType.getName() + " in " + clazz + "!");
    }
    return f;
  }

  public static Field getFieldNullable(Class<?> cls, String fieldName) {
    Class<?> clazz = cls;
    do {
      Field[] fields = clazz.getDeclaredFields();
      for (Field field : fields) {
        if (field.getName().equals(fieldName)) {
          return field;
        }
      }
      clazz = clazz.getSuperclass();
    } while (clazz != null);
    return null;
  }

  /**
   * Get fields.
   *
   * @param cls class
   * @param searchParent true if return super classes fields.
   * @return all fields of class. And all fields of super classes in order from subclass to super
   *     classes if <code>searchParent</code> is true
   */
  public static List<Field> getFields(Class<?> cls, boolean searchParent) {
    Preconditions.checkNotNull(cls);
    List<Field> fields = new ArrayList<>();
    if (searchParent) {
      Class<?> clazz = cls;
      do {
        Collections.addAll(fields, clazz.getDeclaredFields());
        clazz = clazz.getSuperclass();
      } while (clazz != null);
    } else {
      Collections.addAll(fields, cls.getDeclaredFields());
    }
    return fields;
  }

  /** Get fields values from provided object. */
  public static List<Object> getFieldValues(Collection<Field> fields, Object o) {
    List<Object> results = new ArrayList<>(fields.size());
    for (Field field : fields) {
      // Platform.objectFieldOffset(field) can't handle primitive field.
      Object fieldValue = FieldAccessor.createAccessor(field).get(o);
      results.add(fieldValue);
    }
    return results;
  }

  public static long getFieldOffset(Field field) {
    if (GraalvmSupport.isGraalBuildtime()) {
      // See more details at
      // https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/#unsafe-memory-access
      throw new IllegalStateException(
          "Field offset will change between graalvm build time and runtime, "
              + "should bye accessed by following graalvm auto rewrite pattern.");
    }
    if (field == null) {
      return -1;
    }
    return Platform.objectFieldOffset(field);
  }

  public static long getFieldOffset(Class<?> cls, String fieldName) {
    Field field = getFieldNullable(cls, fieldName);
    return getFieldOffset(field);
  }

  public static long getFieldOffsetChecked(Class<?> cls, String fieldName) {
    long offset = getFieldOffset(cls, fieldName);
    Preconditions.checkArgument(offset != -1);
    return offset;
  }

  public static void setObjectFieldValue(Object obj, String fieldName, Object value) {
    setObjectFieldValue(obj, getField(obj.getClass(), fieldName), value);
  }

  public static void setObjectFieldValue(Object obj, Field field, Object value) {
    Preconditions.checkArgument(
        !field.getType().isPrimitive(), "Field %s is primitive type", field);
    Platform.putObject(obj, Platform.objectFieldOffset(field), value);
  }

  public static <T> T getObjectFieldValue(Object obj, Field field) {
    return (T) Platform.getObject(obj, Platform.objectFieldOffset(field));
  }

  /**
   * Returns object field value with specified name or nul if not exists.
   *
   * @throws IllegalArgumentException when field type is primitive.
   */
  public static Object getObjectFieldValue(Object obj, String fieldName) {
    Class<?> cls = obj.getClass();
    Preconditions.checkArgument(!cls.isPrimitive());
    while (cls != Object.class) {
      try {
        Field field = cls.getDeclaredField(fieldName);
        long fieldOffset = Platform.objectFieldOffset(field);
        return Platform.getObject(obj, fieldOffset);
        // CHECKSTYLE.OFF:EmptyCatchBlock
      } catch (NoSuchFieldException ignored) {
      }
      // CHECKSTYLE.ON:EmptyCatchBlock
      cls = cls.getSuperclass();
    }
    return null;
  }

  /**
   * Get classname with package name stripped. Note that this is different from {@link
   * Class#getSimpleName()} since it return className without enclosing classname for inner classes.
   */
  public static String getClassNameWithoutPackage(Class<?> clz) {
    String className = clz.getName();
    int index = className.lastIndexOf(".");
    if (index != -1) {
      return className.substring(index + 1);
    } else {
      return className;
    }
  }

  /**
   * Get classname with package name stripped. Note that this is different from {@link
   * Class#getSimpleName()} since it return className without enclosing classname for inner classes.
   */
  public static String getClassNameWithoutPackage(String className) {
    int index = className.lastIndexOf(".");
    if (index != -1) {
      return className.substring(index + 1);
    } else {
      return className;
    }
  }

  public static boolean isPublic(TypeRef<?> targetType) {
    return Modifier.isPublic(getRawType(targetType).getModifiers());
  }

  public static boolean isPublic(Class<?> type) {
    return Modifier.isPublic(type.getModifiers());
  }

  public static boolean isPrivate(TypeRef<?> targetType) {
    return Modifier.isPrivate(getRawType(targetType).getModifiers());
  }

  public static boolean isPrivate(Class<?> cls) {
    return Modifier.isPrivate(cls.getModifiers());
  }

  /** Returns true if this type can be assigned from other types. */
  public static boolean isPolyMorphic(Class<?> targetType) {
    if (targetType.isArray()) {
      return isPolyMorphic(targetType.getComponentType());
    }
    return !Modifier.isFinal(targetType.getModifiers());
  }

  /** Returns true if this type can't be assigned from other types. */
  public static boolean isMonomorphic(Class<?> targetType) {
    if (targetType.isArray()) {
      return isMonomorphic(targetType.getComponentType());
    }
    // Although enum itself can be non-final, but it's subclass can only be anonymous
    // inner class, which is final, and we serialize enum by value, which already
    // identify the actual class, so we can take enum as monomorphic.
    return targetType.isEnum() || Modifier.isFinal(targetType.getModifiers());
  }

  public static TypeRef getPublicSuperType(TypeRef typeRef) {
    if (!isPublic(typeRef)) {
      Class<?> rawType = Objects.requireNonNull(getRawType(typeRef));
      Class<?> cls = rawType;
      while (cls != null && !isPublic(cls)) {
        cls = cls.getSuperclass();
      }
      if (cls == null) {
        for (Class<?> typeInterface : rawType.getInterfaces()) {
          if (isPublic(typeInterface)) {
            return TypeRef.of(typeInterface);
          }
        }
        return OBJECT_TYPE;
      } else {
        return TypeRef.of(cls);
      }
    } else {
      return typeRef;
    }
  }

  /** Returns package of a class. If package is absent, return empty string. */
  public static String getPackage(Class<?> cls) {
    String pkg;
    // Janino generated class's package might be null
    if (cls.getPackage() == null) {
      String className = cls.getName();
      return getPackage(className);
    } else {
      pkg = cls.getPackage().getName();
    }
    return pkg;
  }

  public static String getPackage(String className) {
    int index = className.lastIndexOf(".");
    if (index != -1) {
      return className.substring(0, index);
    } else {
      return "";
    }
  }

  /**
   * Returns the literal name of the underlying class as defined by <cite>The Java Language
   * Specification</cite>. Throw {@link IllegalArgumentException} if the underlying class does not
   * have a canonical name(i.e., if it is a local or anonymous class or an array whose component
   * type does not have a canonical name).
   *
   * @throws IllegalArgumentException if the canonical name of the underlying class doesn't exist.
   */
  public static String getLiteralName(Class<?> cls) {
    String canonicalName = cls.getCanonicalName();
    org.apache.fury.util.Preconditions.checkArgument(
        canonicalName != null, "Class %s doesn't have canonical name", cls);
    if (canonicalName.endsWith(".")) {
      // qualifier name of scala object type will ends with `.`
      String name = cls.getName();
      canonicalName = name.substring(0, name.length() - 1).replace("$", ".") + "$";
    }
    return canonicalName;
  }

  public static String getSimpleClassName(String className) {
    int index = className.lastIndexOf(".");
    if (index != -1) {
      return className.substring(index + 1);
    } else {
      return className;
    }
  }

  public static String getFullClassName(String pkg, String className) {
    if (StringUtils.isBlank(pkg)) {
      return className;
    }
    return pkg + "." + className;
  }

  @CodegenInvoke
  public static Class<?> loadClass(Class<?> neighbor, String className) {
    try {
      if (className.equals(neighbor.getName())) {
        return neighbor;
      }
      ClassLoader classLoader = neighbor.getClassLoader();
      if (classLoader == null) {
        // jdk class are loaded by bootstrap class loader, which will return null.
        classLoader = Thread.currentThread().getContextClassLoader();
      }
      return Class.forName(className, false, classLoader);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      try {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
          return loader.loadClass(className);
        }
      } catch (ClassNotFoundException ex) {
        throw new RuntimeException(ex);
      }
      throw new RuntimeException(e);
    }
  }

  public static <T> T unsafeCopy(T obj) {
    @SuppressWarnings("unchecked")
    T newInstance = (T) Platform.newInstance(obj.getClass());
    for (Field field : getFields(obj.getClass(), true)) {
      if (!Modifier.isStatic(field.getModifiers())) {
        // Don't cache accessors by `obj.getClass()` using WeakHashMap, the `field` will reference
        // `class`, which cause circular reference.
        FieldAccessor accessor = FieldAccessor.createAccessor(field);
        accessor.set(newInstance, accessor.get(obj));
      }
    }
    return newInstance;
  }

  public static void unsafeCopy(Object from, Object to) {
    Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> commonFieldsInfo =
        getCommonFields(from.getClass(), to.getClass());
    Map<String, Field> fieldMap1 = commonFieldsInfo.f1;
    Map<String, Field> fieldMap2 = commonFieldsInfo.f2;
    for (String commonField : commonFieldsInfo.f0) {
      Field field1 = fieldMap1.get(commonField);
      Field field2 = fieldMap2.get(commonField);
      FieldAccessor accessor1 = FieldAccessor.createAccessor(field1);
      FieldAccessor accessor2 = FieldAccessor.createAccessor(field2);
      accessor2.set(to, accessor1.get(from));
    }
  }

  public static boolean objectFieldsEquals(Object o1, Object o2) {
    List<Field> fields1 = getFields(o1.getClass(), true);
    List<Field> fields2 = getFields(o2.getClass(), true);
    if (fields1.size() != fields2.size()) {
      return false;
    }
    Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> commonFieldsInfo =
        getCommonFields(o1.getClass(), o2.getClass());
    if (commonFieldsInfo.f1.size() != fields1.size()) {
      return false;
    }
    if (commonFieldsInfo.f1.size() != commonFieldsInfo.f2.size()) {
      return false;
    }
    return objectCommonFieldsEquals(commonFieldsInfo, o1, o2);
  }

  public static boolean objectFieldsEquals(Set<String> fields, Object o1, Object o2) {
    Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> commonFieldsInfo =
        getCommonFields(o1.getClass(), o2.getClass());
    Map<String, Field> map1 =
        commonFieldsInfo.f1.entrySet().stream()
            .filter(e -> fields.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, Field> map2 =
        commonFieldsInfo.f2.entrySet().stream()
            .filter(e -> fields.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return objectCommonFieldsEquals(Tuple3.of(fields, map1, map2), o1, o2);
  }

  public static boolean objectCommonFieldsEquals(Object o1, Object o2) {
    Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> commonFieldsInfo =
        getCommonFields(o1.getClass(), o2.getClass());
    return objectCommonFieldsEquals(commonFieldsInfo, o1, o2);
  }

  private static boolean objectCommonFieldsEquals(
      Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> commonFieldsInfo,
      Object o1,
      Object o2) {

    for (String commonField : commonFieldsInfo.f0) {
      Field field1 = commonFieldsInfo.f1.get(commonField);
      Field field2 = commonFieldsInfo.f2.get(commonField);
      FieldAccessor accessor1 = FieldAccessor.createAccessor(field1);
      FieldAccessor accessor2 = FieldAccessor.createAccessor(field2);
      Object f1 = accessor1.get(o1);
      Object f2 = accessor2.get(o2);
      if (f1 == null) {
        if (f2 != null) {
          return false;
        }
      } else {
        if (field1.getType().isArray()) {
          if (field1.getType() == boolean[].class) {
            if (!Arrays.equals((boolean[]) f1, (boolean[]) f2)) {
              return false;
            }
          } else if (field1.getType() == byte[].class) {
            if (!Arrays.equals((byte[]) f1, (byte[]) f2)) {
              return false;
            }
          } else if (field1.getType() == short[].class) {
            if (!Arrays.equals((short[]) f1, (short[]) f2)) {
              return false;
            }
          } else if (field1.getType() == char[].class) {
            if (!Arrays.equals((char[]) f1, (char[]) f2)) {
              return false;
            }
          } else if (field1.getType() == int[].class) {
            if (!Arrays.equals((int[]) f1, (int[]) f2)) {
              return false;
            }
          } else if (field1.getType() == long[].class) {
            if (!Arrays.equals((long[]) f1, (long[]) f2)) {
              return false;
            }
          } else if (field1.getType() == float[].class) {
            if (!Arrays.equals((float[]) f1, (float[]) f2)) {
              return false;
            }
          } else if (field1.getType() == double[].class) {
            if (!Arrays.equals((double[]) f1, (double[]) f2)) {
              return false;
            }
          } else {
            if (!Arrays.deepEquals((Object[]) f1, (Object[]) f2)) {
              return false;
            }
          }
        } else {
          if (!f1.equals(f2)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public static Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> getCommonFields(
      Class<?> cls1, Class<?> cls2) {
    List<Field> fields1 = getFields(cls1, true);
    List<Field> fields2 = getFields(cls2, true);
    return getCommonFields(fields1, fields2);
  }

  public static Tuple3<Set<String>, Map<String, Field>, Map<String, Field>> getCommonFields(
      List<Field> fields1, List<Field> fields2) {
    Map<String, Field> fieldMap1 =
        fields1.stream()
            .collect(
                Collectors.toMap(
                    // don't use `getGenericType` since janino doesn't support generics.
                    f -> f.getDeclaringClass().getSimpleName() + f.getType() + f.getName(),
                    f -> f));
    Map<String, Field> fieldMap2 =
        fields2.stream()
            .collect(
                Collectors.toMap(
                    f -> f.getDeclaringClass().getSimpleName() + f.getType() + f.getName(),
                    f -> f));
    Set<String> commonFields = fieldMap1.keySet();
    commonFields.retainAll(fieldMap2.keySet());
    return Tuple3.of(commonFields, fieldMap1, fieldMap2);
  }

  public static boolean isJdkProxy(Class<?> clz) {
    return Proxy.isProxyClass(clz);
  }

  public static boolean isDynamicGeneratedCLass(Class<?> cls) {
    // TODO(chaokunyang) add cglib check
    return Functions.isLambda(cls) || isJdkProxy(cls);
  }

  /** Returns true if a class is a scala `object` singleton. */
  public static boolean isScalaSingletonObject(Class<?> cls) {
    try {
      cls.getDeclaredField("MODULE$");
      return true;
    } catch (NoSuchFieldException e) {
      return false;
    }
  }
}
