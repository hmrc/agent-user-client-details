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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, Enrolment, EnrolmentKey}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.services.FriendlyNameWorkItemService
import uk.gov.hmrc.agentuserclientdetails.util.StatusUtil
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.workitem.ToDo

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class FriendlyNameController @Inject() (
  cc: ControllerComponents,
  workItemService: FriendlyNameWorkItemService,
  espConnector: EnrolmentStoreProxyConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def updateFriendlyName(arn: Arn): Action[JsValue] = Action.async(parse.json) { implicit request =>
    lazy val mSessionId: Option[String] =
      if (appConfig.stubsCompatibilityMode) hc.sessionId.map(_.value)
      else None // only required for local testing against stubs
    withGroupIdForArn(arn) { groupId =>
      withJsonBody[Seq[Enrolment]] { enrolments =>
        val clientsToUpdate: Seq[Client] =
          enrolments.flatMap(enr => EnrolmentKey.enrolmentKeys(enr).map(Client(_, enr.friendlyName)))
        val processAsynchronously = clientsToUpdate.length > appConfig.maxFriendlyNameUpdateBatchSize
        val (clientsToDoNow, clientsToDoLater) =
          if (processAsynchronously) (Seq.empty, clientsToUpdate) else (clientsToUpdate, Seq.empty)
        for {
          // Try making any ES19 calls before returning. Keep any failures for later inspection.
          results: Seq[Option[(Client, Throwable)]] <- Future.traverse(clientsToDoNow) { client =>
                                                         espConnector
                                                           .updateEnrolmentFriendlyName(
                                                             groupId,
                                                             client.enrolmentKey,
                                                             client.friendlyName
                                                           )
                                                           .transformWith {
                                                             case Success(()) => Future.successful(None)
                                                             case Failure(e)  => Future.successful(Some((client, e)))
                                                           }
                                                       }
          failures: Seq[(Client, Throwable)] = results.flatten
          // Check which failures are temporary and could be retried.
          (retriableFailures, permanentFailures) = failures.partition { case (_, e) => StatusUtil.isRetryable(e) }
          // Add the tasks to be retried to the work item repository.
          workItemsForLater = (retriableFailures.map(_._1) ++ clientsToDoLater).map(client =>
                                FriendlyNameWorkItem(groupId, client, mSessionId)
                              )
          _ <- workItemService.pushNew(workItemsForLater, DateTime.now(), ToDo)
          permanentlyFailedEnrolments =
            enrolments.filter(enr =>
              permanentFailures.map(_._1.enrolmentKey).contains(Client.fromEnrolment(enr).enrolmentKey)
            )
          info = Json.obj(
                   "delayed"           -> Json.toJson((retriableFailures.map(_._1) ++ clientsToDoLater)),
                   "permanentlyFailed" -> Json.toJson(permanentFailures.map(_._1))
                 )
        } yield
          if (workItemsForLater.nonEmpty) Accepted(info)
          else Ok(info)
      }
    }
  }

  private def withGroupIdForArn(
    arn: Arn
  )(f: String => Future[Result])(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    espConnector.getPrincipalGroupIdFor(arn).flatMap {
      case Some(groupId) => f(groupId)
      case None          => Future.successful(NotFound(s"No group id for ARN ${arn.value}."))
    }
}
