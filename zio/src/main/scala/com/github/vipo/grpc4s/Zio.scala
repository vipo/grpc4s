package com.github.vipo.grpc4s

import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.{ServerCalls, StreamObserver}
import io.grpc.{ServerCallHandler, ServerServiceDefinition, Status, StatusRuntimeException}
import scalaz.zio.stream.Take.{End, Fail, Value}
import scalaz.zio.stream.{Stream, Take}
import scalaz.zio.{Exit, IO, Queue, RTS}

import scala.language.higherKinds
import scala.{Stream => _}


object Zio {
  
  class 
  
  private val MAX_PENDING_REQUESTS = 10
  
  case class ExceptionFromClient(cause: Throwable) extends StatusRuntimeException(Status.INTERNAL)
  
  type GrpcIO[T] = IO[StatusRuntimeException, T]
  type GrpcStream[T] = Stream[StatusRuntimeException, T]

  private def serverCallHandler[Req, Res](methodType: MethodType, f: Req => Res, constr: Constr[Req, GrpcIO, GrpcStream], decons: Decons[Res, GrpcIO, GrpcStream])(
      implicit rts: RTS): ServerCallHandler[Array[Byte], Array[Byte]] =
    methodType match {
      case MethodType.UNARY => unary(f, constr, decons)
      case MethodType.CLIENT_STREAMING => clientStream(f, constr, decons)
      case MethodType.SERVER_STREAMING => serverStream(f, constr, decons)
      case _ => bidiStream(f, constr, decons)
    }

  private def unary[Req, Res](f: Req => Res, constr: Constr[Req, GrpcIO, GrpcStream], decons: Decons[Res, GrpcIO, GrpcStream])(
      implicit rts: RTS): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncUnaryCall(
      (request: Array[Byte], observer: StreamObserver[Array[Byte]]) => {
        writeResultUnsafeAsync(Stream.lift(decons(f(constr(RequestPure(request)))).unary), observer)
      }
    )

  private def clientStream[Req, Res](f: Req => Res, constr: Constr[Req, GrpcIO, GrpcStream], decons: Decons[Res, GrpcIO, GrpcStream])(
    implicit rts: RTS): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncClientStreamingCall(
      (observer: StreamObserver[Array[Byte]]) => {
        val reqQueue = rts.unsafeRun(allocateQueue)

        writeResultUnsafeAsync(Stream.lift(decons(f(constr(RequestStream(requestStreamFrom(reqQueue))))).unary), observer)

        readTo(reqQueue)
      }
    )

  private def writeResultUnsafeAsync[Res, Req](response: Stream[StatusRuntimeException, Array[Byte]], observer: StreamObserver[Array[Byte]])(implicit rts: RTS) = {
    rts.unsafeRunAsync(writeStream(response, observer)){
      _ => () // shouldn't happen,
    }
  }

  private def serverStream[Req, Res](f: Req => Res, constr: Constr[Req, GrpcIO, GrpcStream], decons: Decons[Res, GrpcIO, GrpcStream])(
      implicit rts: RTS): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncServerStreamingCall(
      (request: Array[Byte], observer: StreamObserver[Array[Byte]]) => {
        writeResultUnsafeAsync(decons(f(constr(RequestPure(request)))).stream, observer)
      }
    )

  private def bidiStream[Req, Res](f: Req => Res, constr: Constr[Req, GrpcIO, GrpcStream], decons: Decons[Res, GrpcIO, GrpcStream])(
    implicit rts: RTS): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncBidiStreamingCall(
      (observer: StreamObserver[Array[Byte]]) => {
        val reqQueue = rts.unsafeRun(allocateQueue)
        
        writeResultUnsafeAsync(decons(f(constr(RequestStream(requestStreamFrom(reqQueue))))).stream,  observer)
     
        readTo(reqQueue)
      }
    )

  private val allocateQueue = Queue.bounded(MAX_PENDING_REQUESTS)[Option[Either[StatusRuntimeException, Array[Byte]]]]

  private def requestStreamFrom[Res, Req](queue: Queue[Option[Either[StatusRuntimeException, Array[Byte]]]]) = {
    val reqStream = Stream.fromQueue(queue).flatMap {
      case None => Stream.lift(queue.shutdown).flatMap(_ => Stream.empty)
      case Some(Left(err)) => Stream.lift(IO.fail(err))
      case Some(Right(req)) => Stream(req)
    }
    reqStream
  }

  private def readTo(reqQueue: Queue[Option[Either[StatusRuntimeException, Array[Byte]]]])(implicit rts: RTS) = new StreamObserver[Array[Byte]] {
    override def onNext(value: Array[Byte]): Unit = rts.unsafeRunSync(reqQueue.offer(Some(Right(value))))

    override def onError(t: Throwable): Unit = rts.unsafeRunSync(reqQueue.offer(Some(Left(ExceptionFromClient(t)))))

    override def onCompleted(): Unit = rts.unsafeRunSync(reqQueue.offer(None))
  }

  private def writeStream(stream: GrpcStream[Array[Byte]],
                          observer: StreamObserver[Array[Byte]]): IO[Nothing, Nothing] = {
    def feed(t: Take[StatusRuntimeException, Array[Byte]]): IO[Nothing, Unit] = t match {
      case End => IO.sync(observer.onCompleted())
      case Value(value) => IO.sync(observer.onNext(value))
      case Fail(er) => IO.sync(observer.onError(er))
    }

    stream.toQueue().use(_.take.flatMap(feed).forever)
  }
  
  def build[Req, Res](f: Req => Res, definition: ServiceDefinition[Req, Res, GrpcIO, GrpcStream])(implicit rts: RTS): ServerServiceDefinition =
    definition.methods.foldLeft(ServerServiceDefinition.builder(definition.service)){
      case (builder, method) =>
        builder.addMethod(method._1, serverCallHandler(method._1.getType, f, method._2, method._3))
    }.build()

  implicit val conversions: Conversions[GrpcIO, GrpcStream] = new Conversions[GrpcIO, GrpcStream] {
    override def mapStream[F, T](stream: GrpcStream[F], f: F => T): GrpcStream[T] =
      stream.map(f)
    override def mapUnary[F, T](unary: GrpcIO[F], f: F => T): GrpcIO[T] =
      unary.map(f)
  }

}
