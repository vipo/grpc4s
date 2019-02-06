package com.github.vipo.grpc4s.tests

import monix.eval.Task
import monix.reactive.Observable

import scala.language.higherKinds
import vipo.calculator.CalculatorAlgebra._
import vipo.streaming.StreamingAlgebra._
import vipo.common.SingleValue

object MonixServices {

  val calculator: CalculatorFunction[Task, Observable] = {
    case r@Add(p) => r(Task.evalAsync(SingleValue(p.a + p.b)))
    case r@Sub(p) => r(Task.evalAsync(SingleValue(p.a - p.b)))
    case r@Mul(p) => r(Task.evalAsync(SingleValue(p.a * p.b)))
    case r@Div(p) => r(Task.evalAsync(SingleValue(p.a / p.b)))
    case r@Neg(p) => r(Task.evalAsync(SingleValue(-p.a)))
  }

  val streaming: StreamingFunction[Task, Observable] = {
    case r@RequestStream(p) => r(Observable.fromIterable(Iterator.fill(p.a.toInt)(SingleValue(42)).toIterable))
    case r@ConsumeStream(s) => r(s.map(_.a.toInt).foldLeftL(0)(_+_).map(SingleValue(_)))
    case r@BiStream(s) => r(s)
  }

}
