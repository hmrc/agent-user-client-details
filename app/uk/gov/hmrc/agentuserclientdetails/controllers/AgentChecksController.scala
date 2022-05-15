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

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.repositories.AgentSize
import uk.gov.hmrc.agentuserclientdetails.services.AgentChecksService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class AgentChecksController @Inject()(agentChecksService: AgentChecksService, cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def getAgentSize(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    agentChecksService.getAgentSize(arn).transformWith {
      case Success(maybeAgentSize) => maybeAgentSize match {
        case None => Future.successful(NotFound)
        case Some(agentSize) => Future.successful(Ok(generateStatusJson(agentSize)))
      }
      case Failure(uer: UpstreamErrorResponse) if uer.statusCode == NOT_FOUND =>
        logger.warn(s"Details for Arn not found: ${uer.message}")
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) if uer.statusCode == UNAUTHORIZED =>
        logger.warn(s"Request was not authorized: ${uer.message}")
        Future.successful(Unauthorized)
      case Failure(uer: UpstreamErrorResponse) =>
        logger.warn(s"Error from backend: ${uer.statusCode}, ${uer.reportAs}, ${uer.message}")
        Future.successful(InternalServerError)
      case Failure(ex: Exception) =>
        logger.warn(s"Error encountered: ${ex.getMessage}")
        Future.successful(InternalServerError)
    }
  }

  def userCheck(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    agentChecksService.userCheck(arn).transformWith {
      case Success(count) => if (count > 1 ) Future.successful(NoContent) else Future.successful(Forbidden)
      case Failure(uer: UpstreamErrorResponse) if uer.statusCode == NOT_FOUND =>
        logger.warn(s"Details for Arn not found: ${uer.message}")
        Future.successful(NotFound)
      case Failure(uer: UpstreamErrorResponse) if uer.statusCode == UNAUTHORIZED =>
        logger.warn(s"Request was not authorized: ${uer.message}")
        Future.successful(Unauthorized)
      case Failure(uer: UpstreamErrorResponse) =>
        logger.warn(s"Error from backend: ${uer.statusCode}, ${uer.reportAs}, ${uer.message}")
        Future.successful(InternalServerError)
      case Failure(ex: Exception) =>
        logger.warn(s"Error encountered: ${ex.getMessage}")
        Future.successful(InternalServerError)
    }
  }

  private def generateStatusJson(agentSize: AgentSize): JsValue =
    Json.toJson(Json.obj("agent-size" -> agentSize.clientCount))
}
