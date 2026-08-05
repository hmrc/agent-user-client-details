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

import com.google.inject.Inject
import com.google.inject.Singleton
import uk.gov.hmrc.agentuserclientdetails.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentuserclientdetails.model.AgentDetailsDesResponse
import uk.gov.hmrc.agentuserclientdetails.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.unused
import scala.concurrent.Future

@Singleton
class AgentRecordService @Inject() (
  agentServicesAccountConnector: AgentServicesAccountConnector
):

  def getAgentDetails(@unused arn: Arn)(using hc: HeaderCarrier): Future[Option[AgentDetailsDesResponse]] = agentServicesAccountConnector.getAgentDetails
