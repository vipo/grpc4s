package com.github.vipo.grpc4s.tests

import com.github.vipo.grpc4s.NoStreaming

import scala.language.higherKinds
import vipo.calculator.CalculatorAlgebra._
import vipo.common.SingleValue

import scala.concurrent.Future

object VanillaServices {

  val calculator: CalculatorFunction[Future, NoStreaming] = {
    case r@Add(p) => r(Future.successful(SingleValue(p.a + p.b)))
    case r@Sub(p) => r(Future.successful(SingleValue(p.a - p.b)))
    case r@Mul(p) => r(Future.successful(SingleValue(p.a * p.b)))
    case r@Div(p) => r(Future.successful(SingleValue(p.a / p.b)))
    case r@Neg(p) => r(Future.successful(SingleValue(-p.a)))
  }

}