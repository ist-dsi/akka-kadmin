package pt.tecnico.dsi.kadmin.akka

import pt.tecnico.dsi.kadmin.akka.Kadmin._

class DeduplicationSpec extends ActorSysSpec {
  val policyName = "deduplication"
  val principalName = "withDeduplicationPrincipal"

  "The side-effect" must {
    "only be executed once" when {
      "messages are sent in a ping-pong manner" in {
        val addPolicyId = nextSeq()
        kadminActor ! AddPolicy("-history 1", policyName, addPolicyId)
        expectMsg(Successful(addPolicyId))

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
      "messages are sent in rapid succession" in {
        val addPolicyId = nextSeq()
        kadminActor ! AddPolicy("-history 1", policyName, addPolicyId)
        val addPrincipalId = nextSeq()
        kadminActor ! AddPrincipal(s"-policy $policyName", principalName, randKey = true, deliveryId = addPrincipalId)
        val changePaswordId = nextSeq()
        val changePasswordMessage = ChangePrincipalPassword(principalName, newPassword = Some("a new password 123"), deliveryId = changePaswordId)
        kadminActor ! changePasswordMessage
        kadminActor ! changePasswordMessage

        expectMsg(Successful(addPolicyId))
        expectMsg(Successful(addPrincipalId))
        expectMsg(Successful(changePaswordId))
        expectMsg(Successful(changePaswordId)) //This only works because we are retrying.
      }
    }
    "not be executed" when {
      "a future message is sent" in {
        nextSeq() //Skip one message
        val id = nextSeq()
        kadminActor ! GetPrincipal(principalName, id)
        expectNoMsg()
      }
    }
  }
}
