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

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AssignedClient, EnrolmentKey, GroupDelegatedEnrolments}
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[AssignedUsersServiceImpl])
trait AssignedUsersService {

  def calculateClientsWithAssignedUsers(groupDelegatedEnrolments: GroupDelegatedEnrolments)(implicit
    hc: HeaderCarrier
  ): Future[Seq[AssignedClient]]
}

@Singleton
class AssignedUsersServiceImpl @Inject() (espConnector: EnrolmentStoreProxyConnector)(implicit ec: ExecutionContext)
    extends AssignedUsersService with Logging {

  override def calculateClientsWithAssignedUsers(
    groupDelegatedEnrolments: GroupDelegatedEnrolments
  )(implicit hc: HeaderCarrier): Future[Seq[AssignedClient]] = {

    def checkAssignedToIsSomeCountOrAnId(assignedClient: AssignedClient): Try[Short] = Try(
      assignedClient.assignedTo.toShort
    )

    Future
      .sequence(groupDelegatedEnrolments.clients.map { assignedClient =>
        checkAssignedToIsSomeCountOrAnId(assignedClient) match {
          case Success(countAssignedUsers) =>
            if (countAssignedUsers == 0) {
              Future successful Seq.empty
            } else {
              val enrolmentKeys =
                assignedClient.identifiers.map(id => EnrolmentKey.enrolmentKey(assignedClient.serviceName, id.value))

              fetchUsersIdsAssignedToClient(enrolmentKeys).map(usersIdsAssignedToClient =>
                usersIdsAssignedToClient.map(userId => assignedClient.copy(assignedTo = userId))
              )
            }
          case Failure(_) =>
            Future successful Seq(assignedClient)
        }
      })
      .map(_.flatten)
  }

  private def fetchUsersIdsAssignedToClient(
    enrolmentKeys: Seq[String]
  )(implicit hc: HeaderCarrier): Future[Seq[String]] =
    Future
      .sequence(enrolmentKeys.map(key => espConnector.getUsersAssignedToEnrolment(key, "delegated")))
      .map(_.flatten)

}
