package pt.tecnico.dsi.kadmin.akka

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import akka.testkit.TestActorRef

class PolicySpec extends TestKit(ActorSystem("akka-kadmin", ConfigFactory.load()))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  /*def createConfigFor(principal: String) = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      authenticating-principal = "$principal"
      authenticating-principal-password = "MITiys4K5"
    }""")

  val kadminActor = system.actorOf(Props(classOf[KadminActor], createConfigFor("kadmin/admin")))

  var _seqCounter = 0L
  def nextSeq(): Long = {
    val ret = _seqCounter
    _seqCounter += 1
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

    val policy = for {
      delete <- (kadminActor ? DeletePolicy(policyName, 1L)).mapTo[Successful]
      if delete.deliveryId == 1L
      create <- (kadminActor ? AddPolicy(s"-minlength $minimumLength -minclasses $minimumClasses", policyName, 2L)).mapTo[Successful]
      if create.deliveryId == 2L
      get <- (kadminActor ? GetPolicy(policyName, 3L)).mapTo[PolicyResponse]
      if get.deliveryId == 3L && get.policy.name == policyName
    } yield get.policy

    whenReady(policy) { p =>
      p.minimumLength shouldBe minimumLength
      p.minimumCharacterClasses shouldBe minimumClasses
    }
  }*/
}
