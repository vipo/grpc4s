package com.github.vipo.grpc4s

import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}
import io.grpc.{MethodDescriptor, ServiceDescriptor}

import scala.language.higherKinds

sealed trait StreamOrPure[S, P] {
  def pure: P
  def stream: S
  protected def error: Nothing = throw new IllegalStateException("Protobuf compilation error")
}
final case class AsStream[S, P](stream: S) extends StreamOrPure[S, P] {
  override def pure: P = error
}
final case class AsPure[S, P](pure: P) extends StreamOrPure[S, P] {
  override def stream: S = error
}

object ServiceDefinition {
  type Constr[Req, S[_]] = StreamOrPure[S[Array[Byte]], Array[Byte]] => Req
  type Decons[Res, S[_]] = Res => S[Array[Byte]]
}

case class ServiceDefinition[Req, Res, U[_], S[_]](service: ServiceDescriptor,
                                                   methods: List[(MethodDescriptor[Array[Byte], Array[Byte]], Constr[Req, S], Decons[Res, S])])

trait Conversions[U[_], S[_]] {
  def toStreamOf[F, T](value: U[F], f: F => T): S[T]
  def mapStream[F, T](stream: S[F], f: F => T): S[T]
}