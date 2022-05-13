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

import com.kenshoo.play.metrics.Metrics
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.{DesConnector, DesIfHeaders, EnrolmentStoreProxyConnectorImpl}
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.agentuserclientdetails.util.EnrolmentKey
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreProxyConnectorISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with GuiceOneServerPerSuite
  with MockFactory {

  lazy val appConfig = app.injector.instanceOf[AppConfig]
  lazy val cache = app.injector.instanceOf[AgentCacheProvider]
  lazy val metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders = app.injector.instanceOf[DesIfHeaders]
  lazy val desConnector = app.injector.instanceOf[DesConnector]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def mockHttpGet[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient.GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])(_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .when(url, *, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpPut[I, A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient.PUT[I, A](_: String, _: I, _: Seq[(String, String)])(_: Writes[I], _: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .when(url, *, *, *, *, *, *)
      .returns(Future.successful(response))

  val enrolment1 = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2 = Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))

  "EnrolmentStoreProxy" should {
    "complete ES3 call successfully" in {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      val httpClient = stub[HttpClient]
      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.obj("enrolments" -> Json.toJson(Seq(enrolment1, enrolment2))).toString)
      mockHttpGet(s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments?type=delegated", mockResponse)(httpClient)
      val esp = new EnrolmentStoreProxyConnectorImpl(httpClient, cache, metrics)(appConfig)
      esp.getEnrolmentsForGroupId(testGroupId).futureValue.toSet shouldBe Set(enrolment1, enrolment2)
    }
    "complete ES19 call successfully" in {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      val httpClient = stub[HttpClient]
      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.obj("enrolments" -> Json.toJson(Seq(enrolment1, enrolment2))).toString)
      val enrolmentKey = EnrolmentKey.enrolmentKeys(enrolment1).head
      mockHttpPut(s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments/$enrolmentKey/friendly_name", mockResponse)(httpClient)
      val esp = new EnrolmentStoreProxyConnectorImpl(httpClient, cache, metrics)(appConfig)
      esp.updateEnrolmentFriendlyName(testGroupId, enrolmentKey, "Friendly Name").futureValue shouldBe ()
    }
  }
}
