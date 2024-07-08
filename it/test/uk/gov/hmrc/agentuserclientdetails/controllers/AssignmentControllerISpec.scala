/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Enrolment, EnrolmentKey, Identifier}
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.auth.AuthAction
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, Unassign}
import uk.gov.hmrc.agentuserclientdetails.repositories.AssignmentsWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.services.AssignmentsWorkItemServiceImpl
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.WorkItem

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AssignmentControllerISpec
    extends BaseIntegrationSpec with DefaultPlayMongoRepositorySupport[WorkItem[AssignmentWorkItem]]
    with AuthorisationMockSupport {

  override protected val repository: PlayMongoRepository[WorkItem[AssignmentWorkItem]] = wir

  val arn: Arn = Arn("KARN0762398")

  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val wir = AssignmentsWorkItemRepository(config, mongoComponent)
  lazy val wis = new AssignmentsWorkItemServiceImpl(wir, appConfig)
  lazy val esp = mock[EnrolmentStoreProxyConnector]

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
    dropCollection()
  }

  "POST /assign-enrolments" should {
    "respond with 202 Accepted and add items to the queue (assign)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set(ue1, ue2, ue3, ue4), unassign = Set.empty, arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, esp, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 202 Accepted and add items to the queue (unassign)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set.empty, unassign = Set(ue1, ue2, ue3, ue4), arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, esp, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 202 Accepted and add items to the queue (mixed)" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(
        Json.toJson(UserEnrolmentAssignments(assign = Set(ue1), unassign = Set(ue2, ue3, ue4), arn = testArn))
      )
      val fnc = new AssignmentController(cc, wis, esp, appConfig)
      val result = fnc.assignEnrolments(request: Request[JsValue])
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 4
    }
    "respond with 400 status if the request is malformed" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(Json.obj("someJson" -> JsNumber(0xbad)))
      val fnc = new AssignmentController(cc, wis, esp, appConfig)
      val result = fnc.assignEnrolments(request)
      status(result) shouldBe 400
    }
  }

  "POST /arn/:arn/user/:userId/ensure-assignments" should {
    "respond with 200 if the client is already in sync (already has exactly the given enrolments assigned)" in {
      val userId = "myUser"
      val enrolments = Seq(
        Enrolment("HMRC-MTD-VAT", "Activated", "Friendly 1", Seq(Identifier("VRN", "123456789"))),
        Enrolment(
          "HMRC-PPT-ORG",
          "Activated",
          "Friendly 2",
          Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
        )
      )
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val stubEsp = stub[EnrolmentStoreProxyConnector]
      (stubEsp
        .getEnrolmentsAssignedToUser(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(userId, *, *)
        .returns(Future.successful(enrolments))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolments.map(EnrolmentKey.fromEnrolment)))
      val ac = new AssignmentController(cc, wis, stubEsp, appConfig)
      val result = ac.ensureAssignments(arn, "myUser")(request)
      status(result) shouldBe 200
      wis.collectStats.futureValue.values.sum shouldBe 0 // no work items should be created
    }

    "respond with 202 if changes are needed and add items in the queue to effect the desired change" in {
      val userId = "myUser"
      val storedEnrolments = Seq(
        Enrolment("HMRC-MTD-VAT", "Activated", "Client1", Seq(Identifier("VRN", "123456789"))),
        Enrolment("HMRC-PPT-ORG", "Activated", "Client2", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
      )
      val wantedEnrolments = Seq(
        Enrolment("HMRC-MTD-VAT", "Activated", "Client1", Seq(Identifier("VRN", "123456789"))),
        Enrolment("HMRC-MTD-IT", "Activated", "Client3", Seq(Identifier("MTDITID", "NKZJ31383072521")))
      )
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val stubEsp = stub[EnrolmentStoreProxyConnector]
      (stubEsp
        .getEnrolmentsAssignedToUser(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(userId, *, *)
        .returns(Future.successful(storedEnrolments))
      val request =
        FakeRequest("POST", "").withBody(Json.toJson(wantedEnrolments.map(EnrolmentKey.fromEnrolment)))
      val ac = new AssignmentController(cc, wis, stubEsp, appConfig)
      val result = ac.ensureAssignments(arn, "myUser")(request)
      status(result) shouldBe 202
      wis.collectStats.futureValue.values.sum shouldBe 2 // one add, one delete
      wir.collection.find(Filters.empty()).toFuture().futureValue.map(_.item).toSet shouldBe Set(
        AssignmentWorkItem(Assign, "myUser", "HMRC-MTD-IT~MTDITID~NKZJ31383072521", arn.value),
        AssignmentWorkItem(Unassign, "myUser", "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", arn.value)
      )
    }

    "respond with 404 status if userId is unknown" in {
      val userId = "unknownUser"
      val enrolments = Seq(
        Enrolment("HMRC-MTD-VAT", "Activated", "Client1", Seq(Identifier("VRN", "123456789"))),
        Enrolment("HMRC-PPT-ORG", "Activated", "Client2", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
      )
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val stubEsp = stub[EnrolmentStoreProxyConnector]
      (stubEsp
        .getEnrolmentsAssignedToUser(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(userId, *, *)
        .returns(Future.failed(new NotFoundException("")))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolments.map(EnrolmentKey.fromEnrolment)))
      val ac = new AssignmentController(cc, wis, stubEsp, appConfig)
      val result = ac.ensureAssignments(arn, "unknownUser")(request)
      status(result) shouldBe 404
      wis.collectStats.futureValue.values.sum shouldBe 0 // no work items should be created

    }

    "respond with 400 status if the request is malformed" in {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("POST", "").withBody(Json.obj("someJson" -> JsNumber(0xbad)))
      val ac = new AssignmentController(cc, wis, esp, appConfig)
      val result = ac.ensureAssignments(arn, "myUser")(request)
      status(result) shouldBe 400
    }
  }
}
