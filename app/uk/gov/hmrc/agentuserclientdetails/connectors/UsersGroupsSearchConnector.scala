/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.UserDetails
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpErrorFunctions, HttpResponse, UpstreamErrorResponse}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UsersGroupsSearchConnectorImpl])
trait UsersGroupsSearchConnector {
  def getGroupUsers(groupId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]]
}

@Singleton
class UsersGroupsSearchConnectorImpl @Inject() (httpClient: HttpClient, metrics: Metrics)(implicit appConfig: AppConfig)
    extends UsersGroupsSearchConnector with HttpAPIMonitor with HttpErrorFunctions with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  override def getGroupUsers(
    groupId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]] = {
    val url = new URL(s"${appConfig.userGroupsSearchUrl}/users-groups-search/groups/$groupId/users")
    monitor(s"ConsumedAPI-UGS-getGroupUsers-GET") {
      httpClient.GET[HttpResponse](url.toString).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.as[Seq[UserDetails]]
          case Status.NOT_FOUND =>
            logger.warn(s"Group $groupId not found in SCP")
            Seq.empty
          case other =>
            logger.error(s"Error in UGS-getGroupUsers: $other, ${response.body}")
            throw UpstreamErrorResponse(response.body, other, other)
        }
      }
    }
  }

}
