package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import pt.tecnico.dsi.kadmin.{ErrorCase, Policy, Principal, Settings, UnknownError, Kadmin => KadminCore}
import work.martins.simon.expect.fluent.Expect
import akka.pattern.pipe
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.util._

class KadminActor(val settings: Settings = new Settings()) extends Actor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import Kadmin._
  import context.dispatcher

  val kadmin = new KadminCore(settings)

  def processExpectOutput[R](expect: => Expect[Either[ErrorCase, R]], deliveryId: Long): Unit = {
    val recipient = sender()
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
        log.error(ex, ex.getMessage)
        Failed(UnknownError(Some(ex)), deliveryId)
      case Left(ec) => Failed(ec, deliveryId)
    } recover {
      //Most probably
      // · The creation of the expect failed
      // · Or the expect failed due to a TimeoutException and there isn't a when(timeout) declared
      case t: Throwable => Failed(UnknownError(Some(t)), deliveryId)
    } pipeTo recipient
  }

  def receive: Receive = LoggingReceive {
    //=================================================================================
    //==== Principal actions ==========================================================
    //=================================================================================
    case AddPrincipal(options, principal, deliveryId) =>
      processExpectOutput(kadmin.addPrincipal(options, principal), deliveryId)
    case ModifyPrincipal(options, principal, deliveryId) =>
      processExpectOutput(kadmin.modifyPrincipal(options, principal), deliveryId)
    case ExpirePrincipal(principal, expirationDate, deliveryId) =>
      processExpectOutput(kadmin.expirePrincipal(principal, expirationDate), deliveryId)
    case ExpirePrincipalPassword(principal, expirationDate, force, deliveryId) =>
      processExpectOutput(kadmin.expirePrincipalPassword(principal, expirationDate, force), deliveryId)
    case ChangePrincipalPassword(principal, newPassword, randKey, salt, deliveryId) =>
      processExpectOutput(kadmin.changePassword(principal, newPassword, randKey, salt), deliveryId)
    case DeletePrincipal(principal, deliveryId) =>
      processExpectOutput(kadmin.deletePrincipal(principal), deliveryId)
    case GetPrincipal(principal, deliveryId) =>
      processExpectOutput(kadmin.getPrincipal(principal), deliveryId)
    case CheckPrincipalPassword(principal, password, deliveryId) =>
      processExpectOutput(kadmin.checkPassword(principal, password), deliveryId)

    //=================================================================================
    //==== Keytab actions =============================================================
    //=================================================================================
    case CreateKeytab(options, principal , deliveryId) =>
      processExpectOutput(kadmin.createKeytab(options, principal), deliveryId)
    case ObtainKeytab(principal , deliveryId) =>
      val recipient = sender()
      kadmin.obtainKeytab(principal) match {
        case Right(keytab) => recipient ! KeytabResponse(keytab, deliveryId)
        case Left(errorCase) => recipient ! Failed(errorCase, deliveryId)
      }
    //=================================================================================
    //==== Policy actions =============================================================
    //=================================================================================
    case AddPolicy(options, policy, deliveryId) =>
      processExpectOutput(kadmin.addPolicy(options, policy), deliveryId)
    case ModifyPolicy(options, policy, deliveryId) =>
      processExpectOutput(kadmin.modifyPolicy(options, policy), deliveryId)
    case DeletePolicy(policy, deliveryId) =>
      processExpectOutput(kadmin.deletePolicy(policy), deliveryId)
    case GetPolicy(policy, deliveryId) =>
      processExpectOutput(kadmin.getPolicy(policy), deliveryId)
  }
}