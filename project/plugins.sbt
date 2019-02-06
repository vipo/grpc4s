addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin"   % "0.8.2",
  "com.github.vipo"      %% "grpc4s-generator" % "0.1.33-SNAPSHOT"
)

