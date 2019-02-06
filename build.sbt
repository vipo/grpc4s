
val Version = com.github.vipo.Grpc4s.Version
val ScalaPbVersion = scalapb.compiler.Version.scalapbVersion
val GrpcIoVersion = "1.17.1"

lazy val commonSettings = Seq(
  version := Version,
  organization := "com.github.vipo",
  scalaVersion := "2.12.8",
  scalacOptions := Seq("-deprecation", "-unchecked", "-language:_", "-encoding", "UTF-8"),
  libraryDependencies ++= Seq(
    "com.google.protobuf"   % "protobuf-java"   % "3.6.1",
    "com.thesamet.scalapb" %% "protoc-bridge"   % "0.7.3",
    "com.thesamet.scalapb" %% "scalapb-runtime" % ScalaPbVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % ScalaPbVersion,

    "io.grpc"               % "grpc-core"       % GrpcIoVersion
  )
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {}
)

lazy val root = (project in file("."))
  .settings(noPublish)
  .aggregate(monix, zio, vanilla, runtime)
  .dependsOn(monix, zio, vanilla)

lazy val protos = (project in file("test-protos"))
  .settings(
    commonSettings,
    noPublish,
    name := "grpc4s-test-protos",
    libraryDependencies ++= Seq(
      "org.scalatest"        %% "scalatest"        % "3.0.4"
    )
  )
  .dependsOn(runtime)

lazy val monix = (project in file("monix"))
  .settings(
    commonSettings,
    name := "grpc4s-monix",
    libraryDependencies ++= Seq(
      "io.monix"             %% "monix"            % "3.0.0-RC2",
      "io.grpc"               % "grpc-netty"       % GrpcIoVersion    % "test"
    )
  )
  .dependsOn(runtime, protos % "test")

lazy val zio = (project in file("zio"))
  .settings(
    commonSettings,
    name := "grpc4s-zio",
    libraryDependencies ++= Seq(
      "org.scalaz"           %% "scalaz-zio"       % "0.6.0",
      "io.grpc"               % "grpc-netty"       % GrpcIoVersion    % "test"
    )
  )
  .dependsOn(runtime, protos % "test")

lazy val vanilla = (project in file("vanilla"))
  .settings(
    commonSettings,
    name := "grpc4s-vanilla",
    libraryDependencies ++= Seq(
      "io.grpc"               % "grpc-netty"       % GrpcIoVersion    % "test"
    )
  )
  .dependsOn(runtime, protos % "test")

lazy val runtime = (project in file("runtime"))
  .settings(
    commonSettings,
    name := "grpc4s-runtime",
  )
