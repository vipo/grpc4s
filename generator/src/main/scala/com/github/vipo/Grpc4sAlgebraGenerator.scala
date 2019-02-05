package com.github.vipo

import com.google.protobuf.Descriptors.{FileDescriptor, MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import scalapb.compiler.StreamType.{ClientStreaming, ServerStreaming, Unary}
import scalapb.compiler._
import protocbridge.ProtocCodeGenerator

import scala.collection.JavaConverters._

class Grpc4sAlgebraGenerator(implicits: DescriptorImplicits) {
  import implicits._

  private def apply(service: ServiceDescriptor): FunctionalPrinter = {
    val ServiceName = service.getName

    def requests(p: FunctionalPrinter) = service.getMethods.asScala.foldLeft(p) { (printer, m) =>
      printer
        .add(s"case class ${m.getName.capitalize}[U[_], S[_]](value: ${uOrSIn(m)})(implicit conv: Conversions[U, S])")
        .indent
        .addIndented(s"extends ${ServiceName}Request[U, S]")
        .addIndented(s"with (${uOrSOut(m)} => ${ServiceName}Response[U, S]) {")
        .add(
          m.streamType match {
            case StreamType.Unary | StreamType.ClientStreaming =>
              s"def apply(value: ${uOrSOut(m)}): ${ServiceName}Response[U, S] = ${m.getName.capitalize}Response(conv.toStreamOf(value, (v: ${m.outputType.scalaType}) => v.toByteArray))"
            case _ =>
              s"def apply(value: ${uOrSOut(m)}): ${ServiceName}Response[U, S] = ${m.getName.capitalize}Response(conv.mapStream(value, (v: ${m.outputType.scalaType}) => v.toByteArray))"
          }
        )
        .outdent
        .add("}")
    }

    def responses(p: FunctionalPrinter) = service.getMethods.asScala.foldLeft(p) { (printer, m) =>
      printer
        .add(s"case class ${m.getName.capitalize}Response[U[_], S[_]] private(stream: S[Array[Byte]]) extends ${ServiceName}Response[U, S]")
    }

    def uOrSOut(method: MethodDescriptor): String =
      method.streamType match {
        case Unary | ClientStreaming => s"U[${method.outputType.scalaType}]"
        case _                       => s"S[${method.outputType.scalaType}]"
      }

    def uOrSIn(method: MethodDescriptor): String =
      method.streamType match {
        case Unary | ServerStreaming =>   s"${method.inputType.scalaType}"
        case _                       => s"S[${method.inputType.scalaType}]"
      }

    def methodDescriptor(method: MethodDescriptor) = PrinterEndo { p =>

      val methodType = method.streamType match {
        case StreamType.Unary           => "UNARY"
        case StreamType.ClientStreaming => "CLIENT_STREAMING"
        case StreamType.ServerStreaming => "SERVER_STREAMING"
        case StreamType.Bidirectional   => "BIDI_STREAMING"
      }

      p.addStringMargin(
        s"""private val ${method.descriptorName}: MethodDescriptor[Array[Byte], Array[Byte]] =
           |  MethodDescriptor.newBuilder()
           |    .setType(MethodDescriptor.MethodType.$methodType)
           |    .setFullMethodName(MethodDescriptor.generateFullMethodName("${service.getFullName}", "${method.getName}"))
           |    .setSampledToLocalTracing(true)
           |    .setRequestMarshaller(IdentityMarshaller)
           |    .setResponseMarshaller(IdentityMarshaller)
           |    .build()
           |"""
      )
    }

    def methodDef(m: MethodDescriptor) = PrinterEndo(
      _.add(m.streamType match {
        case StreamType.Unary | StreamType.ServerStreaming =>
          s"(${m.descriptorName}, s => ${m.getName.capitalize}(${m.inputType.scalaType}.parseFrom(s.pure)), r => r.stream),"
        case _ =>
          s"(${m.descriptorName}, s => ${m.getName.capitalize}(conv.mapStream(s.stream, (bytes: Array[Byte]) => ${m.inputType.scalaType}.parseFrom(bytes))), r => r.stream),"
      })
    )

    def serviceDescriptor(service: ServiceDescriptor) =
      PrinterEndo(
        _.add(s"private val ${service.descriptorName}: ServiceDescriptor =").indent
          .add(s"""ServiceDescriptor.newBuilder("${service.getFullName}")""")
          .indent
          .add(
            s""".setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(${service.getFile.fileDescriptorObjectFullName}.javaDescriptor))"""
          )
          .print(service.methods) {
            case (p, method) =>
              p.add(s".addMethod(${method.descriptorName})")
          }
          .add(".build()")
          .outdent
          .outdent
          .newline
      )


    FunctionalPrinter()
      .add("package " + service.getFile.scalaPackageName)
      .newline
      .add("import scala.language.higherKinds")
      .newline
      .add("import io.grpc.MethodDescriptor")
      .add("import io.grpc.ServiceDescriptor")
      .add("import com.github.vipo.grpc4s.{Conversions, IdentityMarshaller, ServiceDefinition}")
      .newline
      .add(s"object ${ServiceName}Algebra {")
      .indent
      .newline
      .add(s"type ${ServiceName}Function[U[_], S[_]] = ${ServiceName}Request[U, S] => ${ServiceName}Response[U, S]")
      .newline
      .add("// request")
      .add(s"sealed trait ${ServiceName}Request[U[_], S[_]]")
      .call(requests)
      .newline
      .add("// response")
      .add(s"sealed trait ${ServiceName}Response[U[_], S[_]] { def stream: S[Array[Byte]] }")
      .call(responses)
      .newline
      .add(s"def definition[U[_], S[_]](implicit conv: Conversions[U, S]): ServiceDefinition[${ServiceName}Request[U, S], ${ServiceName}Response[U, S], U, S] = ServiceDefinition(")
      .indent
      .add("SERVICE,")
      .add("List(")
      .indent
      .call(service.methods.map(methodDef): _*)
      .outdent
      .add(")")
      .outdent
      .add(")")
      .newline
      .call(service.methods.map(methodDescriptor): _*)
      .call(serviceDescriptor(service))
      .outdent
      .add("}")
      .newline
  }
}

object Grpc4sAlgebraGenerator extends ProtocCodeGenerator {

  override def run(req: Array[Byte]): Array[Byte] = {
    val request = CodeGeneratorRequest.parseFrom(req)
    handleCodeGeneratorRequest(request).toByteArray
  }

  private def generate(file: FileDescriptor, implicits: DescriptorImplicits): List[CodeGeneratorResponse.File] = {
    val generator = new Grpc4sAlgebraGenerator(implicits)
    import implicits.FileDescriptorPimp
    def responseFile(file: FileDescriptor, service: ServiceDescriptor, result: String): CodeGeneratorResponse.File = {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setName(file.scalaDirectory + "/" + service.getName + "Algebra.scala")
      b.setContent(result)
      b.build
    }
    for {
      service <- file.getServices.asScala.toList
    } yield responseFile(file, service, generator(service).result())
  }

  private def handleCodeGeneratorRequest(request: CodeGeneratorRequest): CodeGeneratorResponse = {
    val fileByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc)
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
      }
    val dImplicits = new DescriptorImplicits(GeneratorParams(), fileByName.values.toVector)
    request.getFileToGenerateList.asScala.foldLeft(CodeGeneratorResponse.newBuilder) {
      case (b, name) => b.addAllFile(generate(fileByName(name), dImplicits).asJava)
    }.build()
  }

}
