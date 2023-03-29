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

package uk.gov.hmrc.agentuserclientdetails.controllers

import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, EnrolmentKey}
import uk.gov.hmrc.agentuserclientdetails.auth.{AuthAction, AuthorisedAgentSupport}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, Unassign}
import uk.gov.hmrc.agentuserclientdetails.services.AssignmentsWorkItemService
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

case class UserEnrolment(userId: String, enrolmentKey: String) {
  override def toString: String = s"$userId:$enrolmentKey"
}

object UserEnrolment {
  implicit val formats: Format[UserEnrolment] = Json.format
}

/** Represents the user/client combinations to assign and unassign in EACD.
  * @param assign
  *   combinations to assign using ES11 API
  * @param unassign
  *   combinations to unassign using ES12 API
  */
case class UserEnrolmentAssignments(assign: Set[UserEnrolment], unassign: Set[UserEnrolment], arn: Arn)

object UserEnrolmentAssignments {
  implicit val formats: Format[UserEnrolmentAssignments] = Json.format
}

@Singleton()
class AssignmentController @Inject() (
  cc: ControllerComponents,
  workItemService: AssignmentsWorkItemService,
  enrolmentStore: EnrolmentStoreProxyConnector,
  appConfig: AppConfig
)(implicit authAction: AuthAction, ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedAgentSupport {

  def assignEnrolments: Action[JsValue] = Action.async(parse.json) { implicit request =>
    lazy val mSessionId: Option[String] =
      if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
      else None // only required for local testing against stubs

    withAuthorisedAgent() { _ =>
      withJsonBody[UserEnrolmentAssignments] { aer =>
        val assignWorkItems = aer.assign.map { case UserEnrolment(userId, enrolmentKey) =>
          AssignmentWorkItem(Assign, userId, enrolmentKey, aer.arn.value, mSessionId)
        }
        val unassignWorkItems = aer.unassign.map { case UserEnrolment(userId, enrolmentKey) =>
          AssignmentWorkItem(Unassign, userId, enrolmentKey, aer.arn.value, mSessionId)
        }
        for {
          _ <- workItemService.pushNew(unassignWorkItems.toSeq ++ assignWorkItems.toSeq, Instant.now(), ToDo)
        } yield Accepted
      }
    }
  }

  /** Check that a given agent user has an expected list of assigned enrolments, and if not, generate work items so that
    * the user's assigned enrolment will match those provided. Note: This is meant to be called occasionally to
    * synchronise a given user with agent-permissions, with the expectation that most of the time there will be no
    * changes to do. It must be used with care and certainly should not be the routine way to manage assignments, as it
    * is a powerful and possibly expensive operation.
    */
  def ensureAssignments(arn: Arn, userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    lazy val mSessionId: Option[String] =
      if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
      else None // only required for local testing against stubs

    // TODO In order to avoid additional EACD calls we are not validating the ARN nor checking whether the userId
    // is really associated with the ARN. But should we?
    withAuthorisedAgent() { _ =>
      withJsonBody[Set[String]] { desiredEnrolmentKeys =>
        for {
          currentEnrolments <- enrolmentStore.getEnrolmentsAssignedToUser(userId)
          currentEnrolmentKeys = currentEnrolments.map(enr => EnrolmentKey.enrolmentKeys(enr).head).toSet
          toAdd = desiredEnrolmentKeys.diff(currentEnrolmentKeys)
          toRemove = currentEnrolmentKeys.diff(desiredEnrolmentKeys)
          isAlreadyInSync = toAdd.isEmpty && toRemove.isEmpty
          _ = if (isAlreadyInSync) logger.info(s"Assignment sync: userId $userId of $arn is already in sync")
              else
                logger.info(
                  s"Syncing assigned enrolments for userId $userId of $arn. To assign: $toAdd, to unassign: $toRemove"
                )
          assignWorkItems = toAdd.map { enrolmentKey =>
                              AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value, mSessionId)
                            }
          unassignWorkItems = toRemove.map { enrolmentKey =>
                                AssignmentWorkItem(Unassign, userId, enrolmentKey, arn.value, mSessionId)
                              }
          _ <- workItemService.pushNew(unassignWorkItems.toSeq ++ assignWorkItems.toSeq, Instant.now(), ToDo)
        } yield if (isAlreadyInSync) Ok else Accepted
      }.recover { case _: NotFoundException =>
        NotFound
      }
    }
  }

}
