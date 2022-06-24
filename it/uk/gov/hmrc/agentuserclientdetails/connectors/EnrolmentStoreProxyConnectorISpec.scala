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

package uk.gov.hmrc.agentuserclientdetails.connectors

import com.google.inject.AbstractModule
import play.api.http.Status.{CREATED, NO_CONTENT, OK}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Enrolment, EnrolmentKey, Identifier}
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreProxyConnectorISpec extends BaseIntegrationSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val httpClient: HttpClient = stub[HttpClient]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[HttpClient]).toInstance(httpClient)
  }

  val enrolment1: Enrolment = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2: Enrolment =
    Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))

  trait TestScope {
    lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    lazy val esp: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]
  }

  "EnrolmentStoreProxy" should {
    "complete ES1 call successfully" in new TestScope {
      val arn = "TARN0000001"
      val groupId = "2K6H-N1C1-7M7V-O4A3"
      val principalGroupIds = s"""{"principalGroupIds": ["$groupId"]}"""
      val mockResponse: HttpResponse = HttpResponse(OK, principalGroupIds)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal",
        mockResponse
      )(httpClient)
      esp.getPrincipalGroupIdFor(Arn(arn)).futureValue shouldBe Some(groupId)
    }
    s"handle $NO_CONTENT in ES1 call" in new TestScope {
      val arn = "TARN0000001"
      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal",
        mockResponse
      )(httpClient)
      esp.getPrincipalGroupIdFor(Arn(arn)).futureValue shouldBe None
    }
    "complete ES3 call successfully" in new TestScope {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      val mockResponse: HttpResponse =
        HttpResponse(OK, Json.obj("enrolments" -> Json.toJson(Seq(enrolment1, enrolment2))).toString)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments?type=delegated",
        mockResponse
      )(httpClient)
      esp.getEnrolmentsForGroupId(testGroupId).futureValue.toSet shouldBe Set(enrolment1, enrolment2)
    }
    "complete ES19 call successfully" in new TestScope {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      val mockResponse: HttpResponse =
        HttpResponse(OK, Json.obj("enrolments" -> Json.toJson(Seq(enrolment1, enrolment2))).toString)
      val enrolmentKey = EnrolmentKey.enrolmentKeys(enrolment1).head
      mockHttpPut(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments/$enrolmentKey/friendly_name",
        mockResponse
      )(httpClient)
      esp.updateEnrolmentFriendlyName(testGroupId, enrolmentKey, "Friendly Name").futureValue shouldBe (())
    }
    "complete ES11 call successfully" in new TestScope {
      val testUserId = "ABCEDEFGI1234568"
      val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"
      val mockResponse: HttpResponse = HttpResponse(CREATED, "")
      mockHttpPostEmpty(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/users/$testUserId/enrolments/$testEnrolmentKey",
        mockResponse
      )(httpClient)
      esp.assignEnrolment(testUserId, testEnrolmentKey).futureValue shouldBe (())
    }
    "complete ES12 call successfully" in new TestScope {
      val testUserId = "ABCEDEFGI1234568"
      val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"
      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")
      mockHttpDelete(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/users/$testUserId/enrolments/$testEnrolmentKey",
        mockResponse
      )(httpClient)
      esp.unassignEnrolment(testUserId, testEnrolmentKey).futureValue shouldBe (())
    }
  }

  def mockHttpGet[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])(
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpPostEmpty[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .POSTEmpty[A](_: String, _: Seq[(String, String)])(_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .when(url, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpPut[I, A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .PUT[I, A](_: String, _: I, _: Seq[(String, String)])(
        _: Writes[I],
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpDelete[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .DELETE[A](_: String, _: Seq[(String, String)])(
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *)
      .returns(Future.successful(response))
}
