// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package meta

// Encoding Algorithms Flags
type Encoding uint8

const (
	UTF_8                     Encoding = 0x00
	LOWER_SPECIAL             Encoding = 0x01
	LOWER_UPPER_DIGIT_SPECIAL Encoding = 0x02
	FIRST_TO_LOWER_SPECIAL    Encoding = 0x03
	ALL_TO_LOWER_SPECIAL      Encoding = 0x04
)

// MetaString saves the serialized data
type MetaString struct {
	inputString  string
	encoding     Encoding // encoding flag
	specialChar1 byte
	specialChar2 byte
	encodedBytes []byte // serialized data
}

func (ms *MetaString) GetInputString() string { return ms.inputString }

func (ms *MetaString) GetEncoding() Encoding { return ms.encoding }

func (ms *MetaString) GetSpecialChar1() byte { return ms.specialChar1 }

func (ms *MetaString) GetSpecialChar2() byte { return ms.specialChar2 }

func (ms *MetaString) GetEncodedBytes() []byte { return ms.encodedBytes }

// StripLastChar return true if last char should be stripped
func (ms *MetaString) StripLastChar() bool {
	if ms.encoding == UTF_8 || ms.encodedBytes == nil {
		return false
	}
	return (ms.encodedBytes[0] & 0x80) > 0
}
