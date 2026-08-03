/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.AgencyDetails
import uk.gov.hmrc.agentuserclientdetails.model.AgentDetailsDesResponse
import uk.gov.hmrc.agentuserclientdetails.stubs.HttpClientStub
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class AgentServicesAccountConnectorISpec
extends BaseIntegrationSpec
with HttpClientStub
with MockFactory {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val agentServicesAccountConnector = new AgentServicesAccountConnector(appConfig, mockHttpClient)

  given HeaderCarrier = HeaderCarrier()

  "AgentServicesAccountConnector" should {
    "return Some(agent details)" in {
      val agencyDetails = Some(AgentDetailsDesResponse(Some(AgencyDetails(Some("Agency Name"), Some("agency@email.com")))))

      val mockResponse: HttpResponse = HttpResponse(OK, Json.toJson(agencyDetails).toString)

      mockHttpGet(url"${appConfig.asaBaseUrl}/agent-services-account/agent-record-with-checks")
      mockRequestBuilderExecute(mockResponse)

      agentServicesAccountConnector.getAgentDetails.futureValue.shouldBe(agencyDetails)
    }

    "return None" in {

      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")

      mockHttpGet(url"${appConfig.asaBaseUrl}/agent-services-account/agent-record-with-checks")
      mockRequestBuilderExecute(mockResponse)

      agentServicesAccountConnector.getAgentDetails.futureValue.shouldBe(None)
    }

  }

}
