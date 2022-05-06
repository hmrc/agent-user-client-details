package uk.gov.hmrc.agentuserclientdetails

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

class ClientListControllerISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with GuiceOneServerPerSuite {

  "/groupid/:groupid/client-list" should {
    "respond with 200 status and a list of enrolments if all of the retrieved enrolments have friendly names" in {
      ???
    }
    "respond with 202 status if any of the retrieved enrolments don't have a friendly name" in {
      ???
    }
    "respond with 200 status if any of the retrieved enrolments don't have a friendly name but they have been tried before and marked as permanently failed" in {
      ???
    }
    "add work items to the repo for any enrolments that don't have a friendly name" in {
      ???
    }
    "don't add work items to the repo if they have been already added" in {
      ???
    }
  }
}
