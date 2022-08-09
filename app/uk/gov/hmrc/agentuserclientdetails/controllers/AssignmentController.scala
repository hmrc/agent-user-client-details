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

package uk.gov.hmrc.agentuserclientdetails.controllers

import org.joda.time.DateTime
import play.api.Logging
import play.api.libs.json.JsValue
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.{UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, Unassign}
import uk.gov.hmrc.agentuserclientdetails.services.AssignmentsWorkItemService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.workitem.ToDo

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class AssignmentController @Inject() (
  cc: ControllerComponents,
  workItemService: AssignmentsWorkItemService,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def assignEnrolments: Action[JsValue] = Action.async(parse.json) { implicit request =>
    lazy val mSessionId: Option[String] =
      if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
      else None // only required for local testing against stubs
    withJsonBody[UserEnrolmentAssignments] { aer =>
      val assignWorkItems = aer.assign.map { case UserEnrolment(userId, enrolmentKey) =>
        AssignmentWorkItem(Assign, userId, enrolmentKey, aer.arn.value, mSessionId)
      }
      val unassignWorkItems = aer.unassign.map { case UserEnrolment(userId, enrolmentKey) =>
        AssignmentWorkItem(Unassign, userId, enrolmentKey, aer.arn.value, mSessionId)
      }
      for {
        _ <- workItemService.pushNew(unassignWorkItems.toSeq ++ assignWorkItems.toSeq, DateTime.now(), ToDo)
      } yield Accepted
    }
  }
}
