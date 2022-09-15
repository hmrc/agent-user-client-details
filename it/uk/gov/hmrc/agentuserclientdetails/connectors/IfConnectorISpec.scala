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
import com.kenshoo.play.metrics.Metrics
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{PptRef, Urn, Utr}
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.PptSubscription
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class IfConnectorISpec extends BaseIntegrationSpec {

  lazy val appConfig = app.injector.instanceOf[AppConfig]
  lazy val cache = app.injector.instanceOf[AgentCacheProvider]
  lazy val metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders = app.injector.instanceOf[DesIfHeaders]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val mockAuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
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

  "IfConnector" should {
    "getTrustName (URN)" in {
      val testUrn = Urn("XXTRUST12345678")
      val httpClient = stub[HttpClient]
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/${testUrn.value}", mockResponse)(
        httpClient
      )
      val ifConnector = new IfConnectorImpl(appConfig, cache, httpClient, metrics, desIfHeaders)
      ifConnector.getTrustName(testUrn.value).futureValue shouldBe Some("Friendly Trust")
    }
    "getTrustName (UTR)" in {
      val testUtr = Utr("4937455253")
      val httpClient = stub[HttpClient]
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/UTR/${testUtr.value}", mockResponse)(
        httpClient
      )
      val ifConnector = new IfConnectorImpl(appConfig, cache, httpClient, metrics, desIfHeaders)
      ifConnector.getTrustName(testUtr.value).futureValue shouldBe Some("Friendly Trust")
    }
    "getPptSubscription (individual)" in {
      val testPptRef = PptRef("XAPPT0000012345")
      val httpClient = stub[HttpClient]
      val responseJson = Json.parse(s"""{
                                       |"legalEntityDetails": {
                                       |  "dateOfApplication": "2021-10-12",
                                       |  "customerDetails": {
                                       |    "customerType": "Individual",
                                       |      "individualDetails": {
                                       |        "firstName": "Bill",
                                       |        "lastName": "Sikes"
                                       |      }
                                       |    }
                                       |  }
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, responseJson.toString)
      mockHttpGet(
        s"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display",
        mockResponse
      )(httpClient)
      val ifConnector = new IfConnectorImpl(appConfig, cache, httpClient, metrics, desIfHeaders)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Bill Sikes"))
    }
    "getPptSubscription (organisation)" in {
      val testPptRef = PptRef("XAPPT0000012346")
      val httpClient = stub[HttpClient]
      val responseJson = Json.parse(s"""{
                                       |"legalEntityDetails": {
                                       |  "dateOfApplication": "2021-10-12",
                                       |  "customerDetails": {
                                       |    "customerType": "Organisation",
                                       |      "organisationDetails": {
                                       |        "organisationName": "Friendly Organisation"
                                       |      }
                                       |    }
                                       |  }
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, responseJson.toString)
      mockHttpGet(
        s"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display",
        mockResponse
      )(httpClient)
      val ifConnector = new IfConnectorImpl(appConfig, cache, httpClient, metrics, desIfHeaders)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Friendly Organisation"))
    }
  }
}
