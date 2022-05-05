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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class ClientListController @Inject()(
                                           cc: ControllerComponents,
                                           workItemRepo: FriendlyNameWorkItemRepository,
                                           espConnector: EnrolmentStoreProxyConnector
                                         )(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def getClientsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    espConnector.getEnrolmentsForGroupId(groupId).flatMap {
      // if friendly names are populated for all enrolments, return 200
      case enrolments if enrolments.forall(_.friendlyName.nonEmpty) =>
        Future.successful(Ok(Json.toJson(enrolments)))
      // otherwise create work items to retrieve the missing names and return 202
      case enrolments =>
        val needFriendlyName = enrolments.filter(_.friendlyName.isEmpty)
        workItemRepo.pushNew(needFriendlyName.map(enrolment => FriendlyNameWorkItem(groupId, enrolment)), DateTime.now())
          .map(_ => Accepted(Json.toJson(enrolments)))
        // TODO: If some names are missing but they are marked as permanently failed return 200 anyway?
    }
  }
}
