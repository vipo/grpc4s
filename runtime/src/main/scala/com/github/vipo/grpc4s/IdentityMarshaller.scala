package com.github.vipo.grpc4s

import java.io.{ByteArrayInputStream, InputStream}

import com.google.common.io.ByteStreams
import io.grpc.MethodDescriptor.Marshaller

object IdentityMarshaller extends Marshaller[Array[Byte]] {
  override def stream(value: Array[Byte]): InputStream =
    new ByteArrayInputStream(value)

  override def parse(stream: InputStream): Array[Byte] =
    ByteStreams.toByteArray(stream)
}
