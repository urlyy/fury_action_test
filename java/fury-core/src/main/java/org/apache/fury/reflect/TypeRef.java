/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.fury.reflect;

import static org.apache.fury.reflect.Types.asTypeVariableKeyOrNull;
import static org.apache.fury.reflect.Types.newArrayType;
import static org.apache.fury.reflect.Types.typeVariablesEquals;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.fury.type.TypeUtils;

// Mostly derived from Guava 32.1.2 com.google.common.reflect.TypeToken
// https://github.com/google/guava/blob/9f6a3840/guava/src/com/google/common/reflect/TypeToken.java
public class TypeRef<T> {

  private final Type type;
  private transient Class<? super T> rawType;
  private transient Map<Types.TypeVariableKey, Type> typeMappings;

  /**
   * Constructs a new type token of {@code T}.
   *
   * <p>Clients create an empty anonymous subclass. This embeds the type parameter in the anonymous
   * class's type hierarchy, so we can reconstitute it at runtime despite erasure.
   *
   * <p>For example:
   *
   * <pre>{@code
   * TypeRef<List<String>> t = new TypeRef<List<String>>() {};
   * }</pre>
   */
  protected TypeRef() {
    this.type = capture();
  }

  private TypeRef(Class<T> declaringClass) {
    this.type = declaringClass;
  }

  private TypeRef(Type type) {
    this.type = type;
  }

  /** Returns an instance of type token that wraps {@code type}. */
  public static <T> TypeRef<T> of(Class<T> clazz) {
    return new TypeRef<>(clazz);
  }

  /** Returns an instance of type token that wraps {@code type}. */
  public static <T> TypeRef<T> of(Type type) {
    return new TypeRef<>(type);
  }

  /** Returns the captured type. */
  private Type capture() {
    final Type superclass = getClass().getGenericSuperclass();
    if (!(superclass instanceof ParameterizedType)) {
      throw new IllegalArgumentException(superclass + " isn't parameterized");
    }
    return ((ParameterizedType) superclass).getActualTypeArguments()[0];
  }

  /** Returns the represented type. */
  public Type getType() {
    return type;
  }

  /**
   * Returns the raw type of {@code T}. Formally speaking, if {@code T} is returned by {@link
   * java.lang.reflect.Method#getGenericReturnType}, the raw type is what's returned by {@link
   * java.lang.reflect.Method#getReturnType} of the same method object. Specifically:
   *
   * <ul>
   *   <li>If {@code T} is a {@code Class} itself, {@code T} itself is returned.
   *   <li>If {@code T} is a {@link ParameterizedType}, the raw type of the parameterized type is
   *       returned.
   *   <li>If {@code T} is a {@link GenericArrayType}, the returned type is the corresponding array
   *       class. For example: {@code List<Integer>[] => List[]}.
   *   <li>If {@code T} is a type variable or a wildcard type, the raw type of the first upper bound
   *       is returned. For example: {@code <X extends Foo> => Foo}.
   * </ul>
   */
  public Class<? super T> getRawType() {
    Class<? super T> cachedRawType = rawType;
    if (cachedRawType != null) {
      return cachedRawType;
    }
    @SuppressWarnings("unchecked")
    Class<? super T> rawType = (Class<? super T>) TypeUtils.getRawType(type);
    this.rawType = rawType;
    return rawType;
  }

  private static Stream<Class<?>> getRawTypes(Type... types) {
    return Arrays.stream(types)
        .flatMap(
            type -> {
              if (type instanceof TypeVariable) {
                return getRawTypes(((TypeVariable<?>) type).getBounds());
              } else if (type instanceof WildcardType) {
                return getRawTypes(((WildcardType) type).getUpperBounds());
              } else if (type instanceof ParameterizedType) {
                return Stream.of((Class<?>) ((ParameterizedType) type).getRawType());
              } else if (type instanceof Class) {
                return Stream.of((Class<?>) type);
              } else if (type instanceof GenericArrayType) {
                Class<?> rawType =
                    getArrayClass(
                        of(((GenericArrayType) type).getGenericComponentType()).getRawType());
                return Stream.of(rawType);
              } else {
                throw new AssertionError("Unknown type: " + type);
              }
            });
  }

  /** Returns true if this type is one of the primitive types (including {@code void}). */
  public boolean isPrimitive() {
    return type instanceof Class && ((Class<?>) type).isPrimitive();
  }

  /**
   * Returns true if this type is known to be an array type, such as {@code int[]}, {@code T[]},
   * {@code <? extends Map<String, Integer>[]>} etc.
   */
  public boolean isArray() {
    return getComponentType(type) != null;
  }

  /**
   * Returns the array component type if this type represents an array ({@code int[]}, {@code T[]},
   * {@code <? extends Map<String, Integer>[]>} etc.), or else {@code null} is returned.
   */
  public TypeRef<?> getComponentType() {
    return of(getComponentType(type));
  }

  /**
   * Returns the array component type if this type represents an array ({@code int[]}, {@code T[]},
   * {@code <? extends Map<String, Integer>[]>} etc.), or else {@code null} is returned.
   */
  private static Type getComponentType(Type type) {
    if (type == null) {
      return null;
    }
    if (type instanceof TypeVariable) {
      return subtypeOfComponentType(((TypeVariable<?>) type).getBounds());
    } else if (type instanceof WildcardType) {
      return subtypeOfComponentType(((WildcardType) type).getUpperBounds());
    } else if (type instanceof Class) {
      return ((Class<?>) type).getComponentType();
    } else if (type instanceof GenericArrayType) {
      return ((GenericArrayType) type).getGenericComponentType();
    }
    return null;
  }

  /**
   * Returns {@code ? extends X} if any of {@code bounds} is a subtype of {@code X[]}; or null
   * otherwise.
   */
  private static Type subtypeOfComponentType(Type[] bounds) {
    for (Type bound : bounds) {
      final Type componentType = getComponentType(bound);
      if (componentType != null) {
        // Only the first bound can be a class or array.
        // Bounds after the first can only be interfaces.
        if (componentType instanceof Class) {
          final Class<?> componentClass = (Class<?>) componentType;
          if (componentClass.isPrimitive()) {
            return componentClass;
          }
        }
        return componentType;
      }
    }
    return null;
  }

  /**
   * Resolves the given {@code type} against the type context represented by this type. For example:
   *
   * <pre>{@code
   * new TypeRef<List<String>>() {}.resolveType(
   *     List.class.getMethod("get", int.class).getGenericReturnType())
   * => String.class
   * }</pre>
   */
  public TypeRef<?> resolveType(Type iteratorReturnType) {
    if (iteratorReturnType instanceof WildcardType) { // fast path
      return of(iteratorReturnType);
    }
    Type invariantContext = WildcardCapturer.capture(type);
    Map<Types.TypeVariableKey, Type> mappings = resolveTypeMappings(invariantContext);
    return resolveType0(iteratorReturnType, mappings);
  }

  private TypeRef<?> resolveType0(
      Type iteratorReturnType, Map<Types.TypeVariableKey, Type> mappings) {
    if (iteratorReturnType instanceof TypeVariable) {
      TypeVariable<?> typeVariable = (TypeVariable<?>) iteratorReturnType;
      Type type = mappings.get(new Types.TypeVariableKey(typeVariable));
      if (type == null) {
        return of(typeVariable);
      }
      return resolveType0(type, mappings);
    } else if (iteratorReturnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) iteratorReturnType;
      Type owner = parameterizedType.getOwnerType();
      Type resolvedOwner = owner == null ? null : resolveType0(owner, mappings).type;
      Type resolvedRawType = resolveType0(parameterizedType.getRawType(), mappings).type;

      Type[] args = parameterizedType.getActualTypeArguments();

      Type[] resolvedArgs = new Type[args.length];
      for (int i = 0; i < args.length; i++) {
        resolvedArgs[i] = resolveType0(args[i], mappings).type;
      }

      return of(new Types.ParameterizedTypeImpl(resolvedOwner, resolvedRawType, resolvedArgs));
    } else if (iteratorReturnType instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) iteratorReturnType).getGenericComponentType();
      Type resolvedComponentType = resolveType0(componentType, mappings).type;
      return of(newArrayType(resolvedComponentType));
    }
    return of(iteratorReturnType);
  }

  private Map<Types.TypeVariableKey, Type> resolveTypeMappings() {
    Map<Types.TypeVariableKey, Type> cachedMappings = this.typeMappings;
    if (cachedMappings != null) {
      return cachedMappings;
    }
    Map<Types.TypeVariableKey, Type> typeMappings = resolveTypeMappings(type);
    this.typeMappings = typeMappings;
    return typeMappings;
  }

  private static Map<Types.TypeVariableKey, Type> resolveTypeMappings(Type contextType) {
    Map<Types.TypeVariableKey, Type> result = new HashMap<>();
    populateTypeMapping(result, contextType);
    return result;
  }

  private static void populateTypeMapping(Map<Types.TypeVariableKey, Type> storage, Type... types) {
    for (Type type : types) {
      if (type == null) {
        continue;
      }

      if (type instanceof TypeVariable) {
        populateTypeMapping(storage, ((TypeVariable<?>) type).getBounds());
      } else if (type instanceof WildcardType) {
        populateTypeMapping(storage, ((WildcardType) type).getUpperBounds());
      } else if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;

        Class<?> rawClass = (Class<?>) parameterizedType.getRawType();
        TypeVariable<?>[] vars = rawClass.getTypeParameters();
        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        storageFiller:
        for (int i = 0; i < vars.length; i++) {
          TypeVariable<?> typeVariable = vars[i];
          Types.TypeVariableKey key = new Types.TypeVariableKey(typeVariable);
          if (storage.containsKey(key)) {
            continue;
          }
          Type arg = typeArgs[i];
          // First, check whether var -> arg forms a cycle
          for (Type t = arg; t != null; t = storage.get(asTypeVariableKeyOrNull(t))) {
            if (typeVariablesEquals(typeVariable, t)) {
              // cycle detected, remove the entire cycle from the mapping so that
              // each type variable resolves deterministically to itself.
              // Otherwise, an F -> T cycle will end up resolving both F and T
              // nondeterministically to either F or T.
              Type x = arg;
              while (x != null) {
                x = storage.remove(asTypeVariableKeyOrNull(x));
              }
              break storageFiller;
            }
          }
          storage.put(key, arg);
        }
        populateTypeMapping(storage, rawClass);
        populateTypeMapping(storage, parameterizedType.getOwnerType());
      } else if (type instanceof Class) {
        Class<?> clazz = (Class<?>) type;
        populateTypeMapping(storage, clazz.getGenericSuperclass());
        populateTypeMapping(storage, clazz.getGenericInterfaces());
      } else {
        throw new AssertionError("Unknown type: " + type);
      }
    }
  }

  /**
   * Returns the generic form of {@code superclass}. For example, if this is {@code
   * ArrayList<String>}, {@code Iterable<String>} is returned given the input {@code
   * Iterable.class}.
   */
  public TypeRef<? super T> getSupertype(Class<? super T> superclass) {
    if (type instanceof TypeVariable) {
      return getSupertypeFromBounds(superclass, ((TypeVariable<?>) type).getBounds());
    }
    if (type instanceof WildcardType) {
      return getSupertypeFromBounds(superclass, ((WildcardType) type).getUpperBounds());
    }
    if (superclass.isArray()) {
      Type componentType;
      if (type instanceof Class) {
        componentType = ((Class<?>) type).getComponentType();
      } else if (type instanceof GenericArrayType) {
        componentType = ((GenericArrayType) type).getGenericComponentType();
      } else {
        throw new AssertionError("Unknown type: " + type);
      }
      if (componentType == null) {
        throw new IllegalArgumentException(superclass + " isn't a super type of " + this);
      }

      @SuppressWarnings("rawtypes")
      TypeRef componentTypeRef = of(componentType);

      @SuppressWarnings("unchecked")
      TypeRef<?> componentSupertype = componentTypeRef.getSupertype(superclass.getComponentType());

      return of(newArrayType(componentSupertype.type));
    }

    Map<Types.TypeVariableKey, Type> mappings = resolveTypeMappings();
    @SuppressWarnings("unchecked") // resolved supertype
    TypeRef<? super T> supertype =
        (TypeRef<? super T>) resolveType0(toGenericType(superclass), mappings);
    return supertype;
  }

  private static <T> Type toGenericType(Class<T> cls) {
    if (cls.isArray()) {
      return Types.newArrayType(
          // If we are passed with int[].class, don't turn it to GenericArrayType
          toGenericType(cls.getComponentType()));
    }
    TypeVariable<Class<T>>[] typeParams = cls.getTypeParameters();
    Type ownerType =
        cls.isMemberClass() && !Modifier.isStatic(cls.getModifiers())
            ? toGenericType(cls.getEnclosingClass())
            : null;
    if (typeParams.length > 0 || (ownerType != null && ownerType != cls.getEnclosingClass())) {
      return new Types.ParameterizedTypeImpl(ownerType, cls, typeParams);
    } else {
      return cls;
    }
  }

  private TypeRef<? super T> getSupertypeFromBounds(Class<? super T> superclass, Type[] bounds) {
    for (Type upperBound : bounds) {
      TypeRef<? extends T> bound = of(upperBound);
      if (bound.isSubtypeOf(superclass)) {
        @SuppressWarnings({"unchecked"}) // guarded by the isSubtypeOf check.
        TypeRef<? super T> result = (TypeRef<? super T>) bound.getSupertype(superclass);
        return result;
      }
    }
    throw new IllegalArgumentException(superclass + " isn't a super type of " + this);
  }

  /**
   * Returns subtype of {@code this} with {@code subclass} as the raw class. For example, if this is
   * {@code Iterable<String>} and {@code subclass} is {@code List}, {@code List<String>} is
   * returned.
   */
  public final TypeRef<? extends T> getSubtype(Class<?> subclass) {
    if (type instanceof WildcardType) {
      Type[] lowerBounds = ((WildcardType) type).getLowerBounds();
      if (lowerBounds.length > 0) {
        TypeRef<? extends T> bound = of(lowerBounds[0]);
        // Java supports only one lowerbound anyway.
        return bound.getSubtype(subclass);
      }
      throw new IllegalArgumentException(subclass + " isn't a subclass of " + this);
    }
    Type componentType = getComponentType(type);
    if (componentType != null) {
      Class<?> subclassComponentType = subclass.getComponentType();
      if (subclassComponentType == null) {
        throw new IllegalArgumentException(
            subclass + " does not appear to be a subtype of " + this);
      }
      // array is covariant. component type is subtype, so is the array type.
      // requireNonNull is safe because we call getArraySubtype only when isArray().
      TypeRef<?> componentSubtype = of(componentType).getSubtype(subclassComponentType);
      // If we are passed with int[].class, don't turn it to GenericArrayType
      return of(Types.newArrayType(componentSubtype.type));
    }

    Class<? super T> rawType = getRawType();
    if (!rawType.isAssignableFrom(subclass)) {
      throw new IllegalArgumentException(subclass + " isn't a subclass of " + this);
    }

    // If both runtimeType and subclass are not parameterized, return subclass
    // If runtimeType is not parameterized but subclass is, process subclass as a parameterized type
    // If runtimeType is a raw type (i.e. is a parameterized type specified as a Class<?>), we
    // return subclass as a raw type
    if (type instanceof Class
        && ((subclass.getTypeParameters().length == 0)
            || (rawType.getTypeParameters().length != 0))) {
      // no resolution needed
      @SuppressWarnings({"unchecked"}) // subclass isn't <? extends T>
      TypeRef<? extends T> result = (TypeRef<? extends T>) of(subclass);
      return result;
    }
    // class Base<A, B> {}
    // class Sub<X, Y> extends Base<X, Y> {}
    // Base<String, Integer>.subtype(Sub.class):

    // Sub<X, Y>.getSupertype(Base.class) => Base<X, Y>
    // => X=String, Y=Integer
    // => Sub<X, Y>=Sub<String, Integer>
    TypeRef<?> genericSubtype = of(toGenericType(subclass));
    @SuppressWarnings({"rawtypes", "unchecked"}) // subclass isn't <? extends T>
    Type supertypeWithArgsFromSubtype = genericSubtype.getSupertype((Class) rawType).type;

    if (genericSubtype.type instanceof WildcardType) {
      @SuppressWarnings({"unchecked"}) // subclass isn't <? extends T>
      TypeRef<? extends T> result = (TypeRef<? extends T>) genericSubtype;
      return result;
    }

    Map<Types.TypeVariableKey, Type> mappings = new HashMap<>();
    populateTypeMappings(mappings, supertypeWithArgsFromSubtype, type);

    return (TypeRef<? extends T>) resolveType0(genericSubtype.type, mappings);
  }

  private void populateTypeMappings(
      Map<Types.TypeVariableKey, Type> mappings, Type supertypeWithArgsFromSubtype, Type toType) {
    if (supertypeWithArgsFromSubtype instanceof TypeVariable) {
      Types.TypeVariableKey typeVariableKey =
          new Types.TypeVariableKey((TypeVariable<?>) supertypeWithArgsFromSubtype);
      mappings.put(typeVariableKey, toType);
    } else if (supertypeWithArgsFromSubtype instanceof WildcardType) {
      if (!(toType instanceof WildcardType)) {
        return; // okay to say <?> is anything
      }
      WildcardType supertypeWildcard = (WildcardType) supertypeWithArgsFromSubtype;
      WildcardType toWildcardType = (WildcardType) toType;
      Type[] fromUpperBounds = supertypeWildcard.getUpperBounds();
      Type[] toUpperBounds = toWildcardType.getUpperBounds();
      Type[] fromLowerBounds = supertypeWildcard.getLowerBounds();
      Type[] toLowerBounds = toWildcardType.getLowerBounds();
      for (int i = 0; i < fromUpperBounds.length; i++) {
        populateTypeMappings(mappings, fromUpperBounds[i], toUpperBounds[i]);
      }
      for (int i = 0; i < fromLowerBounds.length; i++) {
        populateTypeMappings(mappings, fromLowerBounds[i], toLowerBounds[i]);
      }
    } else if (supertypeWithArgsFromSubtype instanceof ParameterizedType) {
      if (toType instanceof WildcardType) {
        return; // Okay to say Foo<A> is <?>
      }
      ParameterizedType toParameterizedType = (ParameterizedType) toType;
      ParameterizedType supertypeParameterized = (ParameterizedType) supertypeWithArgsFromSubtype;
      if (supertypeParameterized.getOwnerType() != null
          && toParameterizedType.getOwnerType() != null) {
        populateTypeMappings(
            mappings, supertypeParameterized.getOwnerType(), toParameterizedType.getOwnerType());
      }
      Type[] fromArgs = supertypeParameterized.getActualTypeArguments();
      Type[] toArgs = toParameterizedType.getActualTypeArguments();
      for (int i = 0; i < fromArgs.length; i++) {
        populateTypeMappings(mappings, fromArgs[i], toArgs[i]);
      }
    } else if (supertypeWithArgsFromSubtype instanceof GenericArrayType) {
      if (toType instanceof WildcardType) {
        return; // Okay to say A[] is <?>
      }
      Type componentType = getComponentType(toType);
      Type fromComponentType =
          ((GenericArrayType) supertypeWithArgsFromSubtype).getGenericComponentType();
      populateTypeMappings(mappings, fromComponentType, componentType);
    } else {
      if (!(supertypeWithArgsFromSubtype instanceof Class)) {
        throw new AssertionError("Unknown type: " + toType);
      }
    }
  }

  /** Returns true if this type is a subtype of the given {@code type}. */
  public boolean isSubtypeOf(TypeRef<?> type) {
    return isSubtypeOf(type.getType());
  }

  /** Returns true if this type is a subtype of the given {@code type}. */
  public final boolean isSubtypeOf(Type supertype) {
    if (supertype instanceof WildcardType) {
      for (Type bound : ((WildcardType) supertype).getLowerBounds()) {
        if (isSubtypeOf(bound)) {
          return true;
        }
      }
      return false;
    }

    if (type instanceof WildcardType) {
      return anyTypeIsSubTypeOf(type, ((WildcardType) type).getUpperBounds());
    }

    if (type instanceof TypeVariable) {
      if (type.equals(supertype)) {
        return true;
      }

      return anyTypeIsSubTypeOf(type, ((TypeVariable<?>) type).getBounds());
    }

    if (supertype instanceof Class) {
      return anyRawTypeIsSubclassOf((Class<?>) supertype);
    }
    if (supertype instanceof ParameterizedType) {
      ParameterizedType parameterizedSuperType = (ParameterizedType) supertype;
      Class<?> matchedClass = of(parameterizedSuperType).getRawType();
      if (!anyRawTypeIsSubclassOf(matchedClass)) {
        return false;
      }
      TypeVariable<?>[] typeParameters = matchedClass.getTypeParameters();
      Type[] supertypeArgs = parameterizedSuperType.getActualTypeArguments();
      for (int i = 0; i < typeParameters.length; i++) {
        TypeVariable<?> typeParameter = typeParameters[i];

        Map<Types.TypeVariableKey, Type> mappings = resolveTypeMappings();
        TypeRef<?> subtypeParam = resolveType0(typeParameter, mappings);

        if (!subtypeParam.is(supertypeArgs[i], typeParameter)) {
          return false;
        }
      }

      if (Modifier.isStatic(((Class<?>) parameterizedSuperType.getRawType()).getModifiers())
          || parameterizedSuperType.getOwnerType() == null) {
        return true;
      }

      return collectTypes(this)
          .anyMatch(
              type -> {
                if (type.type instanceof ParameterizedType) {
                  return of(((ParameterizedType) type.type).getOwnerType()).isSubtypeOf(supertype);
                } else if (type.type instanceof Class<?>) {
                  return of(((Class<?>) type.type).getEnclosingClass()).isSubtypeOf(supertype);
                }
                return false;
              });
    }

    if (supertype instanceof GenericArrayType) {
      if (type instanceof Class) {
        Class<?> fromClass = (Class<?>) type;
        if (!fromClass.isArray()) {
          return false;
        }
        return of(fromClass.getComponentType())
            .isSubtypeOf(((GenericArrayType) supertype).getGenericComponentType());
      } else if (type instanceof GenericArrayType) {
        return of(((GenericArrayType) type).getGenericComponentType())
            .isSubtypeOf(((GenericArrayType) supertype).getGenericComponentType());
      } else {
        return false;
      }
    }

    return false;
  }

  private Stream<TypeRef<?>> collectTypes(TypeRef<?> type) {
    return Stream.of(type)
        .flatMap(
            t -> {
              Stream<TypeRef<?>> genericInterfacesTypeRefs =
                  t.getGenericInterfaces().flatMap(this::collectTypes);
              TypeRef<?> superclass = t.getGenericSuperclass();
              return superclass == null
                  ? genericInterfacesTypeRefs
                  : Stream.concat(genericInterfacesTypeRefs, collectTypes(superclass));
            });
  }

  private Stream<? extends TypeRef<?>> getGenericInterfaces() {
    if (type instanceof TypeVariable) {
      return boundsAsInterfaces(((TypeVariable<?>) type).getBounds());
    }
    if (type instanceof WildcardType) {
      return boundsAsInterfaces(((WildcardType) type).getUpperBounds());
    }
    Map<Types.TypeVariableKey, Type> mappings = resolveTypeMappings();
    return Arrays.stream(getRawType().getGenericInterfaces())
        .map(
            interfaceType -> {
              @SuppressWarnings("unchecked") // interface of T
              TypeRef<? super T> resolvedInterface =
                  (TypeRef<? super T>) resolveType0(interfaceType, mappings);
              return resolvedInterface;
            });
  }

  private TypeRef<? super T> getGenericSuperclass() {
    if (type instanceof TypeVariable) {
      // First bound is always the super class, if one exists.
      return boundAsSuperclass(((TypeVariable<?>) type).getBounds()[0]);
    }
    if (type instanceof WildcardType) {
      // wildcard has one and only one upper bound.
      return boundAsSuperclass(((WildcardType) type).getUpperBounds()[0]);
    }
    Type superclass = getRawType().getGenericSuperclass();
    if (superclass == null) {
      return null;
    }

    Map<Types.TypeVariableKey, Type> mappings = resolveTypeMappings();
    @SuppressWarnings("unchecked") // interface of T
    TypeRef<? super T> superToken = (TypeRef<? super T>) resolveType0(superclass, mappings);
    return superToken;
  }

  private TypeRef<? super T> boundAsSuperclass(Type bound) {
    TypeRef<?> token = of(bound);
    if (token.getRawType().isInterface()) {
      return null;
    }
    @SuppressWarnings("unchecked") // only upper bound of T is passed in.
    TypeRef<? super T> superclass = (TypeRef<? super T>) token;
    return superclass;
  }

  private Stream<? extends TypeRef<?>> boundsAsInterfaces(Type[] bounds) {
    return Arrays.stream(bounds)
        .map(TypeRef::of)
        .filter(boundType -> boundType.getRawType().isInterface());
  }

  private boolean is(Type formalType, TypeVariable<?> declaration) {
    if (type.equals(formalType)) {
      return true;
    }
    if (formalType instanceof WildcardType) {
      WildcardType your = canonicalizeWildcardType(declaration, (WildcardType) formalType);
      // if "formalType" is <? extends Foo>, "this" can be:
      // Foo, SubFoo, <? extends Foo>, <? extends SubFoo>, <T extends Foo> or
      // <T extends SubFoo>.
      // if "formalType" is <? super Foo>, "this" can be:
      // Foo, SuperFoo, <? super Foo> or <? super SuperFoo>.
      for (Type bound : your.getUpperBounds()) {
        if (!of(bound).isSupertypeOf(type)) {
          return false;
        }
      }
      for (Type bound : your.getLowerBounds()) {
        if (!of(bound).isSupertypeOf(type)) {
          return false;
        }
      }
      return true;
    }
    return canonicalizeWildcardsInType(type).equals(canonicalizeWildcardsInType(formalType));
  }

  private static WildcardType canonicalizeWildcardType(
      TypeVariable<?> declaration, WildcardType type) {
    Type[] declared = declaration.getBounds();
    List<Type> upperBounds = new ArrayList<>();
    upperBoundsGenerator:
    for (Type bound : type.getUpperBounds()) {
      for (Type declaredType : declared) {
        if (of(declaredType).isSubtypeOf(bound)) {
          continue upperBoundsGenerator;
        }
      }
      upperBounds.add(canonicalizeWildcardsInType(bound));
    }
    return new Types.WildcardTypeImpl(type.getLowerBounds(), upperBounds.toArray(new Type[0]));
  }

  private static Type canonicalizeWildcardsInType(Type type) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      TypeVariable<?>[] typeVars = rawType.getTypeParameters();
      Type[] typeArgs = parameterizedType.getActualTypeArguments();
      for (int i = 0; i < typeArgs.length; i++) {
        Type typeArg = typeArgs[i];
        typeArgs[i] =
            typeArg instanceof WildcardType
                ? canonicalizeWildcardType(typeVars[i], (WildcardType) typeArg)
                : canonicalizeWildcardsInType(typeArg);
      }
      return new Types.ParameterizedTypeImpl(parameterizedType.getOwnerType(), rawType, typeArgs);
    }
    if (type instanceof GenericArrayType) {
      return Types.newArrayType(
          canonicalizeWildcardsInType(((GenericArrayType) type).getGenericComponentType()));
    }
    return type;
  }

  private static boolean anyTypeIsSubTypeOf(Type upperBound, Type[] declared) {
    for (Type declaredType : declared) {
      if (of(declaredType).isSubtypeOf(upperBound)) {
        return true;
      }
    }
    return false;
  }

  private boolean anyRawTypeIsSubclassOf(Class<?> supertype) {
    return getRawTypes(type).anyMatch(supertype::isAssignableFrom);
  }

  /** Returns true if this type is a supertype of the given {@code type}. */
  public final boolean isSupertypeOf(Type type) {
    return isSupertypeOf(of(type));
  }

  /** Returns true if this type is a supertype of the given {@code type}. */
  public final boolean isSupertypeOf(TypeRef<?> type) {
    return type.isSubtypeOf(getType());
  }

  /**
   * Returns the corresponding wrapper type if this is a primitive type; otherwise returns {@code
   * this} itself.
   */
  public final TypeRef<T> wrap() {
    if (isPrimitive()) {
      @SuppressWarnings("unchecked")
      final Class<T> clazz = (Class<T>) type;
      // cast is safe: long.class and Long.class are both of type Class<Long>
      @SuppressWarnings("unchecked")
      final Class<T> wrapped = (Class<T>) TypeUtils.wrap(clazz);
      return of(wrapped);
    }
    return this;
  }

  /**
   * Returns the corresponding primitive type if this is a wrapper type; otherwise returns {@code
   * this} itself.
   */
  public final TypeRef<T> unwrap() {
    if (isWrapper()) {
      @SuppressWarnings("unchecked") // this is a wrapper class
      final Class<T> clazz = (Class<T>) type;
      // cast is safe: long.class and Long.class are both of type Class<Long>
      @SuppressWarnings("unchecked")
      final Class<T> unwrapped = (Class<T>) TypeUtils.unwrap(clazz);
      return of(unwrapped);
    }
    return this;
  }

  public <X> TypeRef<T> where(TypeParameter<X> typeParam, Class<X> typeArg) {
    return where(typeParam, of(typeArg));
  }

  public final <X> TypeRef<T> where(TypeParameter<X> typeParam, TypeRef<X> typeArg) {
    if (type instanceof WildcardType) { // fast path
      return of(type);
    }
    Types.TypeVariableKey typeVariableKey = new Types.TypeVariableKey(typeParam.typeVariable);
    @SuppressWarnings("unchecked")
    TypeRef<T> result =
        (TypeRef<T>) resolveType0(type, Collections.singletonMap(typeVariableKey, typeArg.type));
    return result;
  }

  private boolean isWrapper() {
    if (type instanceof Class<?>) {
      return TypeUtils.isBoxed((Class<?>) type);
    }
    return false;
  }

  /**
   * Returns true if {@code o} is another {@code TypeToken} that represents the same {@link Type}.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof TypeRef) {
      final TypeRef<?> that = (TypeRef<?>) o;
      return type.equals(that.type);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return (type instanceof Class) ? ((Class<?>) type).getName() : type.toString();
  }

  /** Returns the {@code Class} object of arrays with {@code componentType}. */
  private static Class<?> getArrayClass(Class<?> componentType) {
    return Array.newInstance(componentType, 0).getClass();
  }

  private static class WildcardCapturer {
    private static final WildcardCapturer INSTANCE = new WildcardCapturer();

    static Type capture(Type type) {
      return INSTANCE.capture0(type);
    }

    final Type capture0(Type type) {
      if (type instanceof Class) {
        return type;
      }
      if (type instanceof TypeVariable) {
        return type;
      }
      if (type instanceof GenericArrayType) {
        GenericArrayType arrayType = (GenericArrayType) type;
        return newArrayType(capture0(arrayType.getGenericComponentType()));
      }
      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Class<?> rawType = (Class<?>) parameterizedType.getRawType();
        TypeVariable<?>[] typeVars = rawType.getTypeParameters();
        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        for (int i = 0; i < typeArgs.length; i++) {
          typeArgs[i] = forTypeVariable(typeVars[i]).capture0(typeArgs[i]);
        }
        Type ownerType = parameterizedType.getOwnerType();
        return new Types.ParameterizedTypeImpl(
            ownerType == null ? null : capture0(ownerType), rawType, typeArgs);
      }
      if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;
        Type[] lowerBounds = wildcardType.getLowerBounds();
        return lowerBounds.length == 0 // ? extends something changes to capture-of
            ? captureAsTypeVariable(wildcardType.getUpperBounds())
            : type;
      }
      throw new AssertionError("must have been one of the known types");
    }

    TypeVariable<?> captureAsTypeVariable(Type[] upperBounds) {
      String name =
          "capture of ? extends "
              + Stream.of(upperBounds).map(Type::toString).collect(Collectors.joining("&"));
      return new Types.TypeVariableImpl<>(WildcardCapturer.class, name, upperBounds);
    }

    private WildcardCapturer forTypeVariable(TypeVariable<?> typeParam) {
      return new WildcardCapturer() {
        @Override
        TypeVariable<?> captureAsTypeVariable(Type[] upperBounds) {
          // Since this is an artificially generated type variable, we don't bother checking
          // subtyping between declared type bound and actual type bound. So it's possible that we
          // may generate something like <capture#1-of ? extends Foo&SubFoo>.
          // Checking subtype between declared and actual type bounds
          // adds recursive isSubtypeOf() call and feels complicated.
          // There is no contract one way or another as long as isSubtypeOf() works as expected.
          Type[] typeParamBounds = typeParam.getBounds();
          Type[] combinedUpperBounds = upperBounds;
          if (typeParamBounds.length > 0) {
            int upperBoundsLength = upperBounds.length;
            combinedUpperBounds = new Type[typeParamBounds.length + upperBoundsLength];
            int i = 0;
            for (; i < upperBoundsLength; i++) {
              combinedUpperBounds[i] = upperBounds[i];
            }
            int skipCount = 0;
            rootFor:
            for (; i < combinedUpperBounds.length; i++) {
              Type typeParamBound = typeParamBounds[i - upperBoundsLength];
              for (Type upperBound : upperBounds) {
                if (upperBound.equals(typeParamBound)) {
                  skipCount++;
                  continue rootFor;
                }
              }
              combinedUpperBounds[i] = typeParamBound;
            }
            if (skipCount > 0) {
              i = upperBoundsLength;
              while (combinedUpperBounds[i] == null) {
                if (i == combinedUpperBounds.length - 1) {
                  break;
                } else {
                  combinedUpperBounds[i] = combinedUpperBounds[i++];
                }
              }
              combinedUpperBounds =
                  Arrays.copyOf(combinedUpperBounds, combinedUpperBounds.length - skipCount);
            }
          }
          return super.captureAsTypeVariable(combinedUpperBounds);
        }
      };
    }
  }
}
