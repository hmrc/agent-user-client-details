/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentuserclientdetails.testOnly.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request, RequestHeader, Result}
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.{EnrolmentStoreProxyConnector, HipConnector}
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSizeRepository, AssignmentsWorkItemRepository, Es3CacheRepository, FriendlyNameWorkItemRepository}
import uk.gov.hmrc.agentuserclientdetails.services.{ES3CacheService, FriendlyNameWorkItemService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.net.URL
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class TestOnlyController @Inject() (
  agentSizeRepository: AgentSizeRepository,
  assignmentsWorkItemRepository: AssignmentsWorkItemRepository,
  es3CacheRepository: Es3CacheRepository,
  friendlyNameWorkItemRepository: FriendlyNameWorkItemRepository,
  hipConnector: HipConnector,
  appConfig: AppConfig,
  httpClient: HttpClientV2,
  espConnector: EnrolmentStoreProxyConnector,
  es3CacheService: ES3CacheService,
  workItemService: FriendlyNameWorkItemService
)(implicit ec: ExecutionContext, cc: ControllerComponents)
    extends BackendController(cc) with Logging {

  def getTradingDetailsForMtdItId(mtdItId: String): Action[AnyContent] = Action.async { implicit request =>
    hipConnector
      .getTradingDetailsForMtdItId(MtdItId(mtdItId))
      .map(response => Ok(response.toString))
  }

  def hipConnectivityTest(hipPath: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val queryParams: Map[String, String] = request.queryString.view.mapValues(_.headOption.getOrElse("")).toMap

    val url: URL = uri"${appConfig.hipBaseUrl}"
      .withWholePath(hipPath)
      .withParams(queryParams)
      .toJavaUri
      .toURL

    import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

    httpClient
      .get(url)
      .setHeader(request.headers.headers *)
      .execute[HttpResponse]
      .map(response => Status(response.status)(response.body))
  }

  // called from agents-external-stubs perf-test data generator. Cleans test data prior to running perf-tests.
  def deleteTestData(arn: String, groupId: String): Action[AnyContent] = Action.async { _ =>
    for {
      a <- agentSizeRepository.delete(arn)
      b <- assignmentsWorkItemRepository.deleteWorkItems(arn)
      c <- es3CacheRepository.deleteCache(groupId)
      d <- friendlyNameWorkItemRepository.deleteWorkItems(groupId)
      _ = logger.info(
            s"Deleted test-data for $arn $groupId: agentSize: $a, assignmentsWi: $b, es3Cache: $c, friendlyNameWi: $d"
          )
    } yield Ok
  }

  def forceRefreshFriendlyNames(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withGroupIdFor(arn) { groupId =>
      forceRefreshFriendlyNamesForGroupIdFn(groupId)
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

    es3CacheService.getClients(groupId).transformWith {
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
      case Failure(exception: Throwable) =>
        Future.failed(exception)
    }
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
}
