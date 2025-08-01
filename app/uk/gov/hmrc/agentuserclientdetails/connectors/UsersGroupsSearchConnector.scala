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

import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.agents.accessgroups.UserDetails
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.util.HttpAPIMonitor
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class UsersGroupsSearchConnector @Inject() (
  httpClient: HttpClientV2,
  val metrics: Metrics
)(implicit
  appConfig: AppConfig,
  val ec: ExecutionContext
)
extends HttpAPIMonitor
with HttpErrorFunctions
with Logging {

  def getGroupUsers(
    groupId: String
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[UserDetails]] = {
    val url = url"${appConfig.userGroupsSearchUrl}/users-groups-search/groups/$groupId/users"
    monitor(s"ConsumedAPI-UGS-getGroupUsers-GET") {
      httpClient
        .get(url)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case status if is2xx(status) => response.json.as[Seq[UserDetails]]
            case Status.NOT_FOUND =>
              logger.warn(s"Group $groupId not found in SCP")
              Seq.empty
            case other =>
              logger.error(s"Error in UGS-getGroupUsers: $other, ${response.body}")
              throw UpstreamErrorResponse(
                response.body,
                other,
                other
              )
          }
        }
    }
  }

}
