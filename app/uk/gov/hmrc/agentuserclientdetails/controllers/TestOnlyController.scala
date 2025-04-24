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

package uk.gov.hmrc.agentuserclientdetails.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentuserclientdetails.connectors.HipConnector
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSizeRepository, AssignmentsWorkItemRepository, Es3CacheRepository, FriendlyNameWorkItemRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject() (
  agentSizeRepository: AgentSizeRepository,
  assignmentsWorkItemRepository: AssignmentsWorkItemRepository,
  es3CacheRepository: Es3CacheRepository,
  friendlyNameWorkItemRepository: FriendlyNameWorkItemRepository,
  hipConnector: HipConnector
)(implicit ec: ExecutionContext, cc: ControllerComponents)
    extends BackendController(cc) with Logging {

  def getTradingDetailsForMtdItId(mtdItId: String) = Action.async { implicit request =>
    hipConnector
      .getTradingDetailsForMtdItId(MtdItId(mtdItId))
      .map(response => Ok(response.toString()))

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
}
