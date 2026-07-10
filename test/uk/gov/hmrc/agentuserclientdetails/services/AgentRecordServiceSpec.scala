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

package uk.gov.hmrc.agentuserclientdetails.services

import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentuserclientdetails.model.AgencyDetails
import uk.gov.hmrc.agentuserclientdetails.model.AgentDetailsDesResponse
import uk.gov.hmrc.agentuserclientdetails.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AgentRecordServiceSpec
extends BaseSpec {

  trait TestScope {

    val arn: Arn = Arn("KARN1234567")

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockAgentServicesAccountConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]

    val agentRecordService: AgentRecordService =
      new AgentRecordService(
        mockAgentServicesAccountConnector
      )

  }

  "AgentRecordService.getAgentRecord" should {
    "get the agent record from agent-services-account" in new TestScope {

      val agencyDetails = Some(AgentDetailsDesResponse(Some(AgencyDetails(Some("Agency Name"), Some("agency@email.com")))))

      (mockAgentServicesAccountConnector.getAgentDetails(_: HeaderCarrier)).expects(*).returning(Future.successful(agencyDetails))

      agentRecordService.getAgentDetails(arn).futureValue shouldBe agencyDetails
    }
  }

}
