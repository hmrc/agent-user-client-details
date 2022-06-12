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

package uk.gov.hmrc.agentuserclientdetails.services

import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, UserDetails}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.{EnrolmentStoreProxyConnector, UsersGroupsSearchConnector}
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSize, AgentSizeRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.workitem._

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentChecksService @Inject() (appConfig: AppConfig, agentSizeRepository: AgentSizeRepository,
                                    enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector, usersGroupsSearchConnector: UsersGroupsSearchConnector,
                                    workItemService: WorkItemService) extends Logging {

  private val ENROLMENT_STATE_ACTIVATED = "Activated"
  private val outstandingProcessingStatuses: Set[ProcessingStatus] = Set(ToDo, InProgress, Failed, Deferred)

  def getAgentSize(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[AgentSize]] = {
    agentSizeRepository.get(arn) flatMap {
      case Some(agentSize) if withinRefreshDuration(agentSize.refreshedDateTime) =>
        Future.successful(Option(agentSize))
      case _ =>
        for {
          maybeClientCount <- fetchClientCount(arn)
          maybeAgentSize <- maybeClientCount match {
            case None =>
              Future.successful(None)
            case Some(clientCount) =>
              saveAgentSize(arn, clientCount)
          }
        } yield maybeAgentSize
    }
  }

  def userCheck(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Int] = {
    for {
      maybeGroupId <- enrolmentStoreProxyConnector.getPrincipalGroupIdFor(arn)
      groupUsers <- maybeGroupId match {
        case None =>
          Future.successful(Seq.empty)
        case Some(groupId) =>
          usersGroupsSearchConnector.getGroupUsers(groupId)
      }
    } yield groupUsers.size
  }

  def outstandingWorkItemsExist(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean] = {
    for {
      maybeGroupId <- enrolmentStoreProxyConnector.getPrincipalGroupIdFor(arn)
      workItems <- maybeGroupId match {
        case None =>
          Future.successful(Seq.empty)
        case Some(groupId) =>
          workItemService.query(groupId, None)
      }
    } yield {
      workItems match {
        case items if items.exists(item => outstandingProcessingStatuses.contains(item.status)) => true
        case _ => false
      }
    }
  }

  def getTeamMembers(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[UserDetails]] = {
    for {
      maybeGroupId <- enrolmentStoreProxyConnector.getPrincipalGroupIdFor(arn)
      groupUsers <- maybeGroupId match {
        case None =>
          Future.successful(Seq.empty)
        case Some(groupId) =>
          usersGroupsSearchConnector.getGroupUsers(groupId)
      }
    } yield groupUsers
  }

  private def withinRefreshDuration(refreshedDateTime: LocalDateTime): Boolean = {
    refreshedDateTime.isAfter(LocalDateTime.now().minusSeconds(appConfig.agentsizeRefreshDuration.toSeconds))
  }

  private def fetchClientCount(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[Int]] = {
    for {
      maybeGroupId <- enrolmentStoreProxyConnector.getPrincipalGroupIdFor(arn)
      clientCount <- maybeGroupId match {
        case None =>
          Future.successful(None)
        case Some(groupId) =>
          enrolmentStoreProxyConnector.getEnrolmentsForGroupId(groupId)
            .map(enrolments => Option(enrolments.count(_.state == ENROLMENT_STATE_ACTIVATED)))
      }
    } yield clientCount
  }

  private def saveAgentSize(arn: Arn, clientCount: Int)(implicit ec: ExecutionContext): Future[Option[AgentSize]] = {
    val agentSize = AgentSize(arn, clientCount, LocalDateTime.now())

    for {
      maybeUpsertType <- agentSizeRepository.upsert(agentSize)
    } yield {
      maybeUpsertType.map { upsertType =>
        logger.info(s"AgentSize saved. Type: $upsertType")
        agentSize
      }
    }
  }

}
