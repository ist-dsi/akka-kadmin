package pt.tecnico.dsi.kadmin.akka

import java.util.concurrent.TimeUnit

import org.joda.time.DateTime
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Gen._
import pt.tecnico.dsi.kadmin.{AbsoluteDateTime, ExpirationDateTime, Never, Now, RelativeDateTime}
import pt.tecnico.dsi.kadmin.akka.Kadmin._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

trait Generators {
  val genRelativeDateTime = posNum[Int].map(i => Duration(i.toLong, TimeUnit.SECONDS)).map(new RelativeDateTime(_))
  val genAbsoluteDateTime = posNum[Long].map(new DateTime(_)).map(new AbsoluteDateTime(_))
  val genExpirationDateTime: Gen[ExpirationDateTime] = oneOf(const(Never), const(Now()), genRelativeDateTime, genAbsoluteDateTime)
  val genDurationString = for {
    n <- posNum[Int]
    unit <- oneOf("hours", "minutes", "seconds")
  } yield s""""$n $unit""""
  val possibleSalts = Seq("normal", "v4", "norealm", "onlyrealm", "afs3", "special")
  val genSalt = oneOf(possibleSalts)

  val possibleEncryptionTypes = Seq(
    "des-cbc-crc", "des-cbc-md4", "des-cbc-md5", "des-cbc-raw", "des-hmac-sha1", "des",
    "des3-cbc-raw", "des3-cbc-sha1", "des3-hmac-sha1", "des3-cbc-sha1-kd", "des3",
    "aes256-cts-hmac-sha1-96", "aes256-cts", "AES-256", "aes128-cts-hmac-sha1-96", "aes128-cts", "AES-128", "aes",
    "rc4-hmac", "arcfour-hmac", "arcfour-hmac-md5", "arcfour-hmac-exp", "rc4-hmac-exp", "arcfour-hmac-md5-exp", "rc4",
    "camellia256-cts-cmac", "camellia256-cts", "camellia128-cts-cmac", "camellia128-cts", "camellia"
  )
  val genEncryptionType = oneOf(possibleEncryptionTypes)

  val possibleKeysalts = for {
    encryptionType <- possibleEncryptionTypes
    salt <- possibleSalts
  } yield s"$encryptionType:$salt"
  val genKeysalt = oneOf(possibleKeysalts)
  //This way of generating keysalts ensures we don't generate repeated keysalts
  val genKeysalts = for {
    numberOfSalts <- choose(1, possibleKeysalts.size)
    salts <- pick(numberOfSalts, possibleKeysalts)
  } yield salts.mkString(start = "\"", sep = ",", end = "\"")

  val genPolicyName = alphaStr.suchThat(_.nonEmpty)
  val genPrincipalName = alphaStr.suchThat(_.nonEmpty)


  val genExpireDate = genExpirationDateTime.map(ts => s"-expire $ts")
  val genPasswordExpireDate = genExpirationDateTime.map(ts => s"-pwexpire $ts")
  val genMaxRenewLife = genDurationString.map(d => s"-maxrenewlife $d")
  val genKeyVersionNumber = posNum[Int].map(n => s"-kvno $n")
  val genPolicy = posNum[Int].map(n => s"-policy $n")
  val genClearPolicy = const("-clearpolicy")
  val genRandkey = const("-randkey")
  val genNoKey = const("-nokey")
  val genPassword = alphaStr.suchThat(_.nonEmpty).map(p => s"""-pw "$p"""")
  val genKeysaltList = genKeysalts.map(p => s"-e $p")

  val possibleAttributes = for {
    attribute <- Seq(
                   "allow_postdated", "allow_forwardable", "allow_renewable", "allow_proxiable", "allow_dup_skey",
                   "requires_preauth", "requires_hwauth", "ok_as_delegate",
                   "allow_svr", "allow_tgs_req", "allow_tix",
                   "needchange", "password_changing_service",
                   "ok_to_auth_as_delegate", "no_auth_data_required"
                 )
    allowOrDeny <- Seq("+", "-")
  } yield s"$allowOrDeny$attribute"

  val genAttributes = for {
    numberOfAttributes <- choose(1, possibleAttributes.size)
    attributes <- pick(numberOfAttributes, possibleAttributes)
  } yield attributes.mkString(" ")

  val genMinLife = genDurationString.map(ts => s"-minlife $ts")
  val genMaxLife = genDurationString.map(ts => s"-maxlife $ts")
  val genMinLength = posNum[Int].map(n => s"-minlength $n")
  val genMinClasses = choose(1, 5).map(n => s"-minclasses $n")
  val genHistory = posNum[Int].map(n => s"-history $n")
  val genMaxFailure = posNum[Int].map(n => s"-maxfailure $n")
  val genFailureCountInterval = genDurationString.map(ts => s"-failurecountinterval $ts")
  val genLockoutDuration = genDurationString.map(ts => s"-lockoutduration $ts")
  val genAllowedKeysalts = oneOf(const("-"), genKeysalts).map(s => s"-allowedkeysalts $s")
  val genUnlock = const("-unlock")


  val addPrincipalOptionsGens = Seq(genExpireDate, genPasswordExpireDate, genMaxLife, genMaxRenewLife, genKeyVersionNumber,
  genPolicy, genClearPolicy, genAttributes, genRandkey, genNoKey, genPassword, genKeysaltList)
  val genAddPrincipalOptions: Gen[String] = for {
    someOptions <- someOf(addPrincipalOptionsGens)
    options <- sequence(someOptions)
  } yield options.asScala.mkString(" ")
  val genAddPrincipal = for {
    options <- genAddPrincipalOptions
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield AddPrincipal(options, principal, deliveryId)
  val genModifyPrincipalOptions: Gen[String] = for {
    someOptions <- someOf(genUnlock +: addPrincipalOptionsGens)
    options <- sequence(someOptions)
  } yield options.asScala.mkString(" ")
  val genModifyPrincipal = for {
    options <- genModifyPrincipalOptions
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield ModifyPrincipal(options, principal, deliveryId)
  val genExpirePrincipal = for {
    principal <- genPrincipalName
    expirationDate <- genExpirationDateTime
    deliveryId <- arbitrary[Long]
  } yield ExpirePrincipal(principal, expirationDate, deliveryId)
  val genExpirePrincipalPassword = for {
    principal <- genPrincipalName
    expirationDate <- genExpirationDateTime
    force <- arbitrary[Boolean]
    deliveryId <- arbitrary[Long]
  } yield ExpirePrincipalPassword(principal, expirationDate, force, deliveryId)
  val genGetPrincipal = for {
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield GetPrincipal(principal, deliveryId)
  val genDeletePrincipal = for {
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield DeletePrincipal(principal, deliveryId)
  val genCheckPrincipalPassword = for {
    principal <- genPrincipalName
    password <- arbitrary[String]
    deliveryId <- arbitrary[Long]
  } yield CheckPrincipalPassword(principal, password, deliveryId)
  val genChangePrincipalPassword = for {
    principal <- genPrincipalName
    newPassword <- option(alphaStr.suchThat(_.nonEmpty))
    randKey <- arbitrary[Boolean]
    keysalt <- option(genKeysalt)
    deliveryId <- arbitrary[Long]
  } yield ChangePrincipalPassword(principal, newPassword, randKey, keysalt, deliveryId)

  val genPrincipalRequest: Gen[Request] = oneOf(genAddPrincipal, genModifyPrincipal, genExpirePrincipal, genExpirePrincipalPassword,
    genGetPrincipal, genDeletePrincipal, genCheckPrincipalPassword, genChangePrincipalPassword)

  val genCreateKeytab = for {
    options <- genKeysaltList
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield CreateKeytab(options, principal, deliveryId)
  val genObtainKeytab = for {
    principal <- genPrincipalName
    deliveryId <- arbitrary[Long]
  } yield ObtainKeytab(principal, deliveryId)
  val genKeytabRequest: Gen[Request] = oneOf(genCreateKeytab, genObtainKeytab)

  val genPolicyOptions = for {
    someOptions <- someOf(List(genMinLife, genMaxLife, genMinLength,
      genMinClasses, genHistory, genMaxFailure, genFailureCountInterval, genLockoutDuration, genAllowedKeysalts))
    //TODO: ensure minLife <= maxLife
    options <- sequence(someOptions)
  } yield options.asScala.mkString(" ")
  val genAddPolicy = for {
    options <- genPolicyOptions
    policy <- genPolicyName
    deliveryId <- arbitrary[Long]
  } yield AddPolicy(options, policy, deliveryId)
  val genModifyPolicy = for {
    options <- genPolicyOptions
    policy <- genPolicyName
    deliveryId <- arbitrary[Long]
  } yield ModifyPolicy(options, policy, deliveryId)
  val genDeletePolicy = for {
    policy <- genPolicyName
    deliveryId <- arbitrary[Long]
  } yield DeletePolicy(policy, deliveryId)
  val genGetPolicy = for {
    policy <- genPolicyName
    deliveryId <- arbitrary[Long]
  } yield GetPolicy(policy, deliveryId)

  val genPolicyRequest: Gen[Request] = oneOf(genAddPolicy, genModifyPolicy, genDeletePolicy, genGetPolicy)

  val genRequest: Gen[Request] = oneOf(genPrincipalRequest, genKeytabRequest, genPolicyRequest)


  def genUnitRequest: Gen[Request] = oneOf(
    genAddPrincipal, genModifyPrincipal, genExpirePrincipal, genExpirePrincipalPassword,
    genDeletePrincipal, genCheckPrincipalPassword, genChangePrincipalPassword,
    genAddPolicy, genModifyPolicy, genDeletePolicy
  )
}
