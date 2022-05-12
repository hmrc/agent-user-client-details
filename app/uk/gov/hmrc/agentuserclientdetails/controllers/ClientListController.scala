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
import uk.gov.hmrc.agentuserclientdetails.services.WorkItemService
import uk.gov.hmrc.agentuserclientdetails.util.EnrolmentKey
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.workitem.{PermanentlyFailed, ToDo}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class ClientListController @Inject()(
                                      cc: ControllerComponents,
                                      workItemService: WorkItemService,
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
        val esWithNoFriendlyName = enrolments.filter(_.friendlyName.isEmpty)
        for {
          wisAlreadyInRepo <- workItemService.query(groupId, None)
          esAlreadyInRepo = wisAlreadyInRepo.map(_.item.enrolment)
          esPermanentlyFailed = wisAlreadyInRepo.filter(_.status == PermanentlyFailed).map(_.item.enrolment)
          // We don't want to retry 'permanently failed' enrolments (Those with no name available in DES/IF, or if
          // we know that the call will not succeed if tried again). In this case simply return blank friendly names.
          esWantingName = setDifference(esWithNoFriendlyName, esPermanentlyFailed)
          // We don't want to add to the work items anything that is already in it (whether to-do, failed, duplicate etc.)
          toBeAdded = setDifference(esWantingName, esAlreadyInRepo)
          _ = logger.info(s"Client list request for groupId $groupId. Found: ${enrolments.length}, of which ${esWithNoFriendlyName.length} without a friendly name. (${esAlreadyInRepo.length} work items already in repository, of which ${esPermanentlyFailed.length} permanently failed. ${toBeAdded.length} new work items to create.)")
          _ <- workItemService.pushNew(toBeAdded.map(enrolment => makeWorkItem(enrolment)), DateTime.now(), ToDo)
        } yield {
          if (esWantingName.isEmpty)
            Ok(Json.toJson(enrolments))
          else
            Accepted(Json.toJson(enrolments))
        }
      case Failure(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) =>
        Future.failed(uer)
    }
  }

  def getOutstandingWorkItemsForGroupId(groupId: String): Action[AnyContent] = Action.async { _ =>
    workItemService.query(groupId, None).map { wis =>
      Ok(Json.toJson[Seq[Enrolment]](wis.map(_.item.enrolment)))
    }
  }

  def getWorkItemStats: Action[AnyContent] = Action.async { _ =>
    workItemService.collectStats.map { stats =>
      Ok(Json.toJson(stats))
    }
  }

  def cleanupWorkItems: Action[AnyContent] = Action.async { _ =>
    implicit val writeErrorFormat = Json.format[WriteError]
    workItemService.cleanup.map {
      case result if result.ok =>
        Ok(JsNumber(result.n))
      case result if !result.ok =>
        InternalServerError(Json.toJson(result.writeErrors))
    }
  }

  /*
  Perform set difference based on enrolment keys.
   */
  private def setDifference(e1s: Seq[Enrolment], e2s: Seq[Enrolment]): Seq[Enrolment] = {
    val e2eks = e2s.map(EnrolmentKey.enrolmentKeys)
    e1s.filterNot(enrolment => e2eks.contains(EnrolmentKey.enrolmentKeys(enrolment)))
  }
}
