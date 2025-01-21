/*
 * Copyright 2025 HM Revenue & Customs
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

package test.uk.gov.hmrc.agentuserclientdetails.connectors

import com.google.inject.AbstractModule
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgentAssuranceConnectorISpec extends BaseIntegrationSpec with MockFactory {

  lazy val appConfig = app.injector.instanceOf[AppConfig]
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

  "AgentAssuranceConnector" should {
    "getAgentDetails" in {
      val testArn = Arn("KARN0762398")

      val httpClient = stub[HttpClient]

      val agencyDetails = AgencyDetails(Some("Agency Name"), Some("agency@email.com"))

      val mockResponse: HttpResponse = HttpResponse(Status.OK, Json.toJson(agencyDetails).toString)

      mockHttpGet(
        s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent/agency-details/${testArn.value}",
        mockResponse)(httpClient)

      val agentAssuranceConnector = new AgentAssuranceConnector(appConfig, httpClient)

      agentAssuranceConnector.getAgentDetails(testArn).futureValue shouldBe agencyDetails
    }

    //      intercept[UpstreamErrorResponse](agentAssuranceConnector.getAgentDetails(testArn).futureValue)
  }
}
