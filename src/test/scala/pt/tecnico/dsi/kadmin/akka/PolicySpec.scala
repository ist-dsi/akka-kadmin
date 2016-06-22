package pt.tecnico.dsi.kadmin.akka

import pt.tecnico.dsi.kadmin.akka.Kadmin._

import scala.language.postfixOps

class PolicySpec extends ActorSysSpec {
  "addPolicy" should "idempotently succeed" in {
    val policyName = "add"
    val minimumLength = 6
    val minimumClasses = 2

    val deleteId = nextSeq()
    kadminActor ! DeletePolicy(policyName, deleteId)
    expectMsg(Successful(deleteId))

    val createId = nextSeq()
    kadminActor ! AddPolicy(s"-minlength $minimumLength -minclasses $minimumClasses", policyName, createId)
    expectMsg(Successful(createId))

    val getId = nextSeq()
    kadminActor ! GetPolicy(policyName, getId)
    val policy = expectMsgClass(classOf[PolicyResponse])
    policy.deliveryId shouldBe getId
    policy.policy.name shouldBe policyName
    policy.policy.minimumLength shouldBe minimumLength
    policy.policy.minimumCharacterClasses shouldBe minimumClasses
  }
}
