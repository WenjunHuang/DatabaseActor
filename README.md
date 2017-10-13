[![Build Status](https://travis-ci.org/WenjunHuang/DatabaseActor.svg?branch=master)](https://travis-ci.org/WenjunHuang/DatabaseActor)


An akka & jooq util librayry to provide database connection management and perform database commands asynchronously.
### Design philosophy：
* One actor object manage one connection. if connection lost,its actor dies
* Make good use of capabilities of primitive jdbc driver
* Use it's own ExecutionDispatcher

### Usages：

1. DatabaseAction
    ```scala
           ```scala
                  ```scala
                  import akka.actor.{Actor, ActorRef, ActorSystem, Props}
                  import com.goldlokedu.commons.databaseactor.{DSLContextFactory, DatabaseAction, DatabaseActionResult, }
                  import database.Tables
              
                  import scala.util.{Failure, Success}
              
                  object IntegrationTest extends App {
                    implicit val system = ActorSystem("DatabaseActorTest")
              
                    val mysql = DSLContextFactory.mysql(
                      "jdbc:mysql://localhost:3306/sakila", 
                      "root", 
                      "123456" 
                      )
                    
                    // Create a global DatabaseMasterActor
                    val master = system.actorOf(DatabaseMasterActor.props(factory = mysql, connectionCount = 10))
                    val foo = system.actorOf(Props(classOf[MyFooActor], master))
              
                    foo ! "GetUsers"
              
                    Thread.sleep(2000)
                    system.terminate()
                  }
              
                  class MyFooActor(val master: ActorRef) extends Actor {
                    override def receive: Receive = {
                      case "GetUsers" =>
                        // send DatabaseAction,DatabaseActor will call the given function to execute database action
                        master ! DatabaseAction { dsl =>
                          dsl.selectCount().from(Tables.ACTOR).fetchOne(0)
                        }
                      case DatabaseActionResult(Success(count)) =>
                        println(s"actor sum: $count")
                      case DatabaseActionResult(Failure(e)) =>
                        println(s"exception,$e")
                    }
                  }
                  ```
           ```
    ```
2. DatabaseActionWithPromise
    ```scala
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
    ```
