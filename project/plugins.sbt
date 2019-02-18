addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

resolvers += Resolver.bintrayRepo("vipo", "grpc4s")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin"   % "0.8.2",
  "com.github.vipo"      %% "grpc4s-generator" % "0.2"
)

