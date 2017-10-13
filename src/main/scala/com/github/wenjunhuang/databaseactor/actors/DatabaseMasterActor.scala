package com.github.wenjunhuang.databaseactor.actors

import akka.actor.{Actor, Props, Terminated}
import akka.routing._
import com.github.wenjunhuang.databaseactor.actors.DatabaseActionActor.RetrieveDatabaseConnection
import com.github.wenjunhuang.databaseactor.commands.{DatabaseAction, DatabaseActionWithPromise}
import com.github.wenjunhuang.databaseactor.infrastructures.DSLContextFactory

class DatabaseMasterActor(val factory: DSLContextFactory, val connectionCount: Int) extends Actor {
  private[databaseactor] var router: Router = _

  override def receive: Receive = {
    case w: DatabaseAction[_] =>
      router.route(w, sender())
    case p: DatabaseActionWithPromise[_] ⇒
      router.route(p, sender())
    case Terminated(a) =>
      router = router.removeRoutee(a)
  }

  override def preStart(): Unit = {
    router = createNewRouter
    router.route(Broadcast(RetrieveDatabaseConnection), self)
  }

  private[databaseactor] def createRoutee(index: Int) = {
    val r = context.actorOf(Props(classOf[DatabaseActionActor], factory).withDispatcher("database-actor.dispatcher"), s"DatabaseActor-$index")
    context watch r
    ActorRefRoutee(r)
  }

  private[databaseactor] def createNewRouter = {
    Router(SmallestMailboxRoutingLogic(), (1 to connectionCount).map { index =>
      createRoutee(index)
    })
  }
}

object DatabaseMasterActor {
  val Pattern = "jdbc:(.*)://.*".r

  def props(factory: DSLContextFactory, connectionCount: Int): Props = {
    Props(new DatabaseMasterActor(factory, connectionCount))
  }

  def props(jdbcUrl: String, user: String, password: String, connectionCount: Int): Props = jdbcUrl match {
    case Pattern(database) if database == "mysql" =>
      props(DSLContextFactory.mysql(jdbcUrl, user = user, password = password), connectionCount)
    case _ =>
      throw new IllegalArgumentException(s"not valid jdbcUrl: $jdbcUrl")
  }
}
