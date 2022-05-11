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
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import reactivemongo.api.commands.WriteError
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.util.EnrolmentKey
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class ClientListController @Inject()(
                                      cc: ControllerComponents,
                                      workItemRepo: FriendlyNameWorkItemRepository,
                                      espConnector: EnrolmentStoreProxyConnector,
                                      appConfig: AppConfig
                                    )(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def getClientsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    def makeWorkItem(enrolment: Enrolment)(implicit hc: HeaderCarrier): FriendlyNameWorkItem = {
      val mSessionId: Option[String] = if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value) else None // only required for local testing against stubs
      FriendlyNameWorkItem(groupId, enrolment, mSessionId)
    }
    espConnector.getEnrolmentsForGroupId(groupId).transformWith {
      // if friendly names are populated for all enrolments, return 200
      case Success(enrolments) if enrolments.forall(_.friendlyName.nonEmpty) =>
        logger.info(s"${enrolments.length} enrolments found for groupId $groupId. No friendly name lookups needed.")
        Future.successful(Ok(Json.toJson(enrolments)))
      // Otherwise ...
      case Success(enrolments) =>
        val enrolmentsWithoutFriendlyName = enrolments.filter(_.friendlyName.isEmpty)
        // We don't want to retry 'permanently failed' enrolments (Those with no name available in DES/IF, or if
        // we know that the call will not succeed if tried again). In this case simply return blank friendly names.
        excludePermanentlyFailed(groupId, enrolmentsWithoutFriendlyName).flatMap { todoEnrolments =>
          val nPermanentlyFailed = enrolmentsWithoutFriendlyName.length - todoEnrolments.length
          logger.info(s"Client list request for groupId $groupId. Found: ${enrolments.length}, permanently failed: $nPermanentlyFailed, friendly name lookups needed: ${todoEnrolments.length}.")
          if (todoEnrolments.isEmpty) {
            Future.successful(Ok(Json.toJson(enrolments)))
          } else {
            // create work items to retrieve the missing names and return 202
            workItemRepo.pushNew(todoEnrolments.map(enrolment => makeWorkItem(enrolment)), DateTime.now())
              .map(_ => Accepted(Json.toJson(enrolments)))
          }
        }
      case Failure(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) =>
        Future.failed(uer)
    }
  }

  def getOutstandingWorkItemsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    workItemRepo.queryByGroupId(groupId).map { wis =>
      Ok(Json.toJson[Seq[Enrolment]](wis.map(_.item.enrolment)))
    }
  }

  def getWorkItemStats: Action[AnyContent] = Action.async { implicit request =>
    workItemRepo.collectStats.map { stats =>
      Ok(Json.toJson(stats))
    }
  }

  def cleanupWorkItems: Action[AnyContent] = Action.async { implicit request =>
    implicit val writeErrorFormat = Json.format[WriteError]
    workItemRepo.cleanup.map {
      case result if result.ok =>
        Ok(JsNumber(result.n))
      case result if !result.ok =>
        InternalServerError(Json.toJson(result.writeErrors))
    }
  }

  private def excludePermanentlyFailed(groupId: String, enrolments: Seq[Enrolment]): Future[Seq[Enrolment]] =
    workItemRepo.queryPermanentlyFailedByGroupId(groupId).map { permanentlyFailedWorkItems =>
      val permanentlyFailedEnrolmentKeys = permanentlyFailedWorkItems.map(_.item.enrolment).map(EnrolmentKey.enrolmentKeys)
      enrolments.filterNot(enrolment => permanentlyFailedEnrolmentKeys.contains(EnrolmentKey.enrolmentKeys(enrolment)))
    }
}
