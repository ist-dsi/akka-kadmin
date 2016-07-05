package pt.tecnico.dsi.kadmin.akka

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.event.LoggingReceive
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.typesafe.config.Config
import pt.tecnico.dsi.kadmin.akka.Kadmin._
import pt.tecnico.dsi.kadmin.akka.KadminActor._

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.Duration

object KadminActor {
  private case object SaveSnapshot
  private[akka] case class Retry(deliveryId: DeliveryId)
  private[akka] case class SideEffectResult(recipient: ActorRef, response: Response)
  private case class RemoveResult(recipient: ActorRef, removeId: Option[DeliveryId])
}
class KadminActor(val settings: Settings = new Settings()) extends Actor with PersistentActor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import settings._

  def persistenceId: String = "kadminActor"

  val blockingActor = context.actorOf(Props(classOf[BlockingActor], kadminSettings))
  var counter = 0
  //By using a SortedMap as opposed to a Map we can also extract the latest deliveryId per sender
  var resultsPerSender = Map.empty[ActorPath, SortedMap[DeliveryId, Option[Response]]]

  def performDeduplication(request: Request): Unit = {
    val recipient = sender()
    val senderPath = recipient.path
    val deliveryId = request.deliveryId
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
          blockingActor forward Retry(deliveryId)
      }
    } else { //deliveryId == expectedId
      logIt("==", "Going to perform deduplication.")

      //By setting the result to None we ensure a bigger throughput since we allow this actor to continue
      //processing requests. Aka the expected id will be increased and we will be able to respond to messages
      //where DeliveryId < expectedID.
      updateResult(senderPath, deliveryId, None)

      //The blockingActor will execute the expect then send us back a SideEffectResult
      blockingActor forward request

      // If we crash:
      //  · After executing the expect
      //  · But before persisting the SideEffectResult
      // then the side-effect will be performed twice.
    }
  }

  def removeResult(senderPath: ActorPath, removeId: Option[DeliveryId]): Unit = {
    resultsPerSender.get(senderPath) match {
      case None => //We dont have any entry for senderPath. All good, we don't need to do anything.
      case Some(previousResults) =>
        removeId match {
          case None => resultsPerSender -= senderPath
          case Some(id) => resultsPerSender += senderPath -> (previousResults - id)
        }
    }
  }
  def updateResult(senderPath: ActorPath, deliveryId: DeliveryId, response: Option[Response]): Unit = {
    val previousResults = resultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    resultsPerSender += senderPath -> previousResults.updated(deliveryId, response)

    if (saveSnapshotEveryXMessages > 0) {
      counter += 1
      if (counter >= saveSnapshotEveryXMessages) {
        self ! SaveSnapshot
      }
    }
  }

  def receiveCommand: Receive = LoggingReceive {
    case RemoveDeduplicationResult(removeId, deliveryId) ⇒
      persist(RemoveResult(sender(), removeId)) { remove ⇒
        if (removeDelay == Duration.Zero) {
          removeResult(remove.recipient.path, remove.removeId)
        } else {
          context.system.scheduler.scheduleOnce(removeDelay) {
            self ! remove
          }(context.dispatcher)
        }
        sender() ! Successful(deliveryId)
      }
    case RemoveResult(recipient, removeId) ⇒
      removeResult(recipient.path, removeId)

    case result @ SideEffectResult(recipient, response) ⇒
      persist(result) { _ ⇒
        updateResult(recipient.path, response.deliveryId, Some(response))
        recipient ! response
      }

    case Retry(deliveryId) ⇒
      val senderPath = sender().path
      resultsPerSender.get(senderPath).flatMap(_.get(deliveryId).flatten) match {
        case Some(result) ⇒
          log.debug(s"Retry for ($senderPath, $deliveryId): sending result: $result.")
          sender ! result
        case None ⇒
          log.debug(s"Retry for ($senderPath, $deliveryId): still no result. Most probably it was removed explicitly.")
      }

    case SaveSnapshot => saveSnapshot(resultsPerSender)

    //=================================================================================
    //==== Requests ===================================================================
    //=================================================================================
    case obtainKeytab @ ObtainKeytab(principal , deliveryId) ⇒
      //We do not want to journal keytabs so we send the ObtainKeytab directly to blockingActor.
      //Since obtainKeytab does not create keytabs there is no problem in repeating this action
      blockingActor forward obtainKeytab
    case request: Request ⇒ performDeduplication(request)
  }

  def receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(metadata, offeredSnapshot) =>
      resultsPerSender = offeredSnapshot.asInstanceOf[Map[ActorPath, SortedMap[Long, Option[Response]]]]
    case SideEffectResult(recipient, response) =>
      updateResult(recipient.path, response.deliveryId, Some(response))
    case RemoveResult(recipient, removeId) =>
      removeResult(recipient.path, removeId)
  }
}
