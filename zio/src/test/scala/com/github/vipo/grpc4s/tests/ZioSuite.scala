package com.github.vipo.grpc4s.tests

import com.github.vipo.grpc4s.{Suite, Zio}
import io.grpc._
import io.grpc.ServerBuilder.forPort
import org.scalatest.FunSuite
import vipo.calculator.CalculatorAlgebra
import vipo.streaming.StreamingAlgebra

import Zio._

class ZioSuite extends FunSuite with Suite {

  def port = 4242

  val server: Server = {
    val builder = forPort(port)
    builder.addService(Zio.build(ZioServices.calculator, CalculatorAlgebra.definition))
    builder.addService(Zio.build(ZioServices.streaming, StreamingAlgebra.definition))
    builder.build()
  }

}
