package pt.tecnico.dsi.kadmin.akka

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

abstract class ActorSysSpec extends TestKit(ActorSystem("akka-kadmin", ConfigFactory.load()))
  with Matchers
  with ImplicitSender
  with FlatSpecLike
  with BeforeAndAfterAll
  with LazyLogging {

  implicit val timeout = Timeout(30.seconds)
  val kadminActor = system.actorOf(Props(new KadminActor()), "kadmin")

  private var seqCounter = 0L
  def nextSeq(): Long = {
    val ret = seqCounter
    seqCounter += 1
    ret
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.stop(kadminActor)
    //It seems the only way for levelDB to release the lock is by terminating the system.
    Await.result(system.terminate(), Duration.Inf)
  }
}
