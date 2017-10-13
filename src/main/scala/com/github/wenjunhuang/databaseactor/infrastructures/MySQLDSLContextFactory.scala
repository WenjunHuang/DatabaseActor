package com.github.wenjunhuang.databaseactor.infrastructures

import java.sql.DriverManager
import java.util.Properties

import com.typesafe.config.ConfigFactory
import org.jooq.impl.DSL
import org.jooq.{DSLContext, SQLDialect}

import scala.util.Try

class MySQLDSLContextFactory(jdbcUrl: String, user: String, password: String) extends DSLContextFactory {
  val mysqlConfig = ConfigFactory.load().getConfig("database-actor.mysql")

  override def create(): Try[DSLContext] = Try {
    DSL.using(getConnection, SQLDialect.MYSQL)
  }

  private[infrastructures] def getConnection() = {
    val props = new Properties()
    mysqlConfig.entrySet().forEach { entry =>
      props.put(entry.getKey, entry.getValue.unwrapped())
    }
    props.put("user", user)
    props.put("password", password)

    DriverManager.getConnection(jdbcUrl, props)
  }

  override def keepAlive(dsl: DSLContext): Try[Unit] = {
    check(dsl).map { _ =>
      dsl.execute("/* ping */")
    }
  }
}

