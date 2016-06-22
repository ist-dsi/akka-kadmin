package pt.tecnico.dsi.kadmin.akka

import akka.actor.Actor
import pt.tecnico.dsi.kadmin.{Policy, Principal, UnknownError}
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.{Settings => KadminSettings}
import pt.tecnico.dsi.kadmin.akka.KadminActor.{Retry, SideEffectOperation, SideEffectResult}

import scala.concurrent.Await

class BlockingActor(val kadminSettings: KadminSettings) extends Actor {
  val scalaExpectTimeout = kadminSettings.scalaExpectSettings.timeout

  //We will run the expects in this ExecutionContext
  import context.dispatcher

  def receive: Receive = {
    case SideEffectOperation(recipient, deliveryId, expect) =>
      val f = expect.run() map {
        case Right(principal: Principal) => PrincipalResponse(principal, deliveryId)
        case Right(policy: Policy) => PolicyResponse(policy, deliveryId)
        case Right(()) => Successful(deliveryId)
        case Right(unexpectedType) =>
          val ex = new IllegalArgumentException(s"Got Right with unexpected type: $unexpectedType")
          //log.error(ex, ex.getMessage)
          Failed(UnknownError(Some(ex)), deliveryId)
        case Left(ec) => Failed(ec, deliveryId)
      } recover {
        //Most probably the expect failed due to a TimeoutException and there isn't a when(timeout) declared
        case t: Throwable => Failed(UnknownError(Some(t)), deliveryId)
      } map {
        SideEffectResult(recipient, deliveryId, _)
      }

      //We wait 3*scalaExpectTimeout because the expect might be composed with other expects (with returningExpect or flatMap)
      context.parent ! Await.result(f, 3 * scalaExpectTimeout)
    case r: Retry => context.parent ! r
  }
}
