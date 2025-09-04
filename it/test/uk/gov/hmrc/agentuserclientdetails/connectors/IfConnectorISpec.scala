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
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.PptSubscription
import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.MtdItId
import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.PptRef
import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.Urn
import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.Utr
import uk.gov.hmrc.agentuserclientdetails.stubs.HttpClientStub
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class IfConnectorISpec
extends BaseIntegrationSpec
with HttpClientStub
with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders: DesIfHeaders = app.injector.instanceOf[DesIfHeaders]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val ifConnector =
    new IfConnectorImpl(
      appConfig,
      mockHttpClient,
      metrics,
      desIfHeaders
    )
  val testUrn: Urn = Urn("XXTRUST12345678")
  val testPptRef: PptRef = PptRef("XAPPT0000012345")
  val testMtdItId: MtdItId = MtdItId("12345678")

  override def moduleOverrides: AbstractModule =
    new AbstractModule {
      override def configure(): Unit = bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }

  "IfConnector" should {
    "getTrustName (URN)" in {
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/${testUrn.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTrustName(testUrn.value).futureValue shouldBe Some("Friendly Trust")
    }

    "getTrustName (UTR)" in {
      val testUtr = Utr("4937455253")
      val mockResponse: HttpResponse = HttpResponse(Status.OK, """{"trustDetails": {"trustName": "Friendly Trust"}}""")
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/UTR/${testUtr.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTrustName(testUtr.value).futureValue shouldBe Some("Friendly Trust")
    }

    "getPptSubscription (individual)" in {
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
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Bill Sikes"))
    }

    "getPptSubscription (organisation)" in {
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
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe Some(PptSubscription("Friendly Organisation"))
    }

    "getTradingDetailsForMtdItId" in {
      val responseJson = Json.parse(s"""{"taxPayerDisplayResponse":{
                                       |  "safeId": "XV0000100093327",
                                       |  "nino": "ZR987654C",
                                       |  "propertyIncome": false,
                                       |  "businessData": [
                                       |    {
                                       |      "incomeSourceId": "XWIS00000000219",
                                       |      "accountingPeriodStartDate": "2017-05-06",
                                       |      "accountingPeriodEndDate": "2018-05-05",
                                       |      "tradingName": "Surname DADTN",
                                       |      "businessAddressDetails": {
                                       |        "addressLine1": "100 Sutton Street",
                                       |        "addressLine2": "Wokingham",
                                       |        "addressLine3": "Surrey",
                                       |        "addressLine4": "London",
                                       |        "postalCode": "WC11AA",
                                       |        "countryCode": "GB"
                                       |      },
                                       |      "businessContactDetails": {
                                       |        "phoneNumber": "01111222333",
                                       |        "mobileNumber": "04444555666",
                                       |        "faxNumber": "07777888999",
                                       |        "emailAddress": "aaa@aaa.com"
                                       |      },
                                       |      "tradingStartDate": "2016-05-06",
                                       |      "cashOrAccruals": "cash",
                                       |      "seasonal": true
                                       |    }
                                       |  ]
                                       |  }
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.toJson(responseJson).toString)

      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/registration/business-details/mtdId/${testMtdItId.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)

      ifConnector.getTradingDetailsForMtdItId(testMtdItId).futureValue should matchPattern {
        case Some(TradingDetails(Nino("ZR987654C"), Some("Surname DADTN"))) =>
      }
    }
  }

  "return None" when {

    "a 404 is received for getTrustName" in {
      val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/${testUrn.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTrustName(testUrn.value).futureValue shouldBe None
    }

    "a 404 is received for getPptSubscription" in {
      val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
      mockHttpGet(
        url"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display"
      )
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getPptSubscription(testPptRef).futureValue shouldBe None
    }

    "a 404 is received for getTradingNameForMtdItId" in {
      val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/registration/business-details/mtdId/${testMtdItId.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTradingDetailsForMtdItId(testMtdItId).futureValue shouldBe None
    }
  }

  "throw an exception" when {

    "an unexpected status is received for getTrustName" in {
      val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, "oops")
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/${testUrn.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTrustName(testUrn.value).failed.futureValue shouldBe UpstreamErrorResponse(
        "unexpected status during retrieving TrustName, error=oops",
        500
      )
    }

    "an unexpected status is received for getPptSubscription" in {
      val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, "oops")
      mockHttpGet(
        url"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${testPptRef.value}/display"
      )
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getPptSubscription(testPptRef).failed.futureValue shouldBe UpstreamErrorResponse(
        "unexpected error from getPptSubscriptionDisplay: oops",
        500
      )
    }

    "an unexpected status is received for getTradingNameForMtdItId" in {
      val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR)
      mockHttpGet(url"${appConfig.ifPlatformBaseUrl}/registration/business-details/mtdId/${testMtdItId.value}")
      mockRequestBuilderExecuteWithHeader(mockResponse)
      ifConnector.getTradingDetailsForMtdItId(testMtdItId).failed.futureValue shouldBe UpstreamErrorResponse(
        "unexpected error during 'getTradingNameForMtdItId', statusCode=500",
        500
      )
    }
  }

}
