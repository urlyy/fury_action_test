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

#include "gtest/gtest.h"

#include "fury/meta/field_info.h"

namespace fury {

namespace test {

struct A {
  int x;
  float y;
  bool z;
};

FURY_FIELD_INFO(A, x, y, z);

TEST(FieldInfo, Simple) {
  A a;
  constexpr auto info = FuryFieldInfo(a);

  static_assert(info.Size == 3);

  static_assert(info.Name == "A");

  static_assert(info.Names[0] == "x");
  static_assert(info.Names[1] == "y");
  static_assert(info.Names[2] == "z");

  static_assert(std::get<0>(info.Ptrs) == &A::x);
  static_assert(std::get<1>(info.Ptrs) == &A::y);
  static_assert(std::get<2>(info.Ptrs) == &A::z);
}

struct B {
  A a;
  int hidden;
};

FURY_FIELD_INFO(B, a);

TEST(FieldInfo, Hidden) {
  B b;
  constexpr auto info = FuryFieldInfo(b);

  static_assert(info.Size == 1);

  static_assert(info.Name == "B");

  static_assert(info.Names[0] == "a");

  static_assert(std::get<0>(info.Ptrs) == &B::a);
}

} // namespace test

} // namespace fury

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
