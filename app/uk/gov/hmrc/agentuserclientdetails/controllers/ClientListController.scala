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
import play.api.http.HttpEntity.NoEntity
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc._
import reactivemongo.api.commands.WriteError
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, GroupDelegatedEnrolments}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.{EnrolmentStoreProxyConnector, UsersGroupsSearchConnector}
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.services.{AssignedUsersService, FriendlyNameWorkItemService}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.workitem.{Failed, PermanentlyFailed, ProcessingStatus, ToDo}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class ClientListController @Inject() (
  cc: ControllerComponents,
  workItemService: FriendlyNameWorkItemService,
  espConnector: EnrolmentStoreProxyConnector,
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  appConfig: AppConfig,
  assignedUsersService: AssignedUsersService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def getClientsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    getClientsForGroupIdFn(groupId)
  }

  def getClientsForArn(arn: String): Action[AnyContent] = Action.async { implicit request =>
    adaptForArn(getClientsForGroupIdFn)(arn)
  }

  def getClientListStatusForArn(arn: String): Action[AnyContent] = Action.async { implicit request =>
    adaptForArn(getClientsForGroupIdFn)(arn).map { result =>
      result.header.status match {
        case OK | ACCEPTED => result.copy(body = NoEntity)
        case _             => result
      }
    }
  }

  def forceRefreshFriendlyNamesForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    forceRefreshFriendlyNamesForGroupIdFn(groupId)
  }

  def forceRefreshFriendlyNamesForArn(arn: String): Action[AnyContent] = Action.async { implicit request =>
    adaptForArn(forceRefreshFriendlyNamesForGroupIdFn)(arn)
  }

  def getOutstandingWorkItemsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    getOutstandingWorkItemsForGroupIdFn(groupId)
  }

  def getOutstandingWorkItemsForArn(arn: String): Action[AnyContent] = Action.async { implicit request =>
    adaptForArn(getOutstandingWorkItemsForGroupIdFn)(arn)
  }

  def getClientsWithAssignedUsers(arn: String): Action[AnyContent] = Action.async { implicit request =>
    adaptForArn(getClientsWithAssignedUsersForGroupIdFn)(arn)
  }

  private def getClientsWithAssignedUsersForGroupIdFn(
    groupId: String
  )(implicit request: RequestHeader): Future[Result] =
    espConnector.getGroupDelegatedEnrolments(groupId) flatMap {
      case None =>
        Future successful NotFound
      case Some(groupDelegatedEnrolments) =>
        for {
          assignedClients <- assignedUsersService.calculate(groupDelegatedEnrolments)
          userIdsFromUgs  <- usersGroupsSearchConnector.getGroupUsers(groupId).map(_.flatMap(_.userId))
        } yield Ok(
          Json.toJson(
            GroupDelegatedEnrolments(assignedClients.filter(client => userIdsFromUgs.contains(client.assignedTo)))
          )
        )
    }

  protected def getClientsForGroupIdFn(groupId: String)(implicit request: RequestHeader): Future[Result] = {
    def makeWorkItem(client: Client)(implicit hc: HeaderCarrier): FriendlyNameWorkItem = {
      val mSessionId: Option[String] =
        if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
        else None // only required for local testing against stubs
      FriendlyNameWorkItem(groupId, client, mSessionId)
    }
    espConnector.getEnrolmentsForGroupId(groupId).transformWith {
      // if friendly names are populated for all enrolments, return 200
      case Success(enrolments) if enrolments.forall(_.friendlyName.nonEmpty) =>
        logger.info(s"${enrolments.length} enrolments found for groupId $groupId. No friendly name lookups needed.")
        val clients = enrolments.map(Client.fromEnrolment)
        Future.successful(Ok(Json.toJson(clients)))
      // Otherwise ...
      case Success(enrolments) =>
        val clients = enrolments.map(Client.fromEnrolment)
        val clientsWithNoFriendlyName = clients.filter(_.friendlyName.isEmpty)
        for {
          wisAlreadyInRepo <- workItemService.query(groupId, None)
          clientsAlreadyInRepo = wisAlreadyInRepo.map(_.item.client)
          clientsPermanentlyFailed = wisAlreadyInRepo.filter(_.status == PermanentlyFailed).map(_.item.client)
          // We don't want to retry 'permanently failed' enrolments (Those with no name available in DES/IF, or if
          // we know that the call will not succeed if tried again). In this case simply return blank friendly names.
          clientsWantingName = setDifference(clientsWithNoFriendlyName, clientsPermanentlyFailed)
          // We don't want to add to the work items anything that is already in it (whether to-do, failed, duplicate etc.)
          toBeAdded = setDifference(clientsWantingName, clientsAlreadyInRepo)
          _ =
            logger.info(
              s"Client list request for groupId $groupId. Found: ${clients.length}, of which ${clientsWithNoFriendlyName.length} without a friendly name. (${clientsAlreadyInRepo.length} work items already in repository, of which ${clientsPermanentlyFailed.length} permanently failed. ${toBeAdded.length} new work items to create.)"
            )
          _ <- workItemService.pushNew(toBeAdded.map(client => makeWorkItem(client)), DateTime.now(), ToDo)
        } yield
          if (clientsWantingName.isEmpty)
            Ok(Json.toJson(clients))
          else
            Accepted(Json.toJson(clients))
      case Failure(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) =>
        Future.failed(uer)
    }
  }

  protected def forceRefreshFriendlyNamesForGroupIdFn(
    groupId: String
  )(implicit request: RequestHeader): Future[Result] = {
    def makeWorkItem(client: Client)(implicit hc: HeaderCarrier): FriendlyNameWorkItem = {
      val mSessionId: Option[String] =
        if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
        else None // only required for local testing against stubs
      FriendlyNameWorkItem(groupId, client, mSessionId)
    }
    espConnector.getEnrolmentsForGroupId(groupId).transformWith {
      case Success(enrolments) =>
        val clients = enrolments.map(Client.fromEnrolment)
        for {
          _ <- workItemService.removeByGroupId(groupId)
          _ =
            logger.info(
              s"FORCED client list request for groupId $groupId. All work items for this groupId have been deleted and ${clients.length} new work items will be created."
            )
          clientsWithoutName = clients.map(_.copy(friendlyName = ""))
          _ <- workItemService.pushNew(clientsWithoutName.map(client => makeWorkItem(client)), DateTime.now(), ToDo)
        } yield Accepted
      case Failure(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) =>
        Future.failed(uer)
    }
  }

  def getOutstandingWorkItemsForGroupIdFn(groupId: String)(implicit request: RequestHeader): Future[Result] =
    workItemService.query(groupId, None).map { wis =>
      Ok(
        Json.toJson[Seq[Client]](
          wis.filter(wi => Set[ProcessingStatus](ToDo, Failed).contains(wi.status)).map(_.item.client)
        )
      )
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
  private def setDifference(c1s: Seq[Client], c2s: Seq[Client]): Seq[Client] = {
    val e2ek = c2s.map(_.enrolmentKey)
    c1s.filterNot(client => e2ek.contains(client.enrolmentKey))
  }

  private def adaptForArn(
    groupIdAction: String => Future[Result]
  )(arn: String)(implicit request: RequestHeader): Future[Result] =
    if (!Arn.isValid(arn)) {
      logger.error(s"Invalid ARN: $arn")
      Future.successful(BadRequest)
    } else
      espConnector.getPrincipalGroupIdFor(Arn(arn)).transformWith {
        case Success(Some(groupId)) => groupIdAction(groupId)
        case Success(None) =>
          logger.error(s"ARN $arn not found.")
          Future.successful(NotFound): Future[Result]
        case Failure(uer: UpstreamErrorResponse) if uer.statusCode == NOT_FOUND    => Future.successful(NotFound)
        case Failure(uer: UpstreamErrorResponse) if uer.statusCode == UNAUTHORIZED => Future.successful(Unauthorized)
        case Failure(_) => Future.successful(InternalServerError)
      }
}
