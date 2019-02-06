package com.github.vipo.grpc4s

import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}
import io.grpc.{MethodDescriptor, ServiceDescriptor}

import scala.language.higherKinds

sealed trait RequestSum[P, S] {
  def pure: P
  def stream: S
  protected def error: Nothing = throw new IllegalStateException(s"Protobuf compilation error: $this")
}
final case class RequestStream[P, S](stream: S) extends RequestSum[P, S] {
  override def pure: P = error
}
final case class RequestPure[S, P](pure: P) extends RequestSum[P, S] {
  override def stream: S = error
}

sealed trait ResponseSum[U, S] {
  def unary: U
  def stream: S
  protected def error: Nothing = throw new IllegalStateException(s"Protobuf compilation error: $this")
}
final case class ResponseStream[U, S](stream: S) extends ResponseSum[U, S] {
  override def unary: U = error
}
final case class ResponseUnary[U, S](unary: U) extends ResponseSum[U, S] {
  override def stream: S = error
}


object ServiceDefinition {
  type Constr[Req, U[_], S[_]] = RequestSum[Array[Byte], S[Array[Byte]]] => Req
  type Decons[Res, U[_], S[_]] = Res => ResponseSum[U[Array[Byte]], S[Array[Byte]]]
}

case class ServiceDefinition[Req, Res, U[_], S[_]](service: ServiceDescriptor,
                                                   methods: List[(MethodDescriptor[Array[Byte], Array[Byte]], Constr[Req, U, S], Decons[Res, U, S])])

trait Conversions[U[_], S[_]] {
  def mapStream[F, T](stream: S[F], f: F => T): S[T]
  def mapUnary[F, T](unary: U[F], f: F => T): U[T]
}