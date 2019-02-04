
PB.targets in Compile := Seq(
  scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
  com.github.vipo.Grpc4sAlgebraGenerator -> (sourceManaged in Compile).value
)
