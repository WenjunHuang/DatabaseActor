package com.github.wenjunhuang.databaseactor

/**
  * Created by rick on 2017/7/12.
  */
object Exceptions {
  val TryToRetrieveDatabaseConnection = "error when try to retrieve database connection"

  def tryToRetrieveDatabaseConnection(inner: Throwable = null) = {
    new Exception(TryToRetrieveDatabaseConnection, inner)
  }

  val ConnectionIsInitializing = "database connection is initializing"

  def connectionIsInitializing(inner: Throwable = null) = {
    new Exception(ConnectionIsInitializing, inner)
  }

  val CannotConnectToDatabase = "can not connect to database"

  def cannotConnectToDatabase(inner: Throwable = null) = {
    new Exception(CannotConnectToDatabase, inner)
  }

}
