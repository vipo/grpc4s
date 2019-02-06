package com.github.vipo.grpc4s


import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}

import scala.language.higherKinds
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.{ServerCalls, StreamObserver}
import io.grpc.{ServerCallHandler, ServerServiceDefinition}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait NoStreaming[T]

object Vanilla {

  private def serverCallHandler[Req, Res](methodType: MethodType, f: Req => Res, constr: Constr[Req, Future, NoStreaming], decons: Decons[Res, Future, NoStreaming])(
      implicit ec: ExecutionContext): ServerCallHandler[Array[Byte], Array[Byte]] =
    methodType match {
      case MethodType.UNARY => unary(f, constr, decons)
      case _ => error()
    }

  private def unary[Req, Res](f: Req => Res, constr: Constr[Req, Future, NoStreaming], decons: Decons[Res, Future, NoStreaming])(
      implicit ec: ExecutionContext): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncUnaryCall(
      (request: Array[Byte], observer: StreamObserver[Array[Byte]]) =>
        decons(f(constr(RequestPure(request)))).unary.onComplete {
          case Success(value) =>
            observer.onNext(value)
            observer.onCompleted()
          case Failure(th) =>
            observer.onError(th)
        }
    )

  def build[Req, Res](f: Req => Res, definition: ServiceDefinition[Req, Res, Future, NoStreaming])(implicit ec: ExecutionContext): ServerServiceDefinition =
    definition.methods.foldLeft(ServerServiceDefinition.builder(definition.service)){
      case (builder, method) =>
        builder.addMethod(method._1, serverCallHandler(method._1.getType, f, method._2, method._3))
    }.build()

  implicit val conversions: Conversions[Future, NoStreaming] = new Conversions[Future, NoStreaming] {
    implicit val direct: ExecutionContext = ExecutionContext.fromExecutor((command: Runnable) => command.run())

    override def mapStream[F, T](stream: NoStreaming[F], f: F => T): Nothing =
      error()
    override def mapUnary[F, T](unary: Future[F], f: F => T): Future[T] =
      unary.map(f)
  }

  private def error(): Nothing = throw new IllegalArgumentException("Streaming not supported")

}
