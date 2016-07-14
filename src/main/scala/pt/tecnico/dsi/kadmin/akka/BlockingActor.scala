package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging}
import pt.tecnico.dsi.kadmin.{Kadmin ⇒ KadminCore, Settings ⇒ KadminSettings, _}
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.akka.KadminActor.{Retry, SideEffectResult}
import work.martins.simon.expect.core.Expect

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class BlockingActor(val kadminSettings: KadminSettings) extends Actor with ActorLogging {
  val scalaExpectTimeout = kadminSettings.scalaExpectSettings.timeout

  //We will run the expects in this ExecutionContext
  import context.dispatcher

  val kadmin = new KadminCore(kadminSettings)

  def runExpect[R](deliveryId: DeliveryId, expect: ⇒ Expect[R]): Unit = {
    Try {
      //The expect creation might fail if the arguments to the operation are invalid.
      expect
    } match {
      case Success(e) =>
        val f = e.run() map {
          case Right(principal: Principal) => PrincipalResponse(principal, deliveryId)
          case Right(policy: Policy) => PolicyResponse(policy, deliveryId)
          case Right(()) | () => Successful(deliveryId)
          case Left(ec: ErrorCase) => Failed(ec, deliveryId)
          case tickets: Seq[_] ⇒ TicketsResponse(tickets.asInstanceOf[Seq[Ticket]], deliveryId)
        } recover {
          //Most probably the expect failed due to a TimeoutException and there isn't a when(timeout) declared
          case t: Throwable => Failed(UnknownError(Some(t)), deliveryId)
        }

        //We wait 3*scalaExpectTimeout because the expect might be composed with other expects (with returningExpect or flatMap)
        context.parent ! SideEffectResult(sender(), Await.result(f, 3 * scalaExpectTimeout))
      case Failure(t) =>
        context.parent ! SideEffectResult(sender(), Failed(UnknownError(Some(t)), deliveryId))
    }
  }


  def receive: Receive = {
    case r: Retry => context.parent forward r

    //=================================================================================
    //==== Principal actions ==========================================================
    //=================================================================================
    case AddPrincipal(options, principal, newPassword, randKey, keysalt, deliveryId) =>
      runExpect(deliveryId, kadmin.addPrincipal(options, principal, newPassword, randKey, keysalt))
    case ModifyPrincipal(options, principal, deliveryId) =>
      runExpect(deliveryId, kadmin.modifyPrincipal(options, principal))
    case ExpirePrincipal(principal, expirationDate, deliveryId) =>
      runExpect(deliveryId, kadmin.expirePrincipal(principal, expirationDate))
    case ExpirePrincipalPassword(principal, expirationDate, force, deliveryId) =>
      runExpect(deliveryId, kadmin.expirePrincipalPassword(principal, expirationDate, force))
    case ChangePrincipalPassword(principal, newPassword, randKey, salt, deliveryId) =>
      runExpect(deliveryId, kadmin.changePassword(principal, newPassword, randKey, salt))
    case DeletePrincipal(principal, deliveryId) =>
      runExpect(deliveryId, kadmin.deletePrincipal(principal))
    case GetPrincipal(principal, deliveryId) =>
      runExpect(deliveryId, kadmin.getPrincipal(principal))
    case CheckPrincipalPassword(principal, password, deliveryId) =>
      runExpect(deliveryId, kadmin.checkPassword(principal, password))

    //=================================================================================
    //==== Keytab actions =============================================================
    //=================================================================================
    case CreateKeytab(options, principal , deliveryId) =>
      runExpect(deliveryId, kadmin.createKeytab(options, principal))
    case obtainKeytab @ ObtainKeytab(principal , deliveryId) ⇒
      kadmin.obtainKeytab(principal) match {
        case Right(keytab) => sender() ! KeytabResponse(keytab, deliveryId)
        case Left(errorCase) => sender() ! Failed(errorCase, deliveryId)
      }

    //=================================================================================
    //==== Policy actions =============================================================
    //=================================================================================
    case AddPolicy(options, policy, deliveryId) =>
      runExpect(deliveryId, kadmin.addPolicy(options, policy))
    case ModifyPolicy(options, policy, deliveryId) =>
      runExpect(deliveryId, kadmin.modifyPolicy(options, policy))
    case DeletePolicy(policy, deliveryId) =>
      runExpect(deliveryId, kadmin.deletePolicy(policy))
    case GetPolicy(policy, deliveryId) =>
      runExpect(deliveryId, kadmin.getPolicy(policy))

    //=================================================================================
    //==== Tickets actions ============================================================
    //=================================================================================
    case ObtainTGT(options, principal, password, keytab, deliveryId) ⇒
      runExpect(deliveryId, KadminUtils.obtainTGT(options, principal, password, keytab))
    case ListTickets(options, deliveryId) ⇒
      runExpect(deliveryId, KadminUtils.listTickets(options))
    case DestroyTickets(deliveryId) ⇒
      runExpect(deliveryId, KadminUtils.destroyTickets())
  }
}
