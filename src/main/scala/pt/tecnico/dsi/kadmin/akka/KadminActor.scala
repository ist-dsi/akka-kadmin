package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.pattern.pipe
import com.typesafe.config.Config
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.{ErrorCase, Policy, Principal, UnknownError, Kadmin => KadminCore}
import work.martins.simon.expect.core.Expect

import scala.concurrent.{ExecutionContext, Future}
import scala.util._

object KadminActor {
  def executeExpectAndMapToResponse[R](expect: => Expect[Either[ErrorCase, R]], deliveryId: Long)
                                      (implicit ex: ExecutionContext): Future[Response] = {
    Future {
      expect
    } flatMap {
      _.run()
    } map {
      case Right(principal: Principal) => PrincipalResponse(principal, deliveryId)
      case Right(policy: Policy) => PolicyResponse(policy, deliveryId)
      case Right(()) => Successful(deliveryId)
      case Right(unexpectedType) =>
        val ex = new IllegalArgumentException(s"Got Right with unexpected type: $unexpectedType")
        //log.error(ex, ex.getMessage)
        Failed(UnknownError(Some(ex)), deliveryId)
      case Left(ec) => Failed(ec, deliveryId)
    } recover {
      //Most probably
      // · The creation of the expect failed
      // · Or the expect failed due to a TimeoutException and there isn't a when(timeout) declared
      case t: Throwable => Failed(UnknownError(Some(t)), deliveryId)
    }
  }
}

class KadminActor(val settings: Settings = new Settings()) extends Actor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import Kadmin._
  import context.dispatcher

  val kadmin = new KadminCore()

  val deduplicationActor = if (settings.performDeduplication) {
    Some(context.actorOf(Props(classOf[DeduplicationActor], kadmin)))
  } else {
    None
  }

  def processExpect[R](expect: => Expect[Either[ErrorCase, R]], deliveryId: Long): Unit = {
    KadminActor.executeExpectAndMapToResponse(expect, deliveryId) pipeTo sender()
  }

  def receive: Receive = LoggingReceive {
    //=================================================================================
    //==== Principal actions ==========================================================
    //=================================================================================
    case m @ AddPrincipal(options, principal, deliveryId) =>
      deduplicationActor match {
        case Some(actor) => actor forward m
        case None => processExpect(kadmin.addPrincipal(options, principal), deliveryId)
      }
    case ModifyPrincipal(options, principal, deliveryId) =>
      processExpect(kadmin.modifyPrincipal(options, principal), deliveryId)
    case ExpirePrincipal(principal, expirationDate, deliveryId) =>
      processExpect(kadmin.expirePrincipal(principal, expirationDate), deliveryId)
    case ExpirePrincipalPassword(principal, expirationDate, force, deliveryId) =>
      processExpect(kadmin.expirePrincipalPassword(principal, expirationDate, force), deliveryId)

    case m @ ChangePrincipalPassword(principal, newPassword, randKey, salt, deliveryId) =>
      deduplicationActor match {
        case Some(actor) => actor forward m
        case None => processExpect(kadmin.changePassword(principal, newPassword, randKey, salt), deliveryId)
      }
    case DeletePrincipal(principal, deliveryId) =>
      processExpect(kadmin.deletePrincipal(principal), deliveryId)
    case GetPrincipal(principal, deliveryId) =>
      processExpect(kadmin.getPrincipal(principal), deliveryId)
    case CheckPrincipalPassword(principal, password, deliveryId) =>
      processExpect(kadmin.checkPassword(principal, password), deliveryId)

    //=================================================================================
    //==== Keytab actions =============================================================
    //=================================================================================
    case CreateKeytab(options, principal , deliveryId) =>
      processExpect(kadmin.createKeytab(options, principal), deliveryId)
    case ObtainKeytab(principal , deliveryId) =>
      kadmin.obtainKeytab(principal) match {
        case Right(keytab) => sender() ! KeytabResponse(keytab, deliveryId)
        case Left(errorCase) => sender() ! Failed(errorCase, deliveryId)
      }
    //=================================================================================
    //==== Policy actions =============================================================
    //=================================================================================
    case AddPolicy(options, policy, deliveryId) =>
      processExpect(kadmin.addPolicy(options, policy), deliveryId)
    case ModifyPolicy(options, policy, deliveryId) =>
      processExpect(kadmin.modifyPolicy(options, policy), deliveryId)
    case DeletePolicy(policy, deliveryId) =>
      processExpect(kadmin.deletePolicy(policy), deliveryId)
    case GetPolicy(policy, deliveryId) =>
      processExpect(kadmin.getPolicy(policy), deliveryId)
  }
}