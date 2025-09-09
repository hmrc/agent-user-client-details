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

import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.MtdItId
import uk.gov.hmrc.agentuserclientdetails.stubs.HttpClientStub
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class HipConnectorISpec
extends BaseIntegrationSpec
with HttpClientStub
with MockFactory {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  case class TestCaseHappyPath(
    responseStatus: Int,
    responseBody: String,
    expectedReturn: Option[TradingDetails],
    testCaseName: String
  )

  val hipConnector: HipConnector =
    new HipConnectorImpl(
      appConfig,
      mockHttpClient,
      metrics,
      fixedClock
    )

  "HipConnector.getTradingDetailsForMtdItId calls API endpoint and return TradingDetails" when {
    List(
      TestCaseHappyPath(
        responseStatus = Status.OK,
        responseBody = responseBodySuccess,
        expectedReturn = Some(TradingDetails(nino = Nino("AS243900B"), tradingName = Some("Sudipta Consulting"))),
        testCaseName = "happy path - TradingDetails with trading name"
      ),
      TestCaseHappyPath(
        responseStatus = Status.OK,
        responseBody = responseBodySuccessBusinessDataMissing,
        expectedReturn = Some(TradingDetails(nino = Nino("AS243900B"), tradingName = None)),
        testCaseName = "happy path - empty trading name as there is no business data field in response body"
      ),
      TestCaseHappyPath(
        responseStatus = Status.OK,
        responseBody = responseBodySuccessTradingNameMissing,
        expectedReturn = Some(TradingDetails(nino = Nino("AS243900B"), tradingName = None)),
        testCaseName = "happy path - empty trading name as there is no 'trading name' field in response body"
      ),
      TestCaseHappyPath(
        responseStatus = Status.UNPROCESSABLE_ENTITY,
        responseBody = responseBodyErrors008,
        expectedReturn = None,
        testCaseName = "happy path - empty response as per 008 error code"
      ),
      TestCaseHappyPath(
        responseStatus = Status.UNPROCESSABLE_ENTITY,
        responseBody = responseBodyErrors006,
        expectedReturn = None,
        testCaseName = "happy path - empty response as per 006 error code"
      )
    ).foreach { tc =>
      tc.testCaseName in {
        mockHttpGet(url"$url")
        val mockResponse = HttpResponse(tc.responseStatus, tc.responseBody)
        mockRequestBuilderExecuteWithHeader(mockResponse)
        hipConnector.getTradingDetailsForMtdItId(mtdItId).futureValue shouldBe tc.expectedReturn
      }
    }
  }

  "HipConnector.getTradingDetailsForMtdItId fails for any other response codes" in {

    mockHttpGet(url"$url")
    val mockResponse = HttpResponse(Status.NOT_FOUND, "url not found")
    mockRequestBuilderExecuteWithHeader(mockResponse)

    val result =
      hipConnector
        .getTradingDetailsForMtdItId(mtdItId)
        .failed
        .futureValue
        .getMessage
    val correlationId = result.split("correlationId: ")(1).split("]")(0)

    result shouldBe s"Unexpected response from HIP API: [correlationId: $correlationId] [status: 404] [responseBody: url not found]"
  }

  lazy val responseBodySuccess: String =
    // language=JSON
    """
          {
            "success": {
              "processingDate": "2023-05-14T22:37:48Z",
              "taxPayerDisplayResponse": {
                "safeId": "ZX1135522140666",
                "nino": "AS243900B",
                "mtdId": "XNIT00000068707",
                "yearOfMigration": "2023",
                "propertyIncomeFlag": true,
                "businessData": [
                  {
                    "incomeSourceId": "XWIS00000212241",
                    "incomeSource": "ITSB",
                    "accPeriodSDate": "2020-04-06",
                    "accPeriodEDate": "2021-04-05",
                    "tradingName": "Sudipta Consulting",
                    "businessAddressDetails": {
                      "addressLine1": "104",
                      "addressLine2": "Highland Park",
                      "addressLine3": "olkata City",
                      "addressLine4": "London",
                      "postalCode": "CR4 5TY",
                      "countryCode": "GB"
                    },
                    "businessContactDetails": {
                      "telephone": "7865976530",
                      "mobileNo": "9875438922",
                      "faxNo": "675432",
                      "email": "jdoe@example.com"
                    },
                    "tradingSDate": "2020-06-01",
                    "contextualTaxYear": "2024",
                    "cashOrAccrualsFlag": true,
                    "seasonalFlag": true,
                    "cessationDate": "2024-09-07",
                    "paperLessFlag": true,
                    "incomeSourceStartDate": "1900-03-14",
                    "firstAccountingPeriodStartDate": "2020-04-06",
                    "firstAccountingPeriodEndDate": "2021-04-05",
                    "latencyDetails": {
                      "latencyEndDate": "2024-04-05",
                      "taxYear1": "2023",
                      "latencyIndicator1": "A",
                      "taxYear2": "2024",
                      "latencyIndicator2": "Q"
                    },
                    "quarterTypeElection": {
                      "quarterReportingType": "STANDARD",
                      "taxYearofElection": "2024"
                    }
                  }
                ],
                "propertyData": [
                  {
                    "incomeSourceType": "03",
                    "incomeSourceId": "XBIS00000212243",
                    "accPeriodSDate": "2020-04-06",
                    "accPeriodEDate": "2020-04-05",
                    "tradingSDate": "2020-07-01",
                    "contextualTaxYear": "2024",
                    "cashOrAccrualsFlag": true,
                    "numPropRented": "0",
                    "numPropRentedUK": "0",
                    "numPropRentedEEA": "0",
                    "numPropRentedNONEEA": "0",
                    "email": "jdoe@example.com",
                    "cessationDate": "1900-03-14",
                    "paperLessFlag": true,
                    "incomeSourceStartDate": "1900-03-14",
                    "firstAccountingPeriodStartDate": "2020-04-06",
                    "firstAccountingPeriodEndDate": "2021-04-05",
                    "latencyDetails": {
                      "latencyEndDate": "2024-04-05",
                      "taxYear1": "2023",
                      "latencyIndicator1": "A",
                      "taxYear2": "2024",
                      "latencyIndicator2": "Q"
                    },
                    "quarterTypeElection": {
                      "quarterReportingType": "STANDARD",
                      "taxYearofElection": "0000"
                    }
                  }
                ]
              }
            }
          }
          """

  lazy val responseBodySuccessTradingNameMissing: String =
    // language=JSON
    """
          {
            "success": {
              "processingDate": "2023-05-14T22:37:48Z",
              "taxPayerDisplayResponse": {
                "safeId": "ZX1135522140666",
                "nino": "AS243900B",
                "mtdId": "XNIT00000068707",
                "yearOfMigration": "2023",
                "propertyIncomeFlag": true,
                "businessData": [
                  {
                    "incomeSourceId": "XWIS00000212241",
                    "incomeSource": "ITSB",
                    "accPeriodSDate": "2020-04-06",
                    "accPeriodEDate": "2021-04-05",

                    "businessAddressDetails": {
                      "addressLine1": "104",
                      "addressLine2": "Highland Park",
                      "addressLine3": "olkata City",
                      "addressLine4": "London",
                      "postalCode": "CR4 5TY",
                      "countryCode": "GB"
                    },
                    "businessContactDetails": {
                      "telephone": "7865976530",
                      "mobileNo": "9875438922",
                      "faxNo": "675432",
                      "email": "jdoe@example.com"
                    },
                    "tradingSDate": "2020-06-01",
                    "contextualTaxYear": "2024",
                    "cashOrAccrualsFlag": true,
                    "seasonalFlag": true,
                    "cessationDate": "2024-09-07",
                    "paperLessFlag": true,
                    "incomeSourceStartDate": "1900-03-14",
                    "firstAccountingPeriodStartDate": "2020-04-06",
                    "firstAccountingPeriodEndDate": "2021-04-05",
                    "latencyDetails": {
                      "latencyEndDate": "2024-04-05",
                      "taxYear1": "2023",
                      "latencyIndicator1": "A",
                      "taxYear2": "2024",
                      "latencyIndicator2": "Q"
                    },
                    "quarterTypeElection": {
                      "quarterReportingType": "STANDARD",
                      "taxYearofElection": "2024"
                    }
                  }
                ],
                "propertyData": [
                  {
                    "incomeSourceType": "03",
                    "incomeSourceId": "XBIS00000212243",
                    "accPeriodSDate": "2020-04-06",
                    "accPeriodEDate": "2020-04-05",
                    "tradingSDate": "2020-07-01",
                    "contextualTaxYear": "2024",
                    "cashOrAccrualsFlag": true,
                    "numPropRented": "0",
                    "numPropRentedUK": "0",
                    "numPropRentedEEA": "0",
                    "numPropRentedNONEEA": "0",
                    "email": "jdoe@example.com",
                    "cessationDate": "1900-03-14",
                    "paperLessFlag": true,
                    "incomeSourceStartDate": "1900-03-14",
                    "firstAccountingPeriodStartDate": "2020-04-06",
                    "firstAccountingPeriodEndDate": "2021-04-05",
                    "latencyDetails": {
                      "latencyEndDate": "2024-04-05",
                      "taxYear1": "2023",
                      "latencyIndicator1": "A",
                      "taxYear2": "2024",
                      "latencyIndicator2": "Q"
                    },
                    "quarterTypeElection": {
                      "quarterReportingType": "STANDARD",
                      "taxYearofElection": "0000"
                    }
                  }
                ]
              }
            }
          }
          """

  lazy val responseBodySuccessBusinessDataMissing: String =
    // language=JSON
    """
          {
            "success": {
              "processingDate": "2023-05-14T22:37:48Z",
              "taxPayerDisplayResponse": {
                "safeId": "ZX1135522140666",
                "nino": "AS243900B",
                "mtdId": "XNIT00000068707",
                "yearOfMigration": "2023",
                "propertyIncomeFlag": true,
                "propertyData": [
                  {
                    "incomeSourceType": "03",
                    "incomeSourceId": "XBIS00000212243",
                    "accPeriodSDate": "2020-04-06",
                    "accPeriodEDate": "2020-04-05",
                    "tradingSDate": "2020-07-01",
                    "contextualTaxYear": "2024",
                    "cashOrAccrualsFlag": true,
                    "numPropRented": "0",
                    "numPropRentedUK": "0",
                    "numPropRentedEEA": "0",
                    "numPropRentedNONEEA": "0",
                    "email": "jdoe@example.com",
                    "cessationDate": "1900-03-14",
                    "paperLessFlag": true,
                    "incomeSourceStartDate": "1900-03-14",
                    "firstAccountingPeriodStartDate": "2020-04-06",
                    "firstAccountingPeriodEndDate": "2021-04-05",
                    "latencyDetails": {
                      "latencyEndDate": "2024-04-05",
                      "taxYear1": "2023",
                      "latencyIndicator1": "A",
                      "taxYear2": "2024",
                      "latencyIndicator2": "Q"
                    },
                    "quarterTypeElection": {
                      "quarterReportingType": "STANDARD",
                      "taxYearofElection": "0000"
                    }
                  }
                ]
              }
            }
          }
          """

  lazy val responseBodyErrors008: String =
    // language=JSON
    """{
      "errors": {
        "processingDate": "2024-07-15T09:45:17Z",
        "code": "008",
        "text": "ID not Found"
      }
    }"""

  lazy val responseBodyErrors006: String =
    // language=JSON
    """{
      "errors": {
        "processingDate": "2024-07-15T09:45:17Z",
        "code": "006",
        "text": "Subscription data not Found"
      }
    }"""

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val correlationId: String = "5c290341-2d37-4e3c-a348-06724b2cf1c0"
  lazy val mtdItId: MtdItId = MtdItId("XNIT00000068707")
  lazy val url = "http://localhost:9009/etmp/RESTAdapter/itsa/taxpayer/business-details?mtdReference=XNIT00000068707"

  lazy val fixedClock: Clock = Clock.fixed(
    LocalDateTime.parse("2059-11-25T16:33:51.880", DateTimeFormatter.ISO_DATE_TIME).toInstant(ZoneOffset.UTC),
    ZoneId.of("UTC")
  )

}
