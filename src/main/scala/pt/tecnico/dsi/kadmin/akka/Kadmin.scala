package pt.tecnico.dsi.kadmin.akka

import java.io.File

import pt.tecnico.dsi.kadmin.{ErrorCase, ExpirationDateTime, Policy, Principal, Ticket}

object Kadmin {
  type DeliveryId = Long

  sealed trait Request {
    def deliveryId: DeliveryId
  }

  //If removeId is a Some then just the result for (senderPath, removeId) is removed
  //Otherwise all the results for the senderPath are removed.
  case class RemoveDeduplicationResult(removeId: Option[DeliveryId], deliveryId: DeliveryId) extends Request

  case class AddPrincipal(options: String, principal: String,
                          newPassword: Option[String] = None, randKey: Boolean = false, keysalt: Option[String] = None,
                          deliveryId: DeliveryId) extends Request
  case class ModifyPrincipal(options: String, principal: String, deliveryId: DeliveryId) extends Request
  case class ExpirePrincipal(principal: String, expirationDate: ExpirationDateTime, deliveryId: DeliveryId) extends Request
  case class ExpirePrincipalPassword(principal: String, expirationDate: ExpirationDateTime, force: Boolean = false, deliveryId: DeliveryId) extends Request
  case class GetPrincipal(principal: String, deliveryId: DeliveryId) extends Request
  case class ChangePrincipalPassword(principal: String,
                                     newPassword: Option[String] = None, randKey: Boolean = false, keysalt: Option[String] = None,
                                     deliveryId: DeliveryId) extends Request
  case class DeletePrincipal(principal: String, deliveryId: DeliveryId) extends Request
  case class CheckPrincipalPassword(principal: String, password: String, deliveryId: DeliveryId) extends Request

  case class CreateKeytab(options: String, principal: String, deliveryId: DeliveryId) extends Request
  case class ObtainKeytab(principal: String, deliveryId: DeliveryId) extends Request

  case class AddPolicy(options: String, policy: String, deliveryId: DeliveryId) extends Request
  case class ModifyPolicy(options: String, policy: String, deliveryId: DeliveryId) extends Request
  case class DeletePolicy(policy: String, deliveryId: DeliveryId) extends Request
  case class GetPolicy(policy: String, deliveryId: DeliveryId) extends Request

  //The TGT will be obtained in the machine that is running the kadmin actor
  case class ObtainTGT(options: String = "", principal: String,
                       password: Option[String] = None, keytab: Option[File] = None, deliveryId: DeliveryId) extends Request
  case class ListTickets(options: String = "", deliveryId: DeliveryId) extends Request
  case class DestroyTickets(deliveryId: DeliveryId) extends Request

  sealed trait Response {
    def deliveryId: DeliveryId
  }

  trait SuccessResponse extends Response
  case class Successful(deliveryId: DeliveryId) extends SuccessResponse
  case class PrincipalResponse(principal: Principal, deliveryId: DeliveryId) extends SuccessResponse
  case class PolicyResponse(policy: Policy, deliveryId: DeliveryId) extends SuccessResponse
  case class KeytabResponse(keytab: Array[Byte], deliveryId: DeliveryId) extends SuccessResponse
  case class TicketsResponse(tickets: Seq[Ticket], deliveryId: DeliveryId) extends  SuccessResponse

  trait FailureResponse extends Response
  case class Failed(errorCase: ErrorCase, deliveryId: DeliveryId) extends FailureResponse
}
