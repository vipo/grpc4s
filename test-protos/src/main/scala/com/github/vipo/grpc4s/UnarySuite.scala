package com.github.vipo.grpc4s

import io.grpc.StatusRuntimeException
import vipo.calculator.CalculatorGrpc.CalculatorStub

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait UnarySuite extends SuiteBase {
  val calculator = new CalculatorStub(channel, options)

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

}
