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
import uk.gov.hmrc.agentuserclientdetails.model.Citizen
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

class CitizenDetailsConnectorISpec
extends BaseIntegrationSpec
with HttpClientStub
with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val desIfHeaders: DesIfHeaders = app.injector.instanceOf[DesIfHeaders]
  val cdConnector =
    new CitizenDetailsConnectorImpl(
      appConfig,
      mockHttpClient,
      metrics
    )
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testNino: Nino = Nino("HC275906A")

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule =
    new AbstractModule {
      override def configure(): Unit = bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }

  "getCitizenDetails" should {
    "return a Citizen model when status is 200" in {
      val responseJson = Json.parse(s"""{
                                       |   "name": {
                                       |      "current": {
                                       |         "firstName": "John",
                                       |         "lastName": "Smith"
                                       |      },
                                       |      "previous": []
                                       |   },
                                       |   "ids": {
                                       |      "nino": "HC275906A"
                                       |   },
                                       |   "dateOfBirth": "2000-01-01"
                                       |}""".stripMargin)
      val mockResponse: HttpResponse = HttpResponse(Status.OK, responseJson.toString)
      mockHttpGet(url"${appConfig.citizenDetailsBaseUrl}/citizen-details/nino/${testNino.value}")
      mockRequestBuilderExecute(mockResponse)
      cdConnector.getCitizenDetails(testNino).futureValue shouldBe Some(Citizen(Some("John"), Some("Smith")))
    }

    "return None when status is 404" in {
      val mockResponse: HttpResponse = HttpResponse(Status.NOT_FOUND)
      mockHttpGet(url"${appConfig.citizenDetailsBaseUrl}/citizen-details/nino/${testNino.value}")
      mockRequestBuilderExecute(mockResponse)
      cdConnector.getCitizenDetails(testNino).futureValue shouldBe None
    }

    "throw an exception when status is unrecognised" in {
      val mockResponse: HttpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR)
      mockHttpGet(url"${appConfig.citizenDetailsBaseUrl}/citizen-details/nino/${testNino.value}")
      mockRequestBuilderExecute(mockResponse)
      cdConnector.getCitizenDetails(testNino).failed.futureValue shouldBe UpstreamErrorResponse(
        "unexpected error during 'getCitizenDetails', statusCode=500",
        500
      )
    }
  }

}
