/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

name := "fury-scala"
val scala213Version = "2.13.14"
scalaVersion := scala213Version
crossScalaVersions := Seq(scala213Version, "3.3.3")

resolvers += Resolver.mavenLocal
resolvers += Resolver.ApacheMavenSnapshotsRepo

val furyVersion = "0.6.0-SNAPSHOT"
libraryDependencies ++= Seq(
  "org.apache.fury" % "fury-core" % furyVersion,
  "org.scalatest" %% "scalatest" % "3.2.19",
)
