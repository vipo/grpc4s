package com.github.vipo.grpc4s.tests

import com.github.vipo.grpc4s.{UnarySuite, Vanilla}
import io.grpc._
import io.grpc.ServerBuilder.forPort
import org.scalatest.FunSuite
import vipo.calculator.CalculatorAlgebra
import Vanilla._

import scala.concurrent.ExecutionContext

class VanillaSuite extends FunSuite with UnarySuite {

  def port: Int = 4241

  implicit val ec: ExecutionContext = ExecutionContext.global

  val server: Server = {
    val builder = forPort(port)
    builder.addService(Vanilla.build(VanillaServices.calculator, CalculatorAlgebra.definition))
    builder.build()
  }

}
