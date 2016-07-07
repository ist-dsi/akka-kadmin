package pt.tecnico.dsi.kadmin.akka

import pt.tecnico.dsi.kadmin.akka.Kadmin._

class RemoveAndResendSpec extends ActorSysSpec {
  val principalName = "add"
  val requests = Seq(
    DeletePrincipal(principalName, nextSeq()),
    AddPrincipal("", principalName, randKey = true, deliveryId = nextSeq()),
    GetPrincipal(principalName, nextSeq())
  )
  var responses = Seq.empty[Response]

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    responses = requests.map { request ⇒
      kadminActor ! request
      val msg = expectMsgClass(classOf[Response])
      msg.deliveryId shouldBe request.deliveryId
      msg
    }
  }


  "resend" should "succeed".in {
    kadminActor ! requests(1)
    val msg = expectMsgClass(classOf[Response])
    msg shouldEqual responses(1)
  }

  "remove" should "succeed".in {
    //This test assumes the remove-delay is set to 0

    val removeId = nextSeq()
    kadminActor ! RemoveDeduplicationResult(Some(requests(1).deliveryId), removeId)
    expectMsg(Successful(removeId))

    kadminActor ! requests(1)
    expectNoMsg()

    kadminActor ! requests(2)
    expectMsgClass(classOf[Response]) shouldEqual responses(2)
  }

  "remove all" should "succeed".in {
    //This test assumes the remove-delay is set to 0

    val removeId = nextSeq()
    kadminActor ! RemoveDeduplicationResult(None, removeId)
    expectMsg(Successful(removeId))

    kadminActor ! requests(1)
    expectNoMsg()
    kadminActor ! requests(2)
    expectNoMsg()
  }
}
