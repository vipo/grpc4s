
version := "0.2.1"

name := "grpc4s-generator"

organization := "com.github.vipo"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "protoc-bridge"  % "0.7.4",
  "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
)

sourceGenerators in Compile += Def.task {
  val file = (sourceManaged in Compile).value / "com" / "github" / "vipo" / "Grpc4sVersion.scala"
  IO.write(file,
    s"""package com.github.vipo
       |object Grpc4s {
       |  val Version = "${version.value}"
       |}""".stripMargin)
  Seq(file)
}.taskValue

bintrayRepository := "grpc4s"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))