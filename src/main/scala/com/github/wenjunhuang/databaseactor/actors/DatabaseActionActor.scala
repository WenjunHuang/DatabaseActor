package com.github.wenjunhuang.databaseactor.actors

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, FSM}
import com.github.wenjunhuang.databaseactor.Exceptions
import com.github.wenjunhuang.databaseactor.actors.DatabaseActionActor.State
import com.github.wenjunhuang.databaseactor.commands.{DatabaseAction, DatabaseActionResult, DatabaseActionWithPromise}
import com.github.wenjunhuang.databaseactor.infrastructures.DSLContextFactory
import org.jooq.DSLContext

import scala.concurrent.duration._
import scala.util.{Failure, Try}

class DatabaseActionActor(dslContextFactory: DSLContextFactory) extends Actor with ActorLogging with FSM[State, NotUsed] {

  private[databaseactor] var RetryInterval = 5.minutes
  private[databaseactor] var KeepConnectionAliveInterval = 10.seconds

  private[databaseactor] var dslContext: Option[DSLContext] = None

  import DatabaseActionActor._

  startWith(Initializing, NotUsed)

  when(Initializing) {
    case Event(RetrieveDatabaseConnection, _) =>
      dslContextFactory.create
        .map { dsl =>
          dslContext = Some(dsl)
          goto(Ready)
        }.recover {
        case e =>
          log.error(Exceptions.tryToRetrieveDatabaseConnection(e),
            Exceptions.TryToRetrieveDatabaseConnection)
          goto(Error)
      }.get
    case Event(_: DatabaseAction[_], _) =>
      sender ! DatabaseActionResult(Failure(Exceptions.connectionIsInitializing()))
      stay
    case Event(p: DatabaseActionWithPromise[_], _) =>
      p.promise.failure(Exceptions.connectionIsInitializing())
      stay
  }

  when(Error) {
    case Event(_: DatabaseAction[_], _) =>
      sendDatabaseExceptionToSender(sender, Exceptions.cannotConnectToDatabase())
      stay
    case Event(p: DatabaseActionWithPromise[_], _) ⇒
      p.promise.failure(Exceptions.cannotConnectToDatabase())
      stay
    case Event(RecoverDatabaseConnection, _) =>
      goto(Initializing)
  }

  when(Ready) {
    case Event(DatabaseAction(fn), _) =>
      executeAction(fn).map {
        result =>
          sender.tell(result, context.parent)
          stay
      }.recover {
        case e =>
          val error = Exceptions.cannotConnectToDatabase(e)
          log.error(e, "database error")

          sendDatabaseExceptionToSender(sender, error)
          goto(Error)
      }.get
    case Event(DatabaseActionWithPromise(promise,fn), _) ⇒
      executeAction(fn).map {
        result ⇒
          promise.complete(result.result)
          stay
      }.recover {
        case e ⇒
          val error = Exceptions.cannotConnectToDatabase(e)
          log.error(e, "database error")

          promise.failure(error)
          goto(Error)
      }.get
    case Event(KeepConnectionAlive, _) =>
      dslContextFactory.keepAlive(dslContext.get)
        .map { _ => log.debug("keepAlive successful"); stay() }
        .recover {
          case e =>
            log.info("keepAlive failed")
            val error = Exceptions.cannotConnectToDatabase(e)
            log.error(error, Exceptions.CannotConnectToDatabase)
            goto(Error)
        }
        .get
  }

  onTransition {
    case Initializing -> Ready =>
      onTransitToReady
    case Initializing -> Error =>
      onTransitToError
    case Ready -> Error =>
      onTransitToError
    case Error -> Initializing =>
      onTransitToInitializing
  }

  private[databaseactor] def onTransitToReady = {
    setTimer(KeepConnectionAlive.toString, KeepConnectionAlive, KeepConnectionAliveInterval, repeat = true)
  }

  private[databaseactor] def onTransitToInitializing = {
    self ! RetrieveDatabaseConnection
  }

  private[databaseactor] def onTransitToError = {
    clearPooledObject()
    cancelTimer(KeepConnectionAlive.toString)
    setTimer(RecoverDatabaseConnection.toString, RecoverDatabaseConnection, RetryInterval, repeat = false)
  }


  override def postStop(): Unit = {
    dslContext.foreach { dsl =>
      dslContextFactory.release(dsl)
    }
    dslContext = None
  }

  private[databaseactor] def executeAction(fn: DSLContext⇒Any): Try[DatabaseActionResult[Any]] = {
    dslContext.fold(Failure(Exceptions.cannotConnectToDatabase()).asInstanceOf) {
      dslContext =>
        Try {
          dslContextFactory.check(dslContext)
            .map { _ =>
              DatabaseActionResult(Try {
                fn(dslContext)
              })
            }.get
        }
    }
  }

  private[databaseactor] def clearPooledObject(): Unit = {
    dslContext.foreach {
      obj =>
        try {
          obj.close()
        }
        catch {
          case _: Throwable =>
        }
    }
    dslContext = None
  }

  private[databaseactor] def sendDatabaseExceptionToSender(sender: ActorRef, e: Exception) = {
    sender.tell(DatabaseActionResult(Failure(e)), context.parent)
  }
}

object DatabaseActionActor {

  case object RetrieveDatabaseConnection

  case object RecoverDatabaseConnection

  case object KeepConnectionAlive

  sealed trait State

  case object Initializing extends State

  case object Error extends State

  case object Ready extends State

}

