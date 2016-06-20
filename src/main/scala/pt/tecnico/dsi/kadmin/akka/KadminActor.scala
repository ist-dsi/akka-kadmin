package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef}
import akka.event.LoggingReceive
import akka.pattern.pipe
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.typesafe.config.Config
import pt.tecnico.dsi.kadmin.akka.KadminActor.{RemoveResult, SaveSnapshot, SideEffectResult}
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.{ErrorCase, Policy, Principal, UnknownError, Kadmin => KadminCore}
import work.martins.simon.expect.core.Expect

import scala.concurrent.duration.Duration
import scala.collection.immutable.SortedMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util._

object KadminActor {
  private case object SaveSnapshot
  private case class SideEffectResult(senderPath: ActorRef, expectedId: Long, response: Response)
  private case class RemoveResult(senderPath: ActorRef, removeId: Option[Long])
}
class KadminActor(val settings: Settings = new Settings()) extends Actor with PersistentActor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import settings._
  //We will execute the kadmin expects in this context
  import context.dispatcher

  val kadmin = new KadminCore(kadminSettings)

  def persistenceId: String = "kadminActor"

  //By using a SortedMap as opposed to a Map we can also extract the latest id per sender
  private var previousResultsPerSender = Map.empty[ActorPath, SortedMap[Long, Option[Response]]]

  def executeExpectAndMapToResponse[R](deliveryId: Long, expect: => Expect[Either[ErrorCase, R]])
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

  def performDeduplication[R](expect: Expect[Either[ErrorCase, R]], deliveryId: Long): Unit = {
    val recipient = sender()
    val senderPath = sender().path
    val previousResults = previousResultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    val expectedId = previousResults.keySet.lastOption.map(_ + 1).getOrElse(0L)
    log.info(s"""For sender ($senderPath):
                 |ExpectedId: $expectedId
                 |Received DeliveryId: $deliveryId""".stripMargin)

    if (deliveryId > expectedId) {
      //Ignore it we are not yet ready to deal with this message
      log.info("DeliveryId > expectedID: ignoring message.")
    } else if (deliveryId < expectedId) {
      //Send the stored response, if we have it.
      //The response can already have been deleted by a Remove.
      //Or a future might still be processing it.
      log.debug("DeliveryId < expectedID: resending previously computed result (if there is any).")
      previousResults.get(deliveryId).flatten.foreach(recipient ! _)
    } else { //deliveryId == expectedId
      log.info("Going to perform deduplication")
      updateResult(senderPath, expectedId, None)

      executeExpectAndMapToResponse(deliveryId, expect)
        .map(SideEffectResult(recipient, deliveryId, _))
        .pipeTo(self)

      // If this actor crashes between the previous expect execution and the persist of the side-effect result
      // then the side-effect will be performed twice.
      // In this case we are not guaranteeing de-duplication, but we are reducing the
      // possible window for duplication to a much smaller value, thus implementing best-effort deduplication.

      // The persist and sending back the response occurs when this actor receives the piped result.
    }
  }

  def removeResult(senderPath: ActorPath, removeId: Option[Long]): Unit = {
    previousResultsPerSender.get(senderPath) match {
      case None => //We dont have any entry for senderPath. All good, we don't need to do anything.
      case Some(previousResults) =>
        removeId match {
          case None => previousResultsPerSender -= senderPath
          case Some(id) => previousResultsPerSender += senderPath -> (previousResults - id)
        }
    }
  }
  def updateResult(senderPath: ActorPath, expectedId: Long, response: Option[Response]): Unit = {
    val previousResults = previousResultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    previousResultsPerSender += senderPath -> previousResults.updated(expectedId, response)
  }

  def receiveCommand: Receive = LoggingReceive {
    case RemoveDeduplicationResult(removeId, deliveryId) =>
      persist(RemoveResult(sender(), removeId)) { remove =>
        if (settings.removeDelay == Duration.Zero) {
          removeResult(sender.path, removeId)
        } else {
          context.system.scheduler.scheduleOnce(settings.removeDelay) {
            self ! remove
          }
        }
        sender() ! Successful(deliveryId)
      }
    case RemoveResult(sender, removeId) =>
      removeResult(sender.path, removeId)

    case result @ SideEffectResult(sender, expectedId, response) =>
      persist(result) { _ =>
        updateResult(sender.path, expectedId, Some(response))
        sender ! response
      }

    //=================================================================================
    //==== Principal actions ==========================================================
    //=================================================================================
    case AddPrincipal(options, principal, newPassword, randKey, keysalt, deliveryId) =>
      performDeduplication(kadmin.addPrincipal(options, principal, newPassword, randKey, keysalt), deliveryId)
    case ModifyPrincipal(options, principal, deliveryId) =>
      performDeduplication(kadmin.modifyPrincipal(options, principal), deliveryId)
    case ExpirePrincipal(principal, expirationDate, deliveryId) =>
      performDeduplication(kadmin.expirePrincipal(principal, expirationDate), deliveryId)
    case ExpirePrincipalPassword(principal, expirationDate, force, deliveryId) =>
      performDeduplication(kadmin.expirePrincipalPassword(principal, expirationDate, force), deliveryId)
    case ChangePrincipalPassword(principal, newPassword, randKey, salt, deliveryId) =>
      performDeduplication(kadmin.changePassword(principal, newPassword, randKey, salt), deliveryId)
    case DeletePrincipal(principal, deliveryId) =>
      performDeduplication(kadmin.deletePrincipal(principal), deliveryId)
    case GetPrincipal(principal, deliveryId) =>
      performDeduplication(kadmin.getPrincipal(principal), deliveryId)
    case CheckPrincipalPassword(principal, password, deliveryId) =>
      performDeduplication(kadmin.checkPassword(principal, password), deliveryId)

    //=================================================================================
    //==== Keytab actions =============================================================
    //=================================================================================
    case CreateKeytab(options, principal , deliveryId) =>
      performDeduplication(kadmin.createKeytab(options, principal), deliveryId)
    case ObtainKeytab(principal , deliveryId) =>
      //We do not want to journal keytabs so we dont perform deduplication here
      //Since obtainKeytab does not create it there is no problem in repeating this action
      kadmin.obtainKeytab(principal) match {
        case Right(keytab) => sender() ! KeytabResponse(keytab, deliveryId)
        case Left(errorCase) => sender() ! Failed(errorCase, deliveryId)
      }
    //=================================================================================
    //==== Policy actions =============================================================
    //=================================================================================
    case AddPolicy(options, policy, deliveryId) =>
      performDeduplication(kadmin.addPolicy(options, policy), deliveryId)
    case ModifyPolicy(options, policy, deliveryId) =>
      performDeduplication(kadmin.modifyPolicy(options, policy), deliveryId)
    case DeletePolicy(policy, deliveryId) =>
      performDeduplication(kadmin.deletePolicy(policy), deliveryId)
    case GetPolicy(policy, deliveryId) =>
      performDeduplication(kadmin.getPolicy(policy), deliveryId)

    case SaveSnapshot => saveSnapshot(previousResultsPerSender)
  }

  def receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(metadata, offeredSnapshot) =>
      previousResultsPerSender = offeredSnapshot.asInstanceOf[Map[ActorPath, SortedMap[Long, Option[Response]]]]

    case SideEffectResult(sender, expectedId, response) =>
      updateResult(sender.path, expectedId, Some(response))
    case RemoveResult(sender, removeId) =>
      removeResult(sender.path, removeId)

    case RecoveryCompleted =>
      import context.dispatcher
      if (saveSnapshotInterval != Duration.Zero) {
        context.system.scheduler.schedule(saveSnapshotInterval, saveSnapshotInterval) {
          self ! SaveSnapshot
        }
      }
    //TODO: implement snapshots
  }
}
