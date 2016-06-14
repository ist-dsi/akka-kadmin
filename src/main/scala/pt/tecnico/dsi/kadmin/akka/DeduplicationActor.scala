package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging, ActorPath}
import akka.event.LoggingReceive
import akka.pattern.pipe
import akka.persistence.PersistentActor
import pt.tecnico.dsi.kadmin.akka.DeduplicationActor.{RemoveResult, SideEffectResult}
import pt.tecnico.dsi.kadmin.akka.Kadmin.Response
import pt.tecnico.dsi.kadmin.{ErrorCase, Kadmin => KadminCore}
import work.martins.simon.expect.core.Expect
import scala.concurrent.duration.DurationInt
import scala.collection.immutable.SortedMap
import scala.collection.mutable
import scala.util._

object DeduplicationActor {
  private case class SideEffectResult(senderPath: ActorPath, expectedId: Long, response: Response)
  private case class RemoveResult(senderPath: ActorPath, removeId: Option[Long])
}
class DeduplicationActor(kadmin: KadminCore) extends Actor with PersistentActor with ActorLogging {
  import Kadmin._
  import context.dispatcher

  def persistenceId: String = "kadmin-deduplication-actor"

  val expectedIdsAndResponses = mutable.Map.empty[ActorPath, SortedMap[Long, Option[Response]]]

  def performDeduplication[R](deliveryId: Long)(expect: => Expect[Either[ErrorCase, R]]): Unit = {
    val senderPath = sender().path
    val idsAndResponsesForSender = expectedIdsAndResponses.getOrElseUpdate(senderPath, SortedMap.empty)
    val expectedId = idsAndResponsesForSender.lastOption.map(_._1 + 1).getOrElse(0L)

    if (deliveryId > expectedId) {
      //Ignore it we are not yet ready to deal with this message
    } else if (deliveryId < expectedId) {
      //Send the stored response, if we have it.
      //The response can already have been deleted by a Remove.
      //Or a future might still be processing it.
      idsAndResponsesForSender.get(deliveryId).flatten.foreach(sender() ! _)
    } else { //deliveryId == expectedId
      //Register that we begun the processing for this (senderPath, expectedId)
      //This is just an optimization to reduce the time window in which
      //the execution of the side-effect might run twice.
      expectedIdsAndResponses.update(senderPath, idsAndResponsesForSender.updated(expectedId, None))

      KadminActor.executeExpectAndMapToResponse(expect, expectedId)
        .map(SideEffectResult(senderPath, expectedId, _))
        .pipeTo(self)

      // If this actor crashes between the previous expect execution and the persist
      // then the side-effect will be performed twice.
      // In this case we are not guaranteeing de-duplication, but we are reducing the
      // possible window for duplication to a much smaller value.

      // The persist occurs when this actor receives the piped result.
    }
  }

  def removeResult(senderPath: ActorPath, removeId: Option[Long]): Unit = {
    expectedIdsAndResponses.get(senderPath) match {
      case None => //We dont have any entry for senderPath. All good, we don't need to do anything.
      case Some(idsAndResponsesForSender) =>
        removeId match {
          case None => expectedIdsAndResponses -= senderPath
          case Some(id) => expectedIdsAndResponses.updated(senderPath, idsAndResponsesForSender - id)
        }
    }
  }
  def updateResult(senderPath: ActorPath, expectedId: Long, response: Response): Unit = {
    val idsAndResponsesForSender = expectedIdsAndResponses.getOrElseUpdate(senderPath, SortedMap.empty)
    expectedIdsAndResponses.update(senderPath, idsAndResponsesForSender.updated(expectedId, Some(response)))
  }

  def receiveCommand: Receive = LoggingReceive {
    case RemoveDeduplicationResult(removeId, deliveryId) =>
      val senderPath = sender().path
      persist(RemoveResult(senderPath, removeId)) { remove =>
        //We only perform the remove 15 minutes after receiving it to deal with delayed messages.
        //This way we can still respond with the stored result.
        //TODO: should this time be a setting?
        context.system.scheduler.scheduleOnce(15.minutes) {
          self ! remove
        }
        sender() ! Successful(deliveryId)
      }
    case RemoveResult(senderPath, removeId) =>
      removeResult(senderPath, removeId)

    case result @ SideEffectResult(senderPath, expectedId, response) =>
      persist(result) { _ =>
        updateResult(senderPath, expectedId, response)
      }

    case AddPrincipal(options, principal, deliveryId) =>
      performDeduplication(deliveryId){
        kadmin.addPrincipal(options, principal)
      }

    case ChangePrincipalPassword(principal, newPassword, randKey, salt, deliveryId) =>
      performDeduplication(deliveryId){
        kadmin.changePassword(principal, newPassword, randKey, salt)
      }
  }

  def receiveRecover: Receive = {
    case SideEffectResult(senderPath, expectedId, response) =>
      updateResult(senderPath, expectedId, response)
    case RemoveResult(senderPath, removeId) =>
      removeResult(senderPath, removeId)
    //TODO: implement snapshots
  }
}
