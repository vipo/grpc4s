package com.github.vipo.grpc4s


import com.github.vipo.grpc4s.ServiceDefinition.{Constr, Decons}

import scala.language.higherKinds
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.{ServerCalls, StreamObserver}
import io.grpc.{ServerCallHandler, ServerServiceDefinition}
import monix.eval.Task
import monix.execution.Ack.{Continue, Stop}
import monix.execution.schedulers.AsyncScheduler
import monix.execution.{Ack, ExecutionModel, Scheduler, UncaughtExceptionReporter}
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}

import scala.concurrent.{ExecutionContext, Future}

object Monix {

  private def serverCallHandler[Req, Res](methodType: MethodType, f: Req => Res, constr: Constr[Req, Observable], decons: Decons[Res, Observable])(
      implicit scheduler: Scheduler): ServerCallHandler[Array[Byte], Array[Byte]] =
    methodType match {
      case MethodType.UNARY => unary(f, constr, decons)
      case MethodType.CLIENT_STREAMING => clientStream(f, constr, decons)
      case MethodType.SERVER_STREAMING => serverStream(f, constr, decons)
      case _ => bidiStream(f, constr, decons)
    }

  private def unary[Req, Res](f: Req => Res, constr: Constr[Req, Observable], decons: Decons[Res, Observable])(
      implicit scheduler: Scheduler): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncUnaryCall(
      (request: Array[Byte], observer: StreamObserver[Array[Byte]]) =>
        write(decons(f(constr(AsPure(request)))), observer)
    )

  private def clientStream[Req, Res](f: Req => Res, constr: Constr[Req, Observable], decons: Decons[Res, Observable])(
      implicit scheduler: Scheduler): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncClientStreamingCall(
      (observer: StreamObserver[Array[Byte]]) => {
        val subject = PublishSubject[Array[Byte]]()

        decons(f(constr(AsStream(subject)))).head.subscribe(new Observer.Sync[Array[Byte]] {
          override def onNext(elem: Array[Byte]): Ack = {
            observer.onNext(elem)
            Stop
          }
          override def onError(ex: Throwable): Unit = observer.onError(ex)
          override def onComplete(): Unit = observer.onCompleted()
        })

        new StreamObserver[Array[Byte]] {
          override def onNext(value: Array[Byte]): Unit = subject.onNext(value)
          override def onError(t: Throwable): Unit = subject.onError(t)
          override def onCompleted(): Unit = subject.onComplete()
        }

      }
    )

  private def serverStream[Req, Res](f: Req => Res, constr: Constr[Req, Observable], decons: Decons[Res, Observable])(
      implicit scheduler: Scheduler): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncServerStreamingCall(
      (request: Array[Byte], observer: StreamObserver[Array[Byte]]) =>
        write(decons(f(constr(AsPure(request)))), observer)
    )

  private def bidiStream[Req, Res](f: Req => Res, constr: Constr[Req, Observable], decons: Decons[Res, Observable])(
      implicit scheduler: Scheduler): ServerCallHandler[Array[Byte], Array[Byte]] =
    ServerCalls.asyncBidiStreamingCall(
      (observer: StreamObserver[Array[Byte]]) => {
        val subject = PublishSubject[Array[Byte]]()

        write(decons(f(constr(AsStream(subject)))), observer)

        new StreamObserver[Array[Byte]] {
          override def onNext(value: Array[Byte]): Unit = subject.onNext(value)
          override def onError(t: Throwable): Unit = subject.onError(t)
          override def onCompleted(): Unit = subject.onComplete()
        }
      }
    )

  private def write[T](observable: Observable[T], observer: StreamObserver[T])(
      implicit scheduler: Scheduler): Unit =
    observable.subscribe(new Observer.Sync[T] {
      override def onNext(elem: T): Ack = {
        observer.onNext(elem)
        Continue
      }
      override def onError(ex: Throwable): Unit = observer.onError(ex)
      override def onComplete(): Unit = observer.onCompleted()
    })

  def build[Req, Res](f: Req => Res, definition: ServiceDefinition[Req, Res, Task, Observable])(implicit scheduler: Scheduler): ServerServiceDefinition =
    definition.methods.foldLeft(ServerServiceDefinition.builder(definition.service)){
      case (builder, method) =>
        builder.addMethod(method._1, serverCallHandler(method._1.getType, f, method._2, method._3))
    }.build()

  implicit val conversions: Conversions[Task, Observable] = new Conversions[Task, Observable] {
    override def toStreamOf[F, T](value: Task[F], f: F => T): Observable[T] =
      Observable.fromTask(value.map(f))
    override def mapStream[F, T](stream: Observable[F], f: F => T): Observable[T] =
      stream.map(f)
  }

}
