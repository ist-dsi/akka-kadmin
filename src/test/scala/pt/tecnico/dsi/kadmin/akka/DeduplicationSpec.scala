package pt.tecnico.dsi.kadmin.akka

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import pt.tecnico.dsi.kadmin.akka.Kadmin._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class DeduplicationSpec extends TestKit(ActorSystem("akka-kadmin", ConfigFactory.load()))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender
  with LazyLogging {

  val kadminActor = system.actorOf(Props(new KadminActor()))

  var _seqCounter = 0L
  def nextSeq(): Long = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }
  implicit val timeout = Timeout(30.seconds)
  "The side-effect" should "be executed only once" in {
    logger.info(s"TestActorPath: ${testActor.path}")
    val policyName = "deduplication"
    val addPolicyId = nextSeq()
    kadminActor ! AddPolicy("-history 1", policyName, addPolicyId)
    expectMsg(Successful(addPolicyId))

    val principalName = "withDeduplicationPrincipal"
    val addPrincipalId = nextSeq()
    kadminActor ! AddPrincipal(s"-policy $policyName", principalName, randKey = true, deliveryId = addPrincipalId)
    expectMsg(Successful(addPrincipalId))

    val changePaswordId = nextSeq()
    val changePasswordMessage = ChangePrincipalPassword(principalName, newPassword = Some("abcABC123"), deliveryId = changePaswordId)
    kadminActor ! changePasswordMessage
    expectMsg(Successful(changePaswordId))

    kadminActor ! changePasswordMessage
    expectMsg(Successful(changePaswordId))
  }
}
