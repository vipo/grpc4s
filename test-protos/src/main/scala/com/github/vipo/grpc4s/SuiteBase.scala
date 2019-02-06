package com.github.vipo.grpc4s

import java.util.concurrent.TimeUnit

import io.grpc._
import org.scalatest.{BeforeAndAfterAll, FunSuite}

trait SuiteBase extends FunSuite with BeforeAndAfterAll {
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

}
