package com.github.vipo.grpc4s.tests

import com.github.vipo.grpc4s.{Monix, ResponseUnary, StreamingSuite, UnarySuite}
import io.grpc._
import io.grpc.ServerBuilder.forPort

import scala.concurrent.ExecutionContext
import monix.execution.{ExecutionModel, UncaughtExceptionReporter}
import monix.execution.schedulers.AsyncScheduler
import org.scalatest.FunSuite
import vipo.calculator.CalculatorAlgebra
import vipo.streaming.StreamingAlgebra
import Monix._
import monix.eval.Task
import monix.reactive.Observable
import vipo.calculator.CalculatorAlgebra.{Add, AddEntityResponse, AddResponse}
import vipo.common.SingleValue

class MonixSuite extends FunSuite with UnarySuite with StreamingSuite {

  def port: Int = 4243

  private implicit val scheduler = AsyncScheduler(
    monix.execution.Scheduler.DefaultScheduledExecutor,
    ExecutionContext.Implicits.global,
    UncaughtExceptionReporter.default,
    ExecutionModel.SynchronousExecution
  )

  val server: Server = {
    val builder = forPort(port)
    builder.addService(Monix.build(MonixServices.calculator, CalculatorAlgebra.definition))
    builder.addService(Monix.build(MonixServices.streaming, StreamingAlgebra.definition))
    builder.build()
  }

  test("unit test") {
    MonixServices.calculator(Add(vipo.common.Pair(12, 12))) match {
      case AddEntityResponse(r) => r.unary.runSyncUnsafe() ==
        SingleValue(24)
    }
  }

}
