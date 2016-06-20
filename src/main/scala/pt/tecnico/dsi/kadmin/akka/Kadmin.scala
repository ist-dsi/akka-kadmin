package pt.tecnico.dsi.kadmin.akka

import pt.tecnico.dsi.kadmin.{ErrorCase, ExpirationDateTime, Policy, Principal}

object Kadmin {
  sealed trait Request {
    def deliveryId: Long
  }

  //If removeId is a Some then just the result for (senderPath, removeId) is removed
  //Otherwise all the results for the senderPath are removed.
  case class RemoveDeduplicationResult(removeId: Option[Long], deliveryId: Long) extends Request

  case class AddPrincipal(options: String, principal: String,
                          newPassword: Option[String] = None, randKey: Boolean = false, keysalt: Option[String] = None,
                          deliveryId: Long) extends Request
  case class ModifyPrincipal(options: String, principal: String, deliveryId: Long) extends Request
  case class ExpirePrincipal(principal: String, expirationDate: ExpirationDateTime, deliveryId: Long) extends Request
  case class ExpirePrincipalPassword(principal: String, expirationDate: ExpirationDateTime, force: Boolean = false, deliveryId: Long) extends Request
  case class GetPrincipal(principal: String, deliveryId: Long) extends Request
  case class ChangePrincipalPassword(principal: String,
                                     newPassword: Option[String] = None, randKey: Boolean = false, keysalt: Option[String] = None,
                                     deliveryId: Long) extends Request
  case class DeletePrincipal(principal: String, deliveryId: Long) extends Request
  case class CheckPrincipalPassword(principal: String, password: String, deliveryId: Long) extends Request

  case class CreateKeytab(options: String, principal: String, deliveryId: Long) extends Request
  case class ObtainKeytab(principal: String, deliveryId: Long) extends Request

  case class AddPolicy(options: String, policy: String, deliveryId: Long) extends Request
  case class ModifyPolicy(options: String, policy: String, deliveryId: Long) extends Request
  case class DeletePolicy(policy: String, deliveryId: Long) extends Request
  case class GetPolicy(policy: String, deliveryId: Long) extends Request

  sealed trait Response {
    def deliveryId: Long
  }

  sealed trait SuccessResponse extends Response
  case class Successful(deliveryId: Long) extends SuccessResponse
  case class PrincipalResponse(principal: Principal, deliveryId: Long) extends SuccessResponse
  case class PolicyResponse(policy: Policy, deliveryId: Long) extends SuccessResponse
  case class KeytabResponse(keytab: Array[Byte], deliveryId: Long) extends SuccessResponse

  sealed trait FailureResponse extends Response
  case class Failed(errorCase: ErrorCase, deliveryId: Long) extends FailureResponse
}
