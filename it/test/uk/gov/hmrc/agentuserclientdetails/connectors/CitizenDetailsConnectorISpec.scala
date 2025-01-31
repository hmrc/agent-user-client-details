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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsConnectorISpec extends BaseIntegrationSpec with MockFactory {

  lazy val appConfig = app.injector.instanceOf[AppConfig]
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

  "CitizenDetailsConnector" should {
    "getCitizenDetails" in {
      val testNino = Nino("HC275906A")
      val httpClient = stub[HttpClient]
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
      mockHttpGet(s"${appConfig.citizenDetailsBaseUrl}/citizen-details/nino/${testNino.value}", mockResponse)(
        httpClient
      )
      val cdConnector = new CitizenDetailsConnectorImpl(appConfig, httpClient, metrics)
      cdConnector.getCitizenDetails(testNino).futureValue shouldBe Some(Citizen(Some("John"), Some("Smith")))
    }
  }
}
