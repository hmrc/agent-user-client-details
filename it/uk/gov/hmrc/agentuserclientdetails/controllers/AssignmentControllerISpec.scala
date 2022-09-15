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

package uk.gov.hmrc.agentuserclientdetails.controllers

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.auth.AuthAction
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.repositories.AssignmentsWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.services.AssignmentsWorkItemServiceImpl
import uk.gov.hmrc.agentuserclientdetails.util.MongoProvider
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class AssignmentControllerISpec extends BaseIntegrationSpec with MongoSpecSupport with AuthorisationMockSupport {

  implicit val mongoProvider = MongoProvider(mongo)
  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val wir = AssignmentsWorkItemRepository(config)
  lazy val wis = new AssignmentsWorkItemServiceImpl(wir)

  implicit lazy val mockAuthConnector = mock[AuthConnector]
  implicit lazy val authAction: AuthAction = app.injector.instanceOf[AuthAction]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testUserId = "ABCEDEFGI1234568"
  val ue1 = UserEnrolment(testUserId, "HMRC-MTD-VAT~VRN~101747641")
  val ue2 = UserEnrolment(testUserId, "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345")
  val ue3 = UserEnrolment(testUserId, "HMRC-CGT-PD~CgtRef~XMCGTP123456789")
  val ue4 = UserEnrolment(testUserId, "HMRC-MTD-VAT~VRN~VRN")
  val testArn = Arn("BARN9706518")

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
  }

  "POST /assign-enrolments" should {
    "respond with 202 Accepted and add items to the queue (assign)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set(ue1, ue2, ue3, ue4), unassign = Set.empty, arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 202 Accepted and add items to the queue (unassign)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set.empty, unassign = Set(ue1, ue2, ue3, ue4), arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 202 Accepted and add items to the queue (mixed)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set(ue1), unassign = Set(ue2, ue3, ue4), arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 400 status if the request is malformed" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(Json.obj("someJson" -> JsNumber(0xbad)))
      val fnc = new AssignmentController(cc, wis, appConfig)
      val result = fnc.assignEnrolments(request)
      status(result) shouldBe 400
    }
  }
}
