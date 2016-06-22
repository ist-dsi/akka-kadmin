package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.event.LoggingReceive
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.typesafe.config.Config
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.akka.KadminActor._
import pt.tecnico.dsi.kadmin.{ErrorCase, UnknownError, Kadmin => KadminCore}
import work.martins.simon.expect.core.Expect

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.Duration
import scala.util._

object KadminActor {
  private case object SaveSnapshot
  private[akka] case class Retry(sender: ActorRef, expectedId: Long)
  private[akka] case class SideEffectOperation[R](sender: ActorRef, expectedId: Long, operation: Expect[Either[ErrorCase, R]])
  private[akka] case class SideEffectResult(sender: ActorRef, expectedId: Long, response: Response)
  private case class RemoveResult(sender: ActorRef, removeId: Option[Long])
}
class KadminActor(val settings: Settings = new Settings()) extends Actor with PersistentActor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import settings._
  val kadmin = new KadminCore(kadminSettings)
  val blockingActor = context.actorOf(Props(classOf[BlockingActor], kadminSettings))
  var counter = 0

  def persistenceId: String = "kadminActor"

  //By using a SortedMap as opposed to a Map we can also extract the latest id per sender
  private var resultsPerSender = Map.empty[ActorPath, SortedMap[Long, Option[Response]]]

  def performDeduplication[R](expect: Expect[Either[ErrorCase, R]], deliveryId: Long): Unit = {
    val recipient = sender()
    val senderPath = sender().path
    val previousResults = resultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    val expectedId = previousResults.keySet.lastOption.map(_ + 1).getOrElse(0L)

    def logIt(op: String, description: String): Unit = {
      log.debug(s"""Sender: $senderPath
                  |DeliveryId ($deliveryId) $op ExpectedId ($expectedId)
                  |$description""".stripMargin)
    }
    if (deliveryId > expectedId) {
      logIt(">", "Ignoring message.")
    } else if (deliveryId < expectedId) {
      previousResults.get(deliveryId).flatten match {
        case Some(result) =>
          logIt("<", s"Resending previously computed result: $result.")
          recipient ! result
        case None =>
          logIt("<", "There is no previously computed result. " +
            s"Probably it is still being computed. Going to retry.")
          //We schedule the retry by sending it to the blockingActor, which in turn will send it back to us.
          //This strategy as a few advantages:
          // · The retry will only be processed in the blockingActor after the previous expects are executed.
          //   This is helpful since the result in which we are interested will likely be obtained by executing
          //   one of the previous expects.
          // · This actor will only receive the Retry after the SideEffectResults of the previous expects.
          //   Or in other words, the Retry will only be processed after the results are persisted and updated in the
          //   resultsPerSender, guaranteeing we have the result (if it was not explicitly removed).
          blockingActor ! Retry(recipient, deliveryId)
      }
    } else { //deliveryId == expectedId
      logIt("==", "Going to perform deduplication.")

      //By setting the result to None we ensure a bigger throughput since we allow this actor to continue
      //processing requests. Aka the expected id will be increased and we will be able to respond to messages
      //where DeliveryId < expectedID.
      updateResult(senderPath, deliveryId, None)

      Try {
        //The expect creation might fail if the arguments to the operation are invalid.
        expect
      } match {
        case Success(e) =>
          blockingActor ! SideEffectOperation(recipient, deliveryId, e)
        case Failure(t) =>
          self ! SideEffectResult(recipient, deliveryId, Failed(UnknownError(Some(t)), deliveryId))
      }
      // If this actor or the blockingActor crashes:
      //  · After executing the expect
      //  · But before persisting the SideEffectResult
      // then the side-effect will be performed twice.
      //
      // However since almost every operation in kadmin is idempotent we get effectively exactly-once processing.
      // The only exception to this is ChangePassword and AddPrincipal (since it might call ChangePassword).
      // When these operations are not idempotent we cannot perform deduplication.

      // The persist and sending back the response occurs when this actor receives the SideEffectResult from the blockingActor.
    }
  }

  def removeResult(senderPath: ActorPath, removeId: Option[Long]): Unit = {
    resultsPerSender.get(senderPath) match {
      case None => //We dont have any entry for senderPath. All good, we don't need to do anything.
      case Some(previousResults) =>
        removeId match {
          case None => resultsPerSender -= senderPath
          case Some(id) => resultsPerSender += senderPath -> (previousResults - id)
        }
    }
  }
  def updateResult(senderPath: ActorPath, expectedId: Long, response: Option[Response]): Unit = {
    val previousResults = resultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    resultsPerSender += senderPath -> previousResults.updated(expectedId, response)

    if (saveSnapshotEveryXMessages > 0) {
      counter += 1
      if (counter == saveSnapshotEveryXMessages) {
        self ! SaveSnapshot
      }
    }
  }

  def receiveCommand: Receive = LoggingReceive {
    case RemoveDeduplicationResult(removeId, deliveryId) =>
      persist(RemoveResult(sender(), removeId)) { remove =>
        if (removeDelay == Duration.Zero) {
          removeResult(sender.path, removeId)
        } else {
          context.system.scheduler.scheduleOnce(removeDelay) {
            self ! remove
          }(context.dispatcher)
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

    case Retry(sender, deliveryId) =>
      val senderPath = sender.path
      resultsPerSender.get(senderPath).flatMap(_.get(deliveryId).flatten) match {
        case Some(result) =>
          log.debug(s"Retry for ($senderPath, $deliveryId): sending result: $result.")
          sender ! result
        case None =>
          log.debug(s"Retry for ($senderPath, $deliveryId): still no result. Most probably it was removed explicitly.")
      }

    case SaveSnapshot => saveSnapshot(resultsPerSender)

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
  }

  def receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(metadata, offeredSnapshot) =>
      resultsPerSender = offeredSnapshot.asInstanceOf[Map[ActorPath, SortedMap[Long, Option[Response]]]]

    case SideEffectResult(sender, expectedId, response) =>
      updateResult(sender.path, expectedId, Some(response))
    case RemoveResult(sender, removeId) =>
      removeResult(sender.path, removeId)
  }
}
