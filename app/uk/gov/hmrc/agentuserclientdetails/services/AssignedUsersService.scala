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

package uk.gov.hmrc.agentuserclientdetails.services

import akka.stream.Materializer
import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AssignedClient, Client}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.util.Throttler
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssignedUsersServiceImpl])
trait AssignedUsersService {

  def calculateClientsWithAssignedUsers(groupId: String)(implicit
    hc: HeaderCarrier
  ): Future[Seq[AssignedClient]]
}

@Singleton
class AssignedUsersServiceImpl @Inject() (
  es3CacheManager: Es3CacheManager,
  espConnector: EnrolmentStoreProxyConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends AssignedUsersService with Logging {

  logger.info(s"ES0 requests set to throttle at ${appConfig.es0MaxRequestsPerSecond} per second")

  override def calculateClientsWithAssignedUsers(
    groupId: String
  )(implicit hc: HeaderCarrier): Future[Seq[AssignedClient]] =
    for {
      clients         <- es3CacheManager.getCachedClients(groupId)
      assignedClients <- Throttler.process(clients, appConfig.es0MaxRequestsPerSecond)(fetchAssignedUsersOfClient)
    } yield assignedClients

  private def fetchAssignedUsersOfClient(client: Client)(implicit hc: HeaderCarrier): Future[Seq[AssignedClient]] =
    espConnector.getUsersAssignedToEnrolment(client.enrolmentKey, "delegated") map { usersIdsAssignedToClient =>
      usersIdsAssignedToClient.map(userId =>
        AssignedClient(
          clientEnrolmentKey = client.enrolmentKey,
          friendlyName = None,
          assignedTo = userId
        )
      )
    }

}
