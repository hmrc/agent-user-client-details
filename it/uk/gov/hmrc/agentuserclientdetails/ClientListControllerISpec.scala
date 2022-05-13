/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentuserclientdetails

import com.typesafe.config.Config
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Configuration
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.controllers.ClientListController
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, FriendlyNameWorkItem, Identifier}
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem.{PermanentlyFailed, ProcessingStatus, WorkItem}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.agentuserclientdetails.services.{WorkItemService, WorkItemServiceImpl}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClientListControllerISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterEach
  with IntegrationPatience
  with GuiceOneServerPerSuite
  with MongoSpecSupport
  with MockFactory {

  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val wir = FriendlyNameWorkItemRepository(config)
  lazy val wis = new WorkItemServiceImpl(wir)


  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val enrolment1 = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2 = Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
  val enrolment3 = Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))
  val enrolment4 = Enrolment("HMRC-MTD-VAT", "Activated", "Ross Barker", Seq(Identifier("VRN", "101747641")))
  val enrolmentsWithFriendlyNames: Seq[Enrolment] = Seq(enrolment1, enrolment2, enrolment3, enrolment4)
  val enrolmentsWithoutAnyFriendlyNames = enrolmentsWithFriendlyNames.map(_.copy(friendlyName = ""))
  val enrolmentsWithoutSomeFriendlyNames = enrolmentsWithFriendlyNames.take(2) ++ enrolmentsWithoutAnyFriendlyNames.drop(2)

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
  }

  "/groupid/:groupid/client-list" should {
    "respond with 200 status and a list of enrolments if all of the retrieved enrolments have friendly names" in {
      val esp = mock[EnrolmentStoreProxyConnector]
      (esp.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(enrolmentsWithFriendlyNames))
      val request = FakeRequest("GET", "")
      val clc = new ClientListController(cc, wis, esp, appConfig)
      val result = clc.getClientsForGroupId(testGroupId)(request).futureValue
      result.header.status shouldBe 200
      // TODO check content
    }
    "respond with 404 status if the groupId provided is unknown" in {
      val esp = mock[EnrolmentStoreProxyConnector]
      (esp.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(UpstreamErrorResponse("", 404)))
      val clc = new ClientListController(cc, wis, esp, appConfig)
      val request = FakeRequest("GET", "")
      val result = clc.getClientsForGroupId(badGroupId)(request).futureValue
      result.header.status shouldBe 404
    }
    "respond with 202 status if any of the retrieved enrolments don't have a friendly name" in {
      val esp = mock[EnrolmentStoreProxyConnector]
      (esp.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(enrolmentsWithoutSomeFriendlyNames))
      val request = FakeRequest("GET", "")
      val clc = new ClientListController(cc, wis, esp, appConfig)
      val result = clc.getClientsForGroupId(testGroupId)(request).futureValue
      result.header.status shouldBe 202
      // TODO check content
    }
    "respond with 200 status if any of the retrieved enrolments don't have a friendly name but they have been tried before and marked as permanently failed" in {
      val esp = mock[EnrolmentStoreProxyConnector]
      wis.pushNew(enrolmentsWithoutSomeFriendlyNames.filter(_.friendlyName.isEmpty).map(FriendlyNameWorkItem(testGroupId, _)), DateTime.now(), PermanentlyFailed).futureValue

      (esp.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(enrolmentsWithoutSomeFriendlyNames))
      val request = FakeRequest("GET", "")
      val clc = new ClientListController(cc, wis, esp, appConfig)
      val result = clc.getClientsForGroupId(testGroupId)(request).futureValue
      result.header.status shouldBe 200
      // TODO check content
    }
//    "add work items to the repo for any enrolments that don't have a friendly name" in {
//      ???
//    }
//    "don't add work items to the repo if they have been already added" in {
//      ???
//    }
  }
}
