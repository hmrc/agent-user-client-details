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

import org.mongodb.scala.bson.ObjectId
import play.api.http.HttpEntity.NoEntity
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.auth.{AuthAction, AuthorisedAgentSupport}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.{DesConnector, EnrolmentStoreProxyConnector, UsersGroupsSearchConnector}
import uk.gov.hmrc.agentuserclientdetails.model.{FriendlyNameJobData, FriendlyNameWorkItem, PaginatedClientsBuilder}
import uk.gov.hmrc.agentuserclientdetails.services.{AssignedUsersService, Es3CacheManager, FriendlyNameWorkItemService, JobMonitoringService}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class ClientListController @Inject() (
  cc: ControllerComponents,
  workItemService: FriendlyNameWorkItemService,
  espConnector: EnrolmentStoreProxyConnector,
  es3CacheManager: Es3CacheManager,
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  assignedUsersService: AssignedUsersService,
  jobMonitoringService: JobMonitoringService,
  desConnector: DesConnector,
  appConfig: AppConfig
)(implicit authAction: AuthAction, ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedAgentSupport {

  def getClients(arn: Arn, sendEmail: Option[Boolean] = None, lang: Option[String] = None): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorisedAgent(allowStandardUser = true) { _ =>
        withGroupIdFor(arn) { groupId =>
          getClientsFn(arn, groupId, sendEmail.getOrElse(false), lang)
        }
      }
    }

  def getTaxGroupInfo(arn: Arn): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorisedAgent(allowStandardUser = true) { _ =>
        withGroupIdFor(arn) { groupId =>
          es3CacheManager
            .getCachedClients(groupId)
            .map(_.map(client => EnrolmentKey.deconstruct(client.enrolmentKey)))
            .map(tuples => tuples.groupBy(_._1))
            .map(_.map(entry => entry._1 -> entry._2.length))
            .map(m => Ok(Json.toJson(m)))
        }
      }
    }

  def getPaginatedClients(
    arn: Arn,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { _ =>
      withGroupIdFor(arn) { groupId =>
        es3CacheManager
          .getCachedClients(groupId)
          .map { clients =>
            val clientsMatchingSearch = search.fold(clients) { searchTerm =>
              clients.filter(c => c.friendlyName.toLowerCase.contains(searchTerm.toLowerCase))
            }
            val taxServiceFilteredClients = filter.fold(clientsMatchingSearch) { term =>
              if (term == "TRUST") clientsMatchingSearch.filter(_.enrolmentKey.contains("HMRC-TERS"))
              else clientsMatchingSearch.filter(_.enrolmentKey.contains(term))
            }

            Ok(Json.toJson(PaginatedClientsBuilder.build(page, pageSize, taxServiceFilteredClients)))
          }
      }
    }
  }

  def getClientListStatus(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      withGroupIdFor(arn) { groupId =>
        getClientsFn(arn, groupId, false, None).map { result =>
          result.header.status match {
            case OK | ACCEPTED => result.copy(body = NoEntity)
            case _             => result
          }
        }
      }
    }
  }

  def forceRefreshFriendlyNames(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      withGroupIdFor(arn) { groupId =>
        forceRefreshFriendlyNamesForGroupIdFn(groupId)
      }
    }
  }

  def getOutstandingWorkItemsForGroupId(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      getOutstandingWorkItemsForGroupIdFn(groupId)
    }
  }

  def getClientsWithAssignedUsers(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      withGroupIdFor(arn) { groupId =>
        calculateClientsWithAssignedUsers(groupId) flatMap {
          case Nil =>
            Future successful NotFound
          case assignedClients =>
            for {
              userIdsFromUgs <- usersGroupsSearchConnector.getGroupUsers(groupId).map(_.flatMap(_.userId))
              assignedClientsWithUgsFilteredUsers =
                assignedClients.filter(client => userIdsFromUgs.contains(client.assignedTo))
            } yield Ok(Json.toJson(GroupDelegatedEnrolments(assignedClientsWithUgsFilteredUsers)))
        }

      }
    }
  }

  private def calculateClientsWithAssignedUsers(
    groupId: String
  )(implicit hc: HeaderCarrier): Future[Seq[AssignedClient]] = {

    def processInSeries[A, B](
      itemsToProcess: Iterable[A]
    )(processor: A => Future[B])(implicit ec: ExecutionContext): Future[List[B]] =
      itemsToProcess.foldLeft(Future(List.empty[B])) { (accFutures, nextItem) =>
        accFutures.flatMap { accList =>
          val nextFuture = processor(nextItem)
          nextFuture.map(nextResult => accList :+ nextResult)
        }
      }

    def fetchAssignedUsersOfClient(client: Client): Future[Seq[AssignedClient]] =
      espConnector.getUsersAssignedToEnrolment(client.enrolmentKey, "delegated") map { usersIdsAssignedToClient =>
        usersIdsAssignedToClient.map(userId =>
          AssignedClient(
            clientEnrolmentKey = client.enrolmentKey,
            friendlyName = None,
            assignedTo = userId
          )
        )
      }

    es3CacheManager.getCachedClients(groupId) flatMap { clients =>
      processInSeries(clients)(fetchAssignedUsersOfClient).map(_.flatten)
    }
  }

  def getOutstandingWorkItemsForArn(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      withGroupIdFor(arn) { groupId =>
        getOutstandingWorkItemsForGroupIdFn(groupId)
      }
    }
  }

  protected def getClientsFn(
    arn: Arn,
    groupId: String,
    sendEmail: Boolean, // whether to send an email to inform the agent that the fetching of client names has finished
    lang: Option[String] // The language to be used for notification emails. "en" or "cy"
  )(implicit request: RequestHeader): Future[Result] = {
    def makeWorkItem(client: Client)(implicit hc: HeaderCarrier): FriendlyNameWorkItem = {
      val mSessionId: Option[String] =
        if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
        else None // only required for local testing against stubs
      FriendlyNameWorkItem(groupId, client, mSessionId)
    }

    es3CacheManager.getCachedClients(groupId).transformWith {
      // if friendly names are populated for all enrolments, return 200
      case Success(clients) if clients.forall(_.friendlyName.nonEmpty) =>
        logger.info(s"${clients.length} enrolments found for groupId $groupId. No friendly name lookups needed.")
        Future.successful(Ok(Json.toJson(clients)))
      // Otherwise ...
      case Success(clients) =>
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
          _ <- workItemService.pushNew(toBeAdded.map(client => makeWorkItem(client)), Instant.now(), ToDo)
          _ <- createFriendlyNameMonitoringJob(arn, groupId, clientsWantingName, sendEmail, lang.getOrElse("en"))
          // ^ Note: we must put _all_ the clients that lack a name in the list of the monitoring job, not just those that we are _adding_ to the work repo now,
          // because there could be some work items created by a previous call that we still need to check for completion.
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

    es3CacheManager.getCachedClients(groupId).transformWith {
      case Success(clients) =>
        for {
          _ <- workItemService.removeByGroupId(groupId)
          _ =
            logger.info(
              s"FORCED client list request for groupId $groupId. All work items for this groupId have been deleted and ${clients.length} new work items will be created."
            )
          clientsWithoutName = clients.map(_.copy(friendlyName = ""))
          _ <- workItemService.pushNew(clientsWithoutName.map(client => makeWorkItem(client)), Instant.now(), ToDo)
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

  def getWorkItemStats: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      workItemService.collectStats.map { stats =>
        Ok(Json.toJson(stats))
      }
    }
  }

  def cleanupWorkItems: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { _ =>
      workItemService.cleanup(Instant.now()).map { result =>
        Ok(JsNumber(result.getDeletedCount))
      }
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

  def createFriendlyNameMonitoringJob(
    arn: Arn,
    groupId: String,
    toBeAdded: Seq[Client],
    sendEmail: Boolean,
    lang: String // 'en' or 'cy' -- defaults to 'en' if invalid
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[ObjectId]] =
    if (toBeAdded.isEmpty) Future.successful(None)
    else {
      for {
        maybeAgentDetailsDesResponse <- desConnector.getAgencyDetails(arn)
        _ =
          if (maybeAgentDetailsDesResponse.isEmpty)
            logger.warn(
              s"Agency details could not be retrieved for ${arn.value}. It will not be possible to notify them by email when the client name fetch job is complete."
            )
        maybeObjectId <- maybeAgentDetailsDesResponse.fold[Future[Option[ObjectId]]](Future successful None) {
                           agentDetailsDesResponse =>
                             val agencyName = agentDetailsDesResponse.agencyDetails.flatMap(_.agencyName)
                             val agencyEmail = agentDetailsDesResponse.agencyDetails.flatMap(_.agencyEmail)
                             jobMonitoringService
                               .createFriendlyNameFetchJobData(
                                 FriendlyNameJobData(
                                   groupId = groupId,
                                   enrolmentKeys = toBeAdded.map(_.enrolmentKey),
                                   sendEmailOnCompletion = sendEmail,
                                   agencyName = agencyName,
                                   email = agencyEmail,
                                   emailLanguagePreference =
                                     if (List("cy", "en").contains(lang)) Some(lang)
                                     else Some("en")
                                 )
                               )
                               .map(Some(_))
                         }
      } yield maybeObjectId

    }
}
