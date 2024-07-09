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

/** @type {import('ts-jest').JestConfigWithTsJest} */
const semver = require("semver");
const hpsEnable = semver.gt(process.versions.node, '20.0.0') && process.platform !== 'win32';

module.exports = {
  collectCoverage: hpsEnable,
  preset: 'ts-jest',
  testEnvironment: 'node',
  collectCoverageFrom: [
    "**/*.ts",
    "!**/dist/**",
    "!**/build/**",
    "!packages/fury/lib/murmurHash3.ts"
  ],
  "testPathIgnorePatterns" : [
    hpsEnable ? null : "(.*)/hps.test.ts$",
  ].filter(Boolean),
  transform: {
    '\\.ts$': ['ts-jest', {
      tsconfig: {
        target: "ES2021"
      },
      diagnostics: {
        ignoreCodes: [151001]
      }
    }],
  },
  // todo: JavaScript codebase is iterating rapidly, remove this restriction temporary 
  // coverageThreshold: {
  //   global: {
  //     branches: 91,
  //     functions: 99,
  //     lines: 98,
  //     statements: 98
  //   }
  // }
};