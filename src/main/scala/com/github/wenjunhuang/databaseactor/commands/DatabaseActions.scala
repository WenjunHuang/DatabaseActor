package com.github.wenjunhuang.databaseactor.commands

import org.jooq.DSLContext

import scala.concurrent.Promise
import scala.util.Try

case class DatabaseAction[T](fn: DSLContext => T)

case class DatabaseActionWithPromise[T](promise: Promise[T], fn: DSLContext ⇒ T)

object DatabaseActionWithPromise {
  def apply[T](promise: Promise[T])(fn: DSLContext ⇒ T) = new DatabaseActionWithPromise(promise, fn)
}

case class DatabaseActionResult[T](val result: Try[T])
