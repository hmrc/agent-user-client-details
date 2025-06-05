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

package uk.gov.hmrc.agentuserclientdetails.connectors

import com.codahale.metrics.NoopMetricRegistry
import com.google.inject.AbstractModule
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AgentAssuranceConnectorISpec
extends BaseIntegrationSpec
with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
  val noopMetricRegistry = new NoopMetricRegistry

  val agentAssuranceConnector = new AgentAssuranceConnector(appConfig, mockHttpClientV2)
  val testArn: Arn = Arn("KARN0762398")

  override def moduleOverrides: AbstractModule =
    new AbstractModule {
      override def configure(): Unit = bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }

  def mockHttpGet(url: URL): CallHandler2[
    URL,
    HeaderCarrier,
    RequestBuilder
  ] =
    (mockHttpClientV2
      .get(_: URL)(_: HeaderCarrier))
      .expects(url, *)
      .returning(mockRequestBuilder)

  def mockRequestBuilderExecute[A](value: A): CallHandler2[
    HttpReads[A],
    ExecutionContext,
    Future[A]
  ] =
    (mockRequestBuilder
      .execute(using _: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful value)

  "AgentAssuranceConnector" when {
    "getAgentDetails" should {
      "returns agency details response" in {

        val agencyDetails = Some(AgentDetailsDesResponse(Some(AgencyDetails(Some("Agency Name"), Some("agency@email.com")))))

        val mockResponse: HttpResponse = HttpResponse(OK, Json.toJson(agencyDetails).toString)

        mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent/agency-details/arn/${testArn.value}")
        mockRequestBuilderExecute(mockResponse)

        agentAssuranceConnector.getAgentDetails(testArn).futureValue shouldBe agencyDetails
      }
      "returns None when no agency details response" in {

        val agencyDetails = None

        val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, Json.toJson(agencyDetails).toString)

        mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent/agency-details/arn/${testArn.value}")
        mockRequestBuilderExecute(mockResponse)

        agentAssuranceConnector.getAgentDetails(testArn).futureValue shouldBe None

      }

      "returns an error when the agent assurance service returns an error" in {

        val mockResponse: HttpResponse = HttpResponse(SERVICE_UNAVAILABLE, "")

        mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent/agency-details/arn/${testArn.value}")
        mockRequestBuilderExecute(mockResponse)

        agentAssuranceConnector.getAgentDetails(testArn).failed.futureValue shouldBe an[UpstreamErrorResponse]
      }
    }
  }

}
