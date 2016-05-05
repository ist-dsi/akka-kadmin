package pt.tecnico.dsi.kadmin.akka

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalacheck.Prop._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, Matchers, PropSpecLike}
import pt.tecnico.dsi.kadmin.akka.Kadmin._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class DeliveryIdSpec extends TestKit(ActorSystem("akka-kadmin", ConfigFactory.load()))
  with PropSpecLike
  with Matchers
  with GeneratorDrivenPropertyChecks
  with BeforeAndAfterAll
  with ScalaFutures
  with ImplicitSender
  with Generators {

  def createConfigFor(principal: String) = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      principal = "$principal"
      password = "MITiys4K5"
    }""")

  val kadminActor = system.actorOf(Props(classOf[KadminActor], createConfigFor("kadmin/admin")))
  implicit val timeout = Timeout(15 seconds)
  implicit val patience = PatienceConfig(
    timeout = scaled(timeout.duration),
    interval = scaled((timeout.duration / 15) max (100 millis))
  )

  property("the deliveryId of the response must always be equal to the deliveryId of the request") {
    forAll(genRequest) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        response.deliveryId shouldBe request.deliveryId
      }
    }
  }

  property("A request that generates a Expect[Either[ErrorCase, Unit]] can only be responded with a Successful or with a Failed") {
    forAll(genUnitRequest) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        response should (
          be (a [Successful])
            or
          be (a [Failed])
        )
      }
    }
  }

  property("A GetPrincipal request can only be responded with a PrincipalResponse or with a Failed") {
    forAll(genGetPrincipal) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        collect(response.getClass.getName) {
          (response.isInstanceOf[PrincipalResponse] ?= true) || (response.isInstanceOf[Failed] ?= true)
        }
      }
    }
  }

  property("A Obtain keytab request can only be responded with a KeytabResponse or with a Failed") {
    forAll(genGetPrincipal) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        collect(response.getClass.getName) {
          (response.isInstanceOf[KeytabResponse] ?= true) || (response.isInstanceOf[Failed] ?= true)
        }
      }
    }
  }

  property("A GetPolicy request can only be responded with a PolicyResponse or with a Failed") {
    forAll(genGetPolicy) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        response should (
          be (a [PolicyResponse])
            or
          be (a [Failed])
        )
      }
    }
  }
}
