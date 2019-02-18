# grpc4s

Your grpc service is a function.

Main goals of this project:
* Generate (quite generic) code once and use it with easily plugable _backends_, i.e. Monix, Zio,...
* Minimum dependencies

## Usage

Assuming you have a service defined using protocol buffers,
like [this](test-protos/src/main/protobuf/calculator.proto) or
[that](test-protos/src/main/protobuf/streaming.proto)

### Enable ScalaPB and grpc4s plugins

Put this into `project/plugins`:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")

resolvers += Resolver.bintrayRepo("vipo", "grpc4s")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin"   % "0.8.2",
  "com.github.vipo"      %% "grpc4s-generator" % "0.2"
)
```

### Run plugins

Add to your `build.sbt`:

```scala
PB.targets in Compile := Seq(
  scalapb.gen(grpc = false) -> (sourceManaged in Compile).value,
  com.github.vipo.Grpc4sAlgebraGenerator -> (sourceManaged in Compile).value
)
```

_Note_: grpc feature of ScalaPB is not needed

### Implement your service

Like [here](monix/src/main/scala/com/github/vipo/grpc4s/Monix.scala) or
[there](vanilla/src/main/scala/com/github/vipo/grpc4s/Vanilla.scala).

### Run

For example with Monix (grpc4s-monix is needed):

```scala
val builder = ServerBuilder.forPort(4444)
builder.addService(Monix.build(calculatorFunction, CalculatorAlgebra.definition))
builder.addService(Monix.build(streamingFunction, StreamingAlgebra.definition))
builder.build().start()
```

_Note:_ `CalculatorAlgebra` is a generated code.

## Features

1. [ScalaPB](https://github.com/scalapb/ScalaPB) for entities generation (only, grpc is not needed)
2. Supports streaming
3. Can use Monix a backend (grpc4s-monix): Tasks and Observables will be used
4. Can be used with zero external libraries (grpc4s-vanilla): Scala Futures will be used with no streaming support  

## Next

1. Zio backend
