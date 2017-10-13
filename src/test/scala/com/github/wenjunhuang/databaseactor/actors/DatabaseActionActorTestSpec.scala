package com.github.wenjunhuang.databaseactor.actors

import java.sql.SQLException

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit, TestProbe}
import akka.util.Timeout
import com.github.wenjunhuang.databaseactor.actors.DatabaseActionActor.{RecoverDatabaseConnection, RetrieveDatabaseConnection}
import com.github.wenjunhuang.databaseactor.commands.{DatabaseAction, DatabaseActionResult, DatabaseActionWithPromise}
import com.github.wenjunhuang.databaseactor.infrastructures.DSLContextFactory
import org.jooq.DSLContext
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}


/**
  * Created by rick on 2017/7/3.
  */
@RunWith(classOf[JUnitRunner])
class DatabaseActionActorTestSpec extends TestKit(ActorSystem("WorkerActorTest"))
                                          with ImplicitSender
                                          with FunSuiteLike
                                          with BeforeAndAfterEach
                                          with BeforeAndAfterAll
                                          with Matchers
                                          with MockFactory {
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5.seconds)
  var mockFactory: DSLContextFactory = _
  var mockDslContext: DSLContext = _
  var testProbe: TestProbe = _
  var testActorRef: TestFSMRef[DatabaseActionActor.State, NotUsed, WrapperDatabaseActionActor] = _


  test("when a worker actor is created, it should enter Initializing state") {
    testActorRef.stateName should be(DatabaseActionActor.Initializing)
  }

  test("when action actor successfully retrieve a database connection, it should enter Ready state") {
    checkSuccess()
    testActorRef ! RetrieveDatabaseConnection
    testActorRef.stateName should be(DatabaseActionActor.Ready)
  }

  test(
    "when action actor in Initializing state and can not retrieve a database connection, it should enter Error state") {
    objectFactoryCreateThrows()
    testActorRef ! RetrieveDatabaseConnection
    testActorRef.stateName should be(DatabaseActionActor.Error)
  }

  test(
    "when action actor in Initializing state and receive DatabaseAction, it should send back DatabaseActionResult(Failure) ")
  {
    testActorRef.setState(DatabaseActionActor.Initializing)
    val mockCallback = stubFunction[DSLContext, Unit]
    testActorRef.tell(DatabaseAction(mockCallback), testProbe.ref)

    testProbe.expectMsgPF(timeout.duration) {
      case DatabaseActionResult(Failure(_)) =>
        succeed
      case _ =>
        fail("wrong result")
    }
  }

  test("when action actor in Initializing state and receive DatabaseActionWithPromise, it should fail the promise") {
    testActorRef.setState(DatabaseActionActor.Initializing)
    val mockCallback = stubFunction[DSLContext, Unit]
    val promise = Promise[Unit]()
    testActorRef.tell(DatabaseActionWithPromise(promise, mockCallback), testProbe.ref)

    an[Exception] should be thrownBy Await.result(promise.future, Duration.Inf)
  }

  test("when action actor enter Error state, it should set a timer") {
    testActorRef.isTimerActive(RecoverDatabaseConnection.toString) should be(false)
    testActorRef.setState(DatabaseActionActor.Error)
    testActorRef.isTimerActive(RecoverDatabaseConnection.toString) should be(true)
  }

  test("when action actor enter Error state and it's timer triggered, it should enter Initializing state ") {
    val interval = 3.seconds
    testActorRef.underlyingActor.RetryInterval = interval
    testActorRef.underlyingActor.autoConnectionToDatabase = false
    testActorRef.setState(DatabaseActionActor.Error)

    waitFor(interval)
    testActorRef.stateName should be(DatabaseActionActor.Initializing)
  }

  test("when action actor enter Error state and it's timer triggered, " +
         "but can not receive database connection, then it should go back to Error state ") {
    val interval = 1.seconds
    objectFactoryCreateThrows()
    testActorRef.underlyingActor.RetryInterval = interval
    testActorRef.setState(DatabaseActionActor.Error)

    waitFor(interval)
    testActorRef.stateName should be(DatabaseActionActor.Error)

  }

  private def waitFor(interval: FiniteDuration) = {
    Try {
      Await.ready(Future.never, interval + 1.seconds)
    }
  }

  test("when action actor is in Error state and receive RecoverDatabaseConnection" +
         ", it should enter Initializing state") {
    objectFactoryCreateSuccess()
    testActorRef.setState(DatabaseActionActor.Error)

    testActorRef ! RecoverDatabaseConnection
  }

  test(
    "when action actor is in Error state and receive DatabaseAction, it should send back DatabaseActionResult(Failure)")
  {
    testActorRef.setState(DatabaseActionActor.Error)
    val mockCallback = stubFunction[DSLContext, Unit]
    testActorRef.tell(DatabaseAction(mockCallback), testProbe.ref)

    testProbe.expectMsgPF(timeout.duration) {
      case DatabaseActionResult(Failure(_)) =>
        succeed
      case _ =>
        fail("wrong result")
    }
  }

  test("when action actor is in Error state and receive DatabaseActionWithPromise, it should fail the promise") {
    testActorRef.setState(DatabaseActionActor.Error)
    val mockCallback = stubFunction[DSLContext, Unit]
    val promise = Promise[Unit]()
    testActorRef.tell(DatabaseActionWithPromise(promise, mockCallback), testProbe.ref)

    an[Exception] should be thrownBy (Await.result(promise.future, Duration.Inf))

  }

  test("when action actor is in Ready state, it should call keepAlive repeatly") {
    val keepAliveInterval = 1.seconds
    testActorRef.underlyingActor.dslContext = Some(mockDslContext)
    testActorRef.underlyingActor.KeepConnectionAliveInterval = keepAliveInterval
    objectFactoryKeepAliveSuccess()
    testActorRef.setState(DatabaseActionActor.Ready)

    waitFor(keepAliveInterval + 1.seconds)
    verify(mockFactory, atLeastOnce()).keepAlive(mockDslContext)
  }

  test("when action actor is in Ready state and keepAlive fail, it should go to Error state") {
    val keepAliveInterval = 5.seconds
    testActorRef.underlyingActor.dslContext = Some(mockDslContext)
    testActorRef.underlyingActor.KeepConnectionAliveInterval = keepAliveInterval
    objectFactoryKeepAliveFail()
    testActorRef.setState(DatabaseActionActor.Ready)

    waitFor(keepAliveInterval + 1.seconds)
    verify(mockFactory, atLeastOnce()).keepAlive(mockDslContext)

    testActorRef.stateName should be(DatabaseActionActor.Error)
  }

  test("when receive DatabaseAction, work actor should pass the same DSLContext to action function") {
    checkSuccess()
    val mockCallback = stubFunction[DSLContext, Unit]

    testActorRef ! RetrieveDatabaseConnection
    testActorRef.tell(DatabaseAction(mockCallback), testProbe.ref)

    mockCallback.verify(mockDslContext)
  }

  test("when receive DatabaseActionWithPromise,work actor should pass the same DSLContext to action function") {
    checkSuccess()
    val mockCallback = stubFunction[DSLContext, Unit]

    testActorRef ! RetrieveDatabaseConnection
    testActorRef.tell(DatabaseActionWithPromise(Promise[Unit](), mockCallback), testProbe.ref)

    mockCallback.verify(mockDslContext)
  }

  test("work actor should send back action function result") {
    checkSuccess()
    val result = 100
    val mockCallback = callbackFunctionReturnAnInt(result)

    testActorRef ! RetrieveDatabaseConnection
    testActorRef.tell(DatabaseAction(mockCallback), testProbe.ref)
    testProbe.expectMsgPF(timeout.duration) {
      case DatabaseActionResult(Success(x)) =>
        x should equal(result)
      case _ =>
        fail("wrong result")
    }
  }

  test("work actor should set promise with DatabaseActionWithPromise") {
    checkSuccess()
    val result = 100
    val mockCallback = callbackFunctionReturnAnInt(result)
    val promise = Promise[Int]()

    testActorRef ! RetrieveDatabaseConnection
    testActorRef ! DatabaseActionWithPromise(promise, mockCallback)

    Await.result(promise.future, Duration.Inf) should be(result)
  }

  test("work actor should send back Fail when action function throw an exception") {
    checkSuccess()

    val mockCallback = callbackFunctionThrowsException(new SQLException())

    testActorRef ! RetrieveDatabaseConnection
    testActorRef.tell(DatabaseAction(mockCallback), testProbe.ref)
    testProbe.expectMsgPF(timeout.duration) {
      case DatabaseActionResult(Failure(_: SQLException)) =>
        true
      case _ =>
        fail("wrong result type")
    }
  }

  test("work actor should fail the promise when action function throw an exception") {
    checkSuccess()

    val mockCallback = callbackFunctionThrowsException(new SQLException())
    val promise = Promise[Int]()

    testActorRef ! RetrieveDatabaseConnection
    testActorRef ! DatabaseActionWithPromise(promise, mockCallback)

    an[Exception] should be thrownBy (Await.result(promise.future, Duration.Inf))
  }

  test("when receive a DatabaseAction, actor should go to Error state when check returns Failure") {
    checkFailed()

    val replyTo = TestProbe()
    val mockCallback = callbackFunctionReturnAnInt(100)
    TestActorRef[DatabaseActionActor](Props(classOf[DatabaseActionActor], mockFactory))

    testActorRef ! RetrieveDatabaseConnection
    testActorRef.tell(DatabaseAction(mockCallback), replyTo.ref)

    //Assert
    testActorRef.underlyingActor.stateName should be(DatabaseActionActor.Error)
    testActorRef.underlyingActor.dslContext should be(None)
    replyTo.expectMsgPF() {
      case DatabaseActionResult(Failure(_)) ⇒
    }
  }

  test("when receive a DatabaseActionWithPromise, actor should go to Error state when check returns Failure") {
    checkFailed()
    val mockCallback = callbackFunctionReturnAnInt(100)
    val promise = Promise[Int]()
    TestActorRef[DatabaseActionActor](Props(classOf[DatabaseActionActor], mockFactory))

    testActorRef ! RetrieveDatabaseConnection
    testActorRef ! DatabaseActionWithPromise(promise, mockCallback)

    //Assert
    testActorRef.underlyingActor.stateName should be(DatabaseActionActor.Error)
    testActorRef.underlyingActor.dslContext should be(None)

    an[Exception] should be thrownBy (Await.result(promise.future, Duration.Inf))
  }


  test("action actor should use it's parent as sender sending back a message") {
    checkSuccess()
    val mockCallback = callbackFunctionReturnAnInt(100)
    val actorRef = testProbe.childActorOf(Props(classOf[DatabaseActionActor], mockFactory))

    actorRef ! RetrieveDatabaseConnection
    actorRef.tell(DatabaseAction(mockCallback), testProbe.ref)

    testProbe.receiveOne(timeout.duration)
    testProbe.lastSender should be theSameInstanceAs testProbe.ref
  }

  test("action actor should use it's parent as check fail") {
    checkFailed()
    val mockCallback = callbackFunctionReturnAnInt(100)
    val actorRef = testProbe.childActorOf(Props(classOf[DatabaseActionActor], mockFactory))

    actorRef ! RetrieveDatabaseConnection
    actorRef.tell(DatabaseAction(mockCallback), testProbe.ref)
    testProbe.receiveOne(timeout.duration)
    testProbe.lastSender should be theSameInstanceAs testProbe.ref
  }

  test("action actor should release database connection when it terminated") {
    objectFactoryRelease()

    testActorRef.underlyingActor.dslContext = Some(mockDslContext)
    testActorRef.setState(DatabaseActionActor.Ready)

    testActorRef.stop()
    verify(mockFactory).release(mockDslContext)
  }

  override def beforeEach() {
    mockFactory = Mockito.mock(classOf[DSLContextFactory])
    mockDslContext = Mockito.mock(classOf[DSLContext])
    objectFactoryCreateSuccess()

    testActorRef = TestFSMRef(new WrapperDatabaseActionActor(mockFactory))
    testProbe = TestProbe()
  }

  override def afterEach(): Unit = {
    mockFactory = null
    mockDslContext = null
    testActorRef = null
    testProbe = null
  }

  override def afterAll():Unit ={
    system.terminate()
  }



  private def callbackFunctionThrowsException(e: Exception) = {
    val mockCallback = stubFunction[DSLContext, Int]
    mockCallback.when(mockDslContext).throwing(e)
    mockCallback
  }

  private def callbackFunctionReturnAnInt(result: Int) = {
    val mockCallback = stubFunction[DSLContext, Int]
    mockCallback.when(mockDslContext).returns(result)
    mockCallback
  }

  private def objectFactoryCreateThrows(resetMock: Boolean = true) = {
    if (resetMock)
      reset(mockFactory)
    when(mockFactory.create()).thenReturn(Failure(new Exception))
  }

  private def objectFactoryCreateSuccess(resetMock: Boolean = true) = {
    if (resetMock)
      reset(mockFactory)
    when(mockFactory.create()).thenReturn(Success(mockDslContext))
  }

  private def objectFactoryKeepAliveSuccess(resetMock: Boolean = true) = {
    if (resetMock)
      reset(mockFactory)
    when(mockFactory.keepAlive(mockDslContext)).thenReturn(Success {})
  }

  private def objectFactoryKeepAliveFail(resetMock: Boolean = true) = {
    if (resetMock)
      reset(mockFactory)
    when(mockFactory.keepAlive(mockDslContext)).thenReturn(Failure(new Exception))
  }

  private def objectFactoryRelease(resetMock: Boolean = true) = {
    if (resetMock)
      reset(mockFactory)
    doNothing().when(mockFactory).release(mockDslContext)
  }


  private def checkSuccess(): Unit = {
    when(mockFactory.check(mockDslContext)).thenReturn(Success {
    })
  }

  private def checkFailed(): Unit = {
    when(mockFactory.check(mockDslContext)).thenReturn(Try {
      throw new Exception()
    })
  }
}

class WrapperDatabaseActionActor(dslContextFactory: DSLContextFactory) extends DatabaseActionActor(dslContextFactory) {
  var autoConnectionToDatabase = true

  override def onTransitToInitializing = {
    if (autoConnectionToDatabase)
      super.onTransitToInitializing
  }
}