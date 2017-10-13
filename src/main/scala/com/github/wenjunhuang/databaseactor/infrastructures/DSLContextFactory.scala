package com.github.wenjunhuang.databaseactor.infrastructures

import org.jooq.DSLContext

import scala.concurrent.duration._
import scala.util.Try

trait DSLContextFactory {
  val VALIDATION_TIMEOUT = 5.seconds

  def create(): Try[DSLContext]

  def check(dsl: DSLContext): Try[Unit] = Try {
    val connection = dsl.configuration().connectionProvider().acquire()
    if (!connection.isValid(VALIDATION_TIMEOUT.toSeconds.toInt))
      throw new Exception(s"connection is invalid or dead")
  }

  def keepAlive(dsl: DSLContext): Try[Unit]

  def release(dsl: DSLContext): Unit = {
    if (dsl != null)
      try {
        dsl.close()
      } catch {
        case _: Throwable =>
      }
  }
}

object DSLContextFactory {
  def mysql(jdbcUrl: String, user: String, password: String): DSLContextFactory = {
    new MySQLDSLContextFactory(jdbcUrl, user, password)
  }

  def h2(jdbcUrl: String, user: String, password: String): DSLContextFactory = {
    new H2DSLContextFactory(jdbcUrl, user, password)
  }
}
