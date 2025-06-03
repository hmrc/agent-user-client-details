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

package uk.gov.hmrc.agentuserclientdetails.connectors

import com.google.inject.AbstractModule
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{PptRef, Urn, Utr}
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.PptSubscription
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class IfConnectorISpec extends BaseIntegrationSpec with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders: DesIfHeaders = app.injector.instanceOf[DesIfHeaders]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
  }

  def mockHttpGet(url: URL): CallHandler2[URL, HeaderCarrier, RequestBuilder] =
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(url, *)
      .returning(mockRequestBuilder)

  def mockRequestBuilderExecute[A](value: A): CallHandler2[HttpReads[A], ExecutionContext, Future[A]] = {
    (mockRequestBuilder
      .setHeader(_*))
      .expects(*)
      .returning(mockRequestBuilder)

    (mockRequestBuilder
      .execute(using _: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful value)
  }

  "IfConnector" should {
    "getTrustName (URN)" in {
      val testUrn = Urn("XXTRUST12345678")
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/${testUrn.value}")
      mockRequestBuilderExecute(mockResponse)
      val ifConnector = new IfConnectorImpl(appConfig, mockHttpClient, metrics, desIfHeaders)
      ifConnector.getTrustName(testUrn.value).futureValue shouldBe Some("Friendly Trust")
    }
    "getTrustName (UTR)" in {
      val testUtr = Utr("4937455253")
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/UTR/${testUtr.value}")
      mockRequestBuilderExecute(mockResponse)
      val ifConnector = new IfConnectorImpl(appConfig, mockHttpClient, metrics, desIfHeaders)
      ifConnector.getTrustName(testUtr.value).futureValue shouldBe Some("Friendly Trust")
    }
    "getPptSubscription (individual)" in {
      val testPptRef = PptRef("XAPPT0000012345")
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
        url"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display"
      )
      mockRequestBuilderExecute(mockResponse)
      val ifConnector = new IfConnectorImpl(appConfig, mockHttpClient, metrics, desIfHeaders)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Bill Sikes"))
    }
    "getPptSubscription (organisation)" in {
      val testPptRef = PptRef("XAPPT0000012346")
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
        url"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display"
      )
      mockRequestBuilderExecute(mockResponse)
      val ifConnector = new IfConnectorImpl(appConfig, mockHttpClient, metrics, desIfHeaders)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Friendly Organisation"))
    }
  }
}
