package com.github.vipo.grpc4s.tests

import com.github.vipo.grpc4s.{Monix, StreamingSuite, UnarySuite}
import io.grpc._
import io.grpc.ServerBuilder.forPort

import scala.concurrent.ExecutionContext
import monix.execution.{ExecutionModel, UncaughtExceptionReporter}
import monix.execution.schedulers.AsyncScheduler
import org.scalatest.FunSuite
import vipo.calculator.CalculatorAlgebra
import vipo.streaming.StreamingAlgebra
import Monix._

class MonixSuite extends FunSuite with StreamingSuite with UnarySuite {

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

}
