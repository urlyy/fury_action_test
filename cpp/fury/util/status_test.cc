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

#include "fury/util/status.h"
#include "gtest/gtest.h"

namespace fury {
class StatusTest : public ::testing::Test {};

TEST_F(StatusTest, StringToCode) {
  auto ok = Status::OK();
  StatusCode status = Status::StringToCode(ok.CodeAsString());
  ASSERT_EQ(status, StatusCode::OK);

  auto invalid = Status::Invalid("invalid");
  status = Status::StringToCode(invalid.CodeAsString());
  ASSERT_EQ(status, StatusCode::Invalid);

  ASSERT_EQ(Status::StringToCode("foobar"), StatusCode::IOError);
}

} // namespace fury

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
