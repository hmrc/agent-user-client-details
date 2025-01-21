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

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentAssuranceConnector @Inject() (appConfig: AppConfig, http: HttpClient)(implicit ec: ExecutionContext) {

  private val baseUrl = appConfig.agentAssuranceBaseUrl
  import uk.gov.hmrc.http.HttpReads.Implicits._

  def getAgentDetails(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AgentDetailsDesResponse] =
    httpV2
      .get(new URL(s"$baseUrl/agent-assurance/agent/agency-details/$arn"))
      .execute[HttpResponse]
      .map(response =>
        response.status match {
          case OK    => Json.parse(response.body).as[AgentDetailsDesResponse]
          case other => throw UpstreamErrorResponse(s"agent details unavailable: des response code: $other", 500)
        }
      )
}
