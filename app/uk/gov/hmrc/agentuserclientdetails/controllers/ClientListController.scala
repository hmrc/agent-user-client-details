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
import play.api.i18n.Lang
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc._
import reactivemongo.api.commands.WriteError
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, GroupDelegatedEnrolments}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentuserclientdetails.connectors.{DesConnector, EnrolmentStoreProxyConnector}
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.{FriendlyNameJobData, JobMonitoringRepository}
import uk.gov.hmrc.agentuserclientdetails.services.{AssignedUsersService, ClientNameService, FriendlyNameWorkItemService}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.workitem.{Failed, PermanentlyFailed, ProcessingStatus, ToDo}

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class ClientListController @Inject() (
  cc: ControllerComponents,
  workItemService: FriendlyNameWorkItemService,
  espConnector: EnrolmentStoreProxyConnector,
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  assignedUsersService: AssignedUsersService,
  jobMonitoringRepository: JobMonitoringRepository,
  desConnector: DesConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def getClients(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      getClientsFn(arn, groupId)
    }
  }

  def getClientListStatus(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      getClientsFn(arn, groupId).map { result =>
        result.header.status match {
          case OK | ACCEPTED => result.copy(body = NoEntity)
          case _             => result
        }
      }
    }
  }

  def forceRefreshFriendlyNames(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      forceRefreshFriendlyNamesForGroupIdFn(groupId)
    }
  }

  def getOutstandingWorkItemsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    getOutstandingWorkItemsForGroupIdFn(groupId)
  }

  def getClientsWithAssignedUsers(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      espConnector.getGroupDelegatedEnrolments(groupId) flatMap {
        case None =>
          Future successful NotFound
        case Some(groupDelegatedEnrolments) =>
          for {
            assignedClients <- assignedUsersService.calculateClientsWithAssignedUsers(groupDelegatedEnrolments)
            userIdsFromUgs  <- usersGroupsSearchConnector.getGroupUsers(groupId).map(_.flatMap(_.userId))
            assignedClientsWithUgsFilteredUsers =
              assignedClients.filter(client => userIdsFromUgs.contains(client.assignedTo))
          } yield Ok(Json.toJson(GroupDelegatedEnrolments(assignedClientsWithUgsFilteredUsers)))
      }
    }
  }

  def getOutstandingWorkItemsForArn(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      getOutstandingWorkItemsForGroupIdFn(groupId)
    }
  }

  protected def getClientsFn(
    arn: Arn,
    groupId: String
  )(implicit request: RequestHeader): Future[Result] = {
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
          _ <- createFriendlyNameJobFetchEntry(arn, groupId, toBeAdded, langPreferenceFromHeaders.getOrElse(Lang("en")))
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

  private def langPreferenceFromHeaders(implicit request: RequestHeader): Option[Lang] =
    request.headers.get("PLAY_LANG").map(Lang(_))

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

  def getOutstandingWorkItemsForGroupIdFn(groupId: String): Future[Result] =
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

  private def withGroupIdFor(arn: Arn)(
    groupIdAction: String => Future[Result]
  )(implicit request: RequestHeader): Future[Result] =
    if (!Arn.isValid(arn.value))
      Future.successful(BadRequest("Invalid ARN"))
    else {
      espConnector.getPrincipalGroupIdFor(arn).transformWith {
        case Success(Some(groupId)) => groupIdAction(groupId)
        case Success(None) =>
          logger.error(s"ARN $arn not found.")
          Future.successful(NotFound): Future[Result]
        case Failure(uer: UpstreamErrorResponse) if uer.statusCode == NOT_FOUND    => Future.successful(NotFound)
        case Failure(uer: UpstreamErrorResponse) if uer.statusCode == UNAUTHORIZED => Future.successful(Unauthorized)
        case Failure(_) => Future.successful(InternalServerError)
      }
    }

  def createFriendlyNameJobFetchEntry(
    arn: Arn,
    groupId: String,
    toBeAdded: Seq[Client],
    lang: Lang
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BSONObjectID]] =
    if (toBeAdded.isEmpty) Future.successful(None)
    else {
      for {
        maybeAgentDetailsDesResponse <- desConnector.getAgencyDetails(arn)
        _ =
          if (maybeAgentDetailsDesResponse.isEmpty)
            logger.warn(
              s"Agency details could not be retrieved for ${arn.value}. It will not be possible to notify them by email when the client name fetch job is complete."
            )
        maybeBSONObjectID <- maybeAgentDetailsDesResponse.fold[Future[Option[BSONObjectID]]](Future successful None) {
                               agentDetailsDesResponse =>
                                 val agencyName = agentDetailsDesResponse.agencyDetails.flatMap(_.agencyName)
                                 val agencyEmail = agentDetailsDesResponse.agencyDetails.flatMap(_.agencyEmail)
                                 jobMonitoringRepository.createFriendlyNameFetchJobData(
                                   FriendlyNameJobData(
                                     groupId = groupId,
                                     enrolmentKeys = toBeAdded.map(_.enrolmentKey),
                                     sendEmailOnCompletion = true,
                                     agencyName = agencyName,
                                     email = agencyEmail,
                                     emailLanguagePreference = Some(lang.code),
                                     startTime = LocalDateTime.now()
                                   )
                                 )
                             }
      } yield maybeBSONObjectID

    }
}
