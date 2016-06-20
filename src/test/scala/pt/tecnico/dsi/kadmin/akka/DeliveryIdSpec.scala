package pt.tecnico.dsi.kadmin.akka

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
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

  val kadminActor = system.actorOf(Props(new KadminActor()))
  implicit val timeout = Timeout(15 seconds)
  implicit val patience = PatienceConfig(
    timeout = scaled(timeout.duration),
    interval = scaled((timeout.duration / 15) max (100 millis))
  )


  def requestResponseForAll(gen: Gen[Request])(test: (Request, Response) => Unit): Unit = {
    forAll(gen) { request: Request =>
      val future = (kadminActor ? request).mapTo[Response]
      whenReady(future){ response: Response =>
        test(request, response)
      }
    }
  }

  implicit class RichResponse(response: Response) {
    def shouldBeFailedOr[T <: Response: Manifest]: Unit = {
      response should (
        be (a [Failed])
          or
        be (a [T])
      )
    }
  }

  property("the deliveryId of the response must always be equal to the deliveryId of the request") {
    requestResponseForAll(genRequest){ case (request, response) =>
      response.deliveryId shouldBe request.deliveryId
    }
  }
  /*
  property("A request that generates a Expect[Either[ErrorCase, Unit]] can only be responded with a Successful or with a Failed") {
    requestResponseForAll(genUnitRequest){ case (_, response) =>
      response.shouldBeFailedOr[Successful]
    }
  }

  property("A GetPrincipal request can only be responded with a PrincipalResponse or with a Failed") {
    requestResponseForAll(genGetPrincipal){ case (_, response) =>
      response.shouldBeFailedOr[PrincipalResponse]
    }
  }

  property("A ObtainKeytab request can only be responded with a KeytabResponse or with a Failed") {
    requestResponseForAll(genObtainKeytab){ case (_, response) =>
      response.shouldBeFailedOr[KeytabResponse]
    }
  }

  property("A GetPolicy request can only be responded with a PolicyResponse or with a Failed") {
    requestResponseForAll(genGetPolicy){ case (_, response) =>
      response.shouldBeFailedOr[PolicyResponse]
    }
  }
  */
}
