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

import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.services.WorkItemServiceImpl
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FriendlyNameControllerISpec extends BaseIntegrationSpec with MongoSpecSupport {

  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val wir = FriendlyNameWorkItemRepository(config)
  lazy val wis = new WorkItemServiceImpl(wir)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val anotherTestGroupId = "8R6G-J5B5-0U1Q-N8R2"
  val testArn = Arn("BARN9706518")
  val unknownArn = Arn("SARN4216517")
  val badArn = Arn("XARN0000BAD")
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val enrolment1 = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2 =
    Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
  val enrolment3 = Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))
  val enrolment4 = Enrolment("HMRC-MTD-VAT", "Activated", "Ross Barker", Seq(Identifier("VRN", "101747642")))
  val enrolmentsWithFriendlyNames: Seq[Enrolment] = Seq(enrolment1, enrolment2, enrolment3, enrolment4)

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
  }

  "POST /arn/:arn/friendly-name" should {
    "respond with 200 status if all of the requests were processed successfully" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(testGroupId)))
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *)
        .returns(Future.successful(()))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolmentsWithFriendlyNames))
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request: Request[JsValue])
      status(result) shouldBe 200
      contentAsJson(result) \ "delayed" shouldBe JsDefined(JsArray(Seq.empty))
      contentAsJson(result) \ "permanentlyFailed" shouldBe JsDefined(JsArray(Seq.empty))
      wis.collectStats.futureValue.values.sum shouldBe 0
    }
    "respond with 200 status if some of the requests permanently failed and there is no further work outstanding" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(testGroupId)))
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, EnrolmentKey.enrolmentKeys(enrolment4).head, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 404))) // a 404 from enrolment store is a non-retryable failure
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *)
        .returns(Future.successful(()))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolmentsWithFriendlyNames))
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request: Request[JsValue])
      status(result) shouldBe 200
      contentAsJson(result) \ "delayed" shouldBe JsDefined(JsArray(Seq.empty))
      contentAsJson(result) \ "permanentlyFailed" shouldBe JsDefined(
        JsArray(Seq(Json.toJson(Client.fromEnrolment(enrolment4))))
      )
      wis.collectStats.futureValue.values.sum shouldBe 0
    }
    "respond with 202 status if some of the requests temporarily failed and work items for the failed item should be created" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(testGroupId)))
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, EnrolmentKey.enrolmentKeys(enrolment2).head, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 429))) // a 429 from enrolment store is a retryable failure
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *)
        .returns(Future.successful(()))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolmentsWithFriendlyNames))
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request: Request[JsValue])
      status(result) shouldBe 202
      contentAsJson(result) \ "delayed" shouldBe JsDefined(JsArray(Seq(Json.toJson(Client.fromEnrolment(enrolment2)))))
      contentAsJson(result) \ "permanentlyFailed" shouldBe JsDefined(JsArray(Seq.empty))
      wis.collectStats.futureValue.values.sum shouldBe 1
    }
    "respond with 202 status if the request has too many enrolments to process and add work items to the repository" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(testGroupId)))
      (esp
        .updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *)
        .returns(Future.successful(()))
      val request =
        FakeRequest("POST", "").withBody(Json.toJson(Seq.fill(100)(enrolment1))) // 100 enrolments to process
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request: Request[JsValue])
      status(result) shouldBe 202
      contentAsJson(result) \ "delayed" shouldBe JsDefined(
        JsArray(Seq.fill(100)(Json.toJson(Client.fromEnrolment(enrolment1))))
      )
      contentAsJson(result) \ "permanentlyFailed" shouldBe JsDefined(JsArray(Seq.empty))
      wis.collectStats.futureValue.values.sum shouldBe 100
    }
    "respond with 400 status if the request is malformed" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(testGroupId)))
      val request = FakeRequest("POST", "").withBody(Json.obj("someJson" -> JsNumber(0xbad)))
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request)
      status(result) shouldBe 400
    }
    "respond with 404 status if the groupId provided is unknown" in {
      val esp = stub[EnrolmentStoreProxyConnector]
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(None))
      val request = FakeRequest("POST", "").withBody(Json.toJson(enrolmentsWithFriendlyNames))
      val fnc = new FriendlyNameController(cc, wis, esp, appConfig)
      val result = fnc.updateFriendlyName(testArn)(request)
      status(result) shouldBe 404
    }
  }
}
