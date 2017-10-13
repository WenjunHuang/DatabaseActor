package com.github.wenjunhuang

import org.jooq.DSLContext

import scala.util.Try

/**
  * Created by rick on 2017/7/20.
  */
package object databaseactor {
  type DatabaseAction[T] = commands.DatabaseAction[T]

  object DatabaseAction {
    def apply[T](fn: DSLContext => T): DatabaseAction[T] = new DatabaseAction(fn)

    def unapply[T](arg: DatabaseAction[T]): Option[DSLContext => T] = Some(arg.fn)
  }

  type DatabaseActionResult[T] = commands.DatabaseActionResult[T]

  object DatabaseActionResult {
    def apply[T](result: Try[T]): DatabaseActionResult[T] = new DatabaseActionResult(result)

    def unapply[T](arg: DatabaseActionResult[T]): Option[Try[T]] = Some(arg.result)
  }


  type DatabaseMasterActor = actors.DatabaseMasterActor
  val DatabaseMasterActor = actors.DatabaseMasterActor

  type DSLContextFactory = infrastructures.DSLContextFactory
  val DSLContextFactory = infrastructures.DSLContextFactory
}
