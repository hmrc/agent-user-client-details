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
import uk.gov.hmrc.agentmtdidentifiers.model.CgtRef
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DesConnectorISpec
extends BaseIntegrationSpec
with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders: DesIfHeaders = app.injector.instanceOf[DesIfHeaders]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  val desConnector =
    new DesConnectorImpl(
      appConfig,
      mockHttpClient,
      metrics,
      desIfHeaders
    )

  val testCgtRef: CgtRef = CgtRef("XMCGTP123456789")
  val testVrn: Vrn = Vrn("12345678")

  def mockHttpGet(url: URL): CallHandler2[
    URL,
    HeaderCarrier,
    RequestBuilder
  ] =
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(url, *)
      .returning(mockRequestBuilder)

  def mockRequestBuilderExecute[A](value: A): CallHandler2[
    HttpReads[A],
    ExecutionContext,
    Future[A]
  ] = {
    (mockRequestBuilder
      .setHeader(_ *))
      .expects(*)
      .returning(mockRequestBuilder)

    (mockRequestBuilder
      .execute(using _: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful value)
  }

  override def moduleOverrides: AbstractModule =
    new AbstractModule {
      override def configure(): Unit = bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }

  "DesConnector" should {
    "getCgtSubscription" in {
      val cgtSubscription = CgtSubscription(SubscriptionDetails(TypeOfPersonDetails("Individual", Left(IndividualName("Tom", "Jones")))))

      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.toJson(cgtSubscription).toString)

      mockHttpGet(url"${appConfig.desBaseUrl}/subscriptions/CGT/ZCGT/${testCgtRef.value}")
      mockRequestBuilderExecute(mockResponse)

      val desConnector =
        new DesConnectorImpl(
          appConfig,
          mockHttpClient,
          metrics,
          desIfHeaders
        )

      desConnector.getCgtSubscription(CgtRef("XMCGTP123456789")).futureValue should matchPattern {
        case Some(sub) if sub == cgtSubscription =>
      }
    }
    "getCgtSubscription (trust)" in {
      val testCgtRef = CgtRef("XMCGTP123456789")
      val cgtSubscription = CgtSubscription(SubscriptionDetails(TypeOfPersonDetails("Trustee", Right(OrganisationName("Friendly Trust")))))
      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.toJson(cgtSubscription).toString)

      mockHttpGet(url"${appConfig.desBaseUrl}/subscriptions/CGT/ZCGT/${testCgtRef.value}")
      mockRequestBuilderExecute(mockResponse)

      desConnector.getCgtSubscription(CgtRef("XMCGTP123456789")).futureValue should matchPattern {
        case Some(sub) if sub == cgtSubscription =>
      }
    }

    "getVatCustomerDetails (organisation)" in {
      val responseJson = Json.parse(s"""{
                                       |   "approvedInformation" : {
                                       |      "customerDetails" : {
                                       |         "organisationName" : "Friendly Organisation",
                                       |         "mandationStatus" : "2",
                                       |         "businessStartDate" : "2017-04-02",
                                       |         "registrationReason" : "0013",
                                       |         "effectiveRegistrationDate": "2017-04-02",
                                       |         "isInsolvent" : false
                                       |      },
                                       |      "bankDetails" : {
                                       |         "sortCode" : "16****",
                                       |         "accountHolderName" : "***********************",
                                       |         "bankAccountNumber" : "****9584"
                                       |      },
                                       |      "deregistration" : {
                                       |         "effectDateOfCancellation" : "2018-03-01",
                                       |         "lastReturnDueDate" : "2018-02-24",
                                       |         "deregistrationReason" : "0001"
                                       |      },
                                       |      "PPOB" : {
                                       |         "address" : {
                                       |            "line4" : "VAT PPOB Line5",
                                       |            "postCode" : "HA0 3ET",
                                       |            "countryCode" : "GB",
                                       |            "line2" : "VAT PPOB Line2",
                                       |            "line3" : "VAT PPOB Line3",
                                       |            "line1" : "VAT PPOB Line1"
                                       |         },
                                       |         "contactDetails" : {
                                       |            "mobileNumber" : "012345678902",
                                       |            "emailAddress" : "testsignuptcooo37@hmrc.co.uk",
                                       |            "faxNumber" : "012345678903",
                                       |            "primaryPhoneNumber" : "012345678901"
                                       |         },
                                       |         "websiteAddress" : "www.tumbleweed.com"
                                       |      },
                                       |      "flatRateScheme" : {
                                       |         "FRSCategory" : "003",
                                       |         "limitedCostTrader" : true,
                                       |         "FRSPercentage" : 59.99
                                       |      },
                                       |      "returnPeriod" : {
                                       |         "stdReturnPeriod" : "MA"
                                       |      },
                                       |      "businessActivities" : {
                                       |         "primaryMainCode" : "10410",
                                       |         "mainCode3" : "10710",
                                       |         "mainCode2" : "10611",
                                       |         "mainCode4" : "10720"
                                       |      }
                                       |   }
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, responseJson.toString)

      mockHttpGet(url"${appConfig.desBaseUrl}/vat/customer/vrn/${testVrn.value}/information")
      mockRequestBuilderExecute(mockResponse)

      desConnector.getVatCustomerDetails(testVrn).futureValue shouldBe Some(
        VatCustomerDetails(
          Some("Friendly Organisation"),
          None,
          None
        )
      )
    }

    "getVatCustomerDetails (individual)" in {
      val testVrn = Vrn("12345679")
      val responseJson = Json.parse(s"""{
                                       |   "approvedInformation" : {
                                       |      "customerDetails" : {
                                       |         "individual" : {
                                       |           "firstName" : "Tom",
                                       |           "lastName" : "Jones"
                                       |         },
                                       |         "mandationStatus" : "2",
                                       |         "businessStartDate" : "2017-04-02",
                                       |         "registrationReason" : "0013",
                                       |         "effectiveRegistrationDate": "2017-04-02",
                                       |         "isInsolvent" : false
                                       |      },
                                       |      "bankDetails" : {
                                       |         "sortCode" : "16****",
                                       |         "accountHolderName" : "***********************",
                                       |         "bankAccountNumber" : "****9584"
                                       |      },
                                       |      "deregistration" : {
                                       |         "effectDateOfCancellation" : "2018-03-01",
                                       |         "lastReturnDueDate" : "2018-02-24",
                                       |         "deregistrationReason" : "0001"
                                       |      },
                                       |      "PPOB" : {
                                       |         "address" : {
                                       |            "line4" : "VAT PPOB Line5",
                                       |            "postCode" : "HA0 3ET",
                                       |            "countryCode" : "GB",
                                       |            "line2" : "VAT PPOB Line2",
                                       |            "line3" : "VAT PPOB Line3",
                                       |            "line1" : "VAT PPOB Line1"
                                       |         },
                                       |         "contactDetails" : {
                                       |            "mobileNumber" : "012345678902",
                                       |            "emailAddress" : "testsignuptcooo37@hmrc.co.uk",
                                       |            "faxNumber" : "012345678903",
                                       |            "primaryPhoneNumber" : "012345678901"
                                       |         },
                                       |         "websiteAddress" : "www.tumbleweed.com"
                                       |      },
                                       |      "flatRateScheme" : {
                                       |         "FRSCategory" : "003",
                                       |         "limitedCostTrader" : true,
                                       |         "FRSPercentage" : 59.99
                                       |      },
                                       |      "returnPeriod" : {
                                       |         "stdReturnPeriod" : "MA"
                                       |      },
                                       |      "businessActivities" : {
                                       |         "primaryMainCode" : "10410",
                                       |         "mainCode3" : "10710",
                                       |         "mainCode2" : "10611",
                                       |         "mainCode4" : "10720"
                                       |      }
                                       |   }
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, responseJson.toString)

      mockHttpGet(url"${appConfig.desBaseUrl}/vat/customer/vrn/${testVrn.value}/information")
      mockRequestBuilderExecute(mockResponse)

      desConnector.getVatCustomerDetails(testVrn).futureValue shouldBe Some(
        VatCustomerDetails(
          None,
          Some(VatIndividual(
            None,
            Some("Tom"),
            None,
            Some("Jones")
          )),
          None
        )
      )
    }

    "return None" when {

      "a 404 is received for getCgtSubscription" in {
        val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
        mockHttpGet(url"${appConfig.desBaseUrl}/subscriptions/CGT/ZCGT/${testCgtRef.value}")
        mockRequestBuilderExecute(mockResponse)
        desConnector.getCgtSubscription(testCgtRef).futureValue shouldBe None
      }

      "a 404 is received for getVatCustomerDetails" in {
        val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
        mockHttpGet(url"${appConfig.desBaseUrl}/vat/customer/vrn/${testVrn.value}/information")
        mockRequestBuilderExecute(mockResponse)
        desConnector.getVatCustomerDetails(testVrn).futureValue shouldBe None
      }
    }

    "throw an exception" when {

      "an unexpected status is returned for getCgtSubscription" in {
        val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, "oops")
        mockHttpGet(url"${appConfig.desBaseUrl}/subscriptions/CGT/ZCGT/${testCgtRef.value}")
        mockRequestBuilderExecute(mockResponse)
        desConnector.getCgtSubscription(testCgtRef).failed.futureValue shouldBe UpstreamErrorResponse(
          "unexpected error during 'getCgtSubscription': oops",
          500
        )
      }

      "an unexpected status is returned for getVatCustomerDetails" in {
        val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR)
        mockHttpGet(url"${appConfig.desBaseUrl}/vat/customer/vrn/${testVrn.value}/information")
        mockRequestBuilderExecute(mockResponse)
        desConnector.getVatCustomerDetails(testVrn).failed.futureValue shouldBe UpstreamErrorResponse(
          "unexpected error during 'getVatCustomerDetails', statusCode=500",
          500
        )
      }
    }
  }

}
