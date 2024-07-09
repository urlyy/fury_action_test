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

package fury

import (
	"fmt"
	"github.com/stretchr/testify/require"
	"reflect"
	"testing"
)

func TestTypeResolver(t *testing.T) {
	typeResolver := newTypeResolver()
	type A struct {
		F1 string
	}
	fmt.Println(reflect.TypeOf(A{}).String())
	require.Nil(t, typeResolver.RegisterTypeTag(reflect.TypeOf(A{}), "example.A"))
	require.Error(t, typeResolver.RegisterTypeTag(reflect.TypeOf(A{}), "example.A"))

	var tests = []struct {
		type_    reflect.Type
		typeInfo string
	}{
		{reflect.TypeOf((*int)(nil)), "*int"},
		{reflect.TypeOf((*[10]int)(nil)), "*[10]int"},
		{reflect.TypeOf((*[10]int)(nil)).Elem(), "[10]int"},
		{reflect.TypeOf((*[]map[string][]map[string]*interface{})(nil)).Elem(),
			"[]map[string][]map[string]*interface {}"},
		{reflect.TypeOf((*A)(nil)), "*@example.A"},
		{reflect.TypeOf((*A)(nil)).Elem(), "@example.A"},
		{reflect.TypeOf((*[]map[string]int)(nil)), "*[]map[string]int"},
		{reflect.TypeOf((*[]map[A]int)(nil)), "*[]map[@example.A]int"},
		{reflect.TypeOf((*[]map[string]*A)(nil)), "*[]map[string]*@example.A"},
	}
	for _, test := range tests {
		typeStr, err := typeResolver.encodeType(test.type_)
		require.Nil(t, err)
		require.Equal(t, test.typeInfo, typeStr)
	}
	for _, test := range tests {
		type_, typeStr, err := typeResolver.decodeType(test.typeInfo)
		require.Nil(t, err)
		require.Equal(t, test.typeInfo, typeStr)
		require.Equal(t, test.type_, type_)
	}
}
