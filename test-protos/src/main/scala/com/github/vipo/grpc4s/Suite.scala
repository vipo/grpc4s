package com.github.vipo.grpc4s

import java.util.concurrent.TimeUnit

import io.grpc._
import io.grpc.stub.StreamObserver
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import vipo.calculator.CalculatorGrpc.CalculatorStub
import vipo.common.SingleValue
import vipo.streaming.StreamingGrpc.StreamingStub

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

trait Suite extends FunSuite with BeforeAndAfterAll {
  val server: Server
  def port: Int

  val channel: ManagedChannel = {
    val builder = ManagedChannelBuilder.forAddress("localhost", port)
    builder.usePlaintext()
    builder.build()
  }

  override def beforeAll(): Unit = server.start()

  override def afterAll(): Unit = server.shutdownNow()

  val options = CallOptions.DEFAULT.withDeadline(Deadline.after(10, TimeUnit.SECONDS))
  val calculator = new CalculatorStub(channel, options)
  val streaming = new StreamingStub(channel, options)

  test("happy path") {
    assert(
      Await.result(calculator.add(vipo.common.Pair(42, 42)), Duration.Inf) ==
        vipo.common.SingleValue(84)
    )
  }

  test("exception") {
    val thrown = intercept[Exception] {
      Await.result(calculator.div(vipo.common.Pair(42, 0)), Duration.Inf)
    }
    assert(
      thrown.getClass == classOf[StatusRuntimeException]
    )
  }

  test("request a stream") {
    val result = mutable.ArrayBuffer[SingleValue]()
    val promise = Promise[List[SingleValue]]
    streaming.requestStream(vipo.common.SingleValue(4), new StreamObserver[SingleValue] {
      override def onNext(value: SingleValue): Unit = result.append(value)
      override def onError(t: Throwable): Unit = promise.failure(new IllegalStateException())
      override def onCompleted(): Unit = promise.success(result.toList)
    })

    assert(
      Await.result(promise.future, Duration.Inf) ==
        List(SingleValue(42), SingleValue(42), SingleValue(42), SingleValue(42))
    )
  }

  test("send a stream") {
    val promise = Promise[SingleValue]
    val writer = streaming.consumeStream(new StreamObserver[SingleValue] {
      override def onNext(value: SingleValue): Unit =  promise.success(value)
      override def onError(t: Throwable): Unit = promise.failure(new IllegalStateException())
      override def onCompleted(): Unit = ()
    })
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onCompleted()

    assert(
      Await.result(promise.future, Duration.Inf) ==
        SingleValue(48)
    )
  }

  test("test bidi") {
    val result = mutable.ArrayBuffer[SingleValue]()
    val promise = Promise[List[SingleValue]]
    val writer = streaming.biStream(new StreamObserver[SingleValue] {
      override def onNext(value: SingleValue): Unit = result.append(value)
      override def onError(t: Throwable): Unit = promise.failure(new IllegalStateException())
      override def onCompleted(): Unit = promise.success(result.toList)
    })
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onNext(SingleValue(12))
    writer.onCompleted()

    assert(
      Await.result(promise.future, Duration.Inf) ==
        List(SingleValue(12), SingleValue(12), SingleValue(12), SingleValue(12))
    )
  }

}
