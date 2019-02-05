package com.github.vipo.grpc4s

import com.github.vipo.grpc4s.Zio.{GrpcIO, GrpcStream}

import scala.{Stream => _}
import scalaz.zio.IO
import scalaz.zio.stream.Stream

import scala.language.higherKinds
import vipo.calculator.CalculatorAlgebra._
import vipo.streaming.StreamingAlgebra._
import vipo.common.SingleValue

object ZioServices {

  val calculator: CalculatorFunction[GrpcIO, GrpcStream] = {
    case r@Add(p) => r(IO.sync(SingleValue(p.a + p.b)))
    case r@Sub(p) => r(IO.sync(SingleValue(p.a - p.b)))
    case r@Mul(p) => r(IO.sync(SingleValue(p.a * p.b)))
    case r@Div(p) => r(IO.sync(SingleValue(p.a / p.b)))
    case r@Neg(p) => r(IO.sync(SingleValue(-p.a)))
  }

  val streaming: StreamingFunction[GrpcIO, GrpcStream] = {
    case r@RequestStream(p) => r(Stream.fromIterable(Iterator.fill(p.a.toInt)(SingleValue(42)).toIterable))
    case r@ConsumeStream(s) => r(s.map(_.a.toInt).foldLeft(0)(_+_).map(SingleValue(_)))
    case r@BiStream(s) => r(s)
  }

}
