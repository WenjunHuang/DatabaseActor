import akka.actor.{ActorSystem, Props}
import com.github.wenjunhuang.databaseactor.actors.DatabaseMasterActor
import com.github.wenjunhuang.databaseactor.commands.DatabaseActionWithPromise
import com.github.wenjunhuang.databaseactor.infrastructures.DSLContextFactory

import scala.concurrent.Promise
import scala.util.{Failure, Success}
import org.jooq.scalaextensions.Conversions._

object DatabaseActionWithPromiseTest {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("test")
    import system.dispatcher
    val factory = DSLContextFactory.mysql("jdbc:mysql://192.168.2.246:3306/akka_http_test", "root", "123456")

    val master = system.actorOf(Props(classOf[DatabaseMasterActor], factory, 10))
    val okPromise = Promise[Int]()
    master ! DatabaseActionWithPromise(okPromise, { dslContext ⇒
      dslContext.selectCount().from("orders").fetchOneOption().map { record ⇒
        record.value1()
      }.getOrElse(new Integer(0)).intValue()
    })

    okPromise.future.onComplete {
      case Success(value) ⇒
        println(value)
      case Failure(e) ⇒
        println(e)
    }

    val exceptionPromise = Promise[Int]()
    master ! DatabaseActionWithPromise(exceptionPromise, { dslContext ⇒
      dslContext.selectCount().from("i_am_not_exist").fetchOneOption().map { record ⇒
        record.value1()
      }.getOrElse(new Integer(0)).intValue()
    })

    exceptionPromise.future.onComplete {
      case Failure(e) ⇒
        println(e)
    }
  }

}
