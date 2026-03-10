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

import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.AgentDetailsDesResponse
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentServicesAccountConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(implicit ec: ExecutionContext) {

  private lazy val baseUrl: String = appConfig.asaBaseUrl

  def getAgentDetails(implicit hc: HeaderCarrier): Future[Option[AgentDetailsDesResponse]] = http
    .get(url"$baseUrl/agent-services-account/agent-record-with-checks")
    .execute[HttpResponse]
    .map(response =>
      response.status match {
        case OK => Json.parse(response.body).asOpt[AgentDetailsDesResponse]
        case NO_CONTENT => None
        case other => throw UpstreamErrorResponse(s"agent details unavailable: agent assurance response code: $other", 500)
      }
    )

}
