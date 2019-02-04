package com.github.vipo.grpc4s

import io.grpc._
import io.grpc.ServerBuilder.forPort
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import vipo.calculator.{CalculatorAlgebra, CalculatorGrpc}

import scala.concurrent.{Await, ExecutionContext, Promise}
import Monix._
import io.grpc.stub.StreamObserver
import vipo.common.SingleValue
import vipo.streaming.{StreamingAlgebra, StreamingGrpc}

import scala.collection.mutable
import scala.concurrent.duration.Duration

class MonixSuite extends FunSuite with BeforeAndAfterAll {

  val Port = 4242
  val executionContext: ExecutionContext = ExecutionContext.global

  val server: Server = {
    val builder = forPort(Port)
    builder.addService(Monix.build(MonixServices.calculator, CalculatorAlgebra.definition))
    builder.addService(Monix.build(MonixServices.streaming, StreamingAlgebra.definition))
    builder.build()
  }

  val channel: ManagedChannel = ManagedChannelBuilder
    .forAddress("localhost", Port)
    .usePlaintext
    .build()

  override def beforeAll(): Unit = server.start()

  override def afterAll(): Unit = server.shutdownNow()

  test("happy path") {
    assert(
      Await.result(CalculatorGrpc.stub(channel).add(vipo.common.Pair(42, 42)), Duration.Inf) ==
        vipo.common.SingleValue(84)
    )
  }

  test("exception") {
    val thrown = intercept[Exception] {
      Await.result(CalculatorGrpc.stub(channel).div(vipo.common.Pair(42, 0)), Duration.Inf)
    }
    assert(
      thrown.getClass == classOf[StatusRuntimeException]
    )
  }

  test("request a stream") {
    val result = mutable.ArrayBuffer[SingleValue]()
    val promise = Promise[List[SingleValue]]
    StreamingGrpc.stub(channel).requestStream(vipo.common.SingleValue(4), new StreamObserver[SingleValue] {
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
    val writer = StreamingGrpc.stub(channel).consumeStream(new StreamObserver[SingleValue] {
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
    val writer = StreamingGrpc.stub(channel).biStream(new StreamObserver[SingleValue] {
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
