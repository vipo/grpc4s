package com.github.vipo.grpc4s

import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}
import com.google.protobuf.GeneratedMessage
import io.grpc.{MethodDescriptor, ServiceDescriptor}

import scala.language.higherKinds

object ServiceDefinition {
  type Constr[Req, S[_]] = S[Array[Byte]] => Req
  type Decons[Res, S[_]] = Res => S[Array[Byte]]
}

case class ServiceDefinition[Req, Res, U[_], S[_]](service: ServiceDescriptor,
                                                   methods: List[(MethodDescriptor[Array[Byte], Array[Byte]], Constr[Req, S], Decons[Res, S])])

trait Conversions[U[_], S[_]] {
  def toStreamOf[F, T](value: U[F], f: F => T): S[T]
  def mapStream[F, T](stream: S[F], f: F => T): S[T]
  def unsafeHead[T](stream: S[T]): T
}