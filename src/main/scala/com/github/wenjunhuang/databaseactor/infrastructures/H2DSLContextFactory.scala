package com.github.wenjunhuang.databaseactor.infrastructures

import java.sql.DriverManager
import java.util.Properties

import org.jooq.impl.DSL
import org.jooq.{DSLContext, SQLDialect}

import scala.util.{Success, Try}

class H2DSLContextFactory(jdbcUrl:String, user:String,password:String) extends DSLContextFactory {
  override def create() = {
    Try {
      DSL.using(getConnection, SQLDialect.H2)
    }
  }

  private[infrastructures] def getConnection() = {
    val props = new Properties()
    props.put("user", user)
    props.put("password", password)

    DriverManager.getConnection(jdbcUrl, props)
  }

  override def keepAlive(dsl: DSLContext):Try[Unit] = Success(Unit)
}
