package pt.tecnico.dsi.kadmin.akka

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import pt.tecnico.dsi.kadmin.akka.Kadmin._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class PolicySpec extends TestKit(ActorSystem("akka-kadmin", ConfigFactory.load()))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  val kadminActor = system.actorOf(Props(new KadminActor()))

  private var seqCounter = 0L
  def nextSeq(): Long = {
    val ret = seqCounter
    seqCounter += 1
    ret
  }

  import system.dispatcher
  implicit val timeout = Timeout(5 seconds)
  implicit val patience = PatienceConfig(
    timeout = scaled(timeout.duration),
    interval = scaled((timeout.duration / 15) max (100 millis))
  )

  "addPolicy" should "idempotently succeed" in {
    val policyName = "add"
    val minimumLength = 6
    val minimumClasses = 2

    val deleteId = nextSeq()
    val policy = for {
      delete <- (kadminActor ? DeletePolicy(policyName, deleteId)).mapTo[Successful]
      if delete.deliveryId == deleteId
      createId = nextSeq()
      create <- (kadminActor ? AddPolicy(s"-minlength $minimumLength -minclasses $minimumClasses", policyName, createId)).mapTo[Successful]
      if create.deliveryId == createId
      getId = nextSeq()
      get <- (kadminActor ? GetPolicy(policyName, getId)).mapTo[PolicyResponse]
      if get.deliveryId == getId && get.policy.name == policyName
    } yield get.policy

    whenReady(policy) { p =>
      p.minimumLength shouldBe minimumLength
      p.minimumCharacterClasses shouldBe minimumClasses
    }
  }
}
