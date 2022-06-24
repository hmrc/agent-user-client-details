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

package uk.gov.hmrc.agentuserclientdetails

import akka.actor.ActorSystem

import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.services.{AssignmentsWorkItemService, AssignmentsWorker, FriendlyNameWorkItemService, FriendlyNameWorker}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AgentUserClientDetailsMain @Inject() (
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle,
  friendlyNameWorker: FriendlyNameWorker,
  friendlyNameWorkItemService: FriendlyNameWorkItemService,
  assignmentsWorker: AssignmentsWorker,
  assignmentsWorkItemService: AssignmentsWorkItemService,
  appConfig: AppConfig
)(implicit val ec: ExecutionContext)
    extends Logging {

  lifecycle.addStopHook(() =>
    Future {
      actorSystem.terminate()
    }
  )

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.friendlyNameJobRestartRepoQueueInitialDelaySeconds.seconds,
    interval = appConfig.friendlyNameJobRestartRepoQueueIntervalSeconds.second
  ) {
    friendlyNameWorker.running.get() match {
      case true =>
        logger.debug("[Friendly name job] Was already running, so I did not trigger it again.")
      case false =>
        logger.debug("[Friendly name job] Triggered")
        friendlyNameWorker.start()
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.assignEnrolmentJobRestartRepoQueueInitialDelaySeconds.seconds,
    interval = appConfig.assignEnrolmentJobRestartRepoQueueIntervalSeconds.second
  ) {
    assignmentsWorker.running.get() match {
      case true =>
        logger.debug("[Assign enrolment job] Was already running, so I did not trigger it again.")
      case false =>
        logger.debug("[Assign enrolment job] Triggered")
        assignmentsWorker.start()
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.friendlyNameJobRepoCleanupInitialDelaySeconds.seconds,
    interval = appConfig.friendlyNameJobRepoCleanupIntervalSeconds.seconds
  ) {
    logger.info("[Friendly name job] Starting work item cleanup.")
    friendlyNameWorkItemService.cleanup().map {
      case result if result.ok =>
        logger.info(s"[Friendly name job] Work item cleanup complete. ${result.n} work items deleted.")
      case result if !result.ok =>
        logger.error(
          s"[Friendly name job] Work item cleanup finished with errors. ${result.n} work items deleted, ${result.writeErrors.length} write errors."
        )
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.assignEnrolmentJobRepoCleanupInitialDelaySeconds.seconds,
    interval = appConfig.assignEnrolmentJobRepoCleanupIntervalSeconds.seconds
  ) {
    logger.info("[Assign enrolment job] Starting work item cleanup.")
    assignmentsWorkItemService.cleanup().map {
      case result if result.ok =>
        logger.info(s"[Assign enrolment job] Work item cleanup complete. ${result.n} work items deleted.")
      case result if !result.ok =>
        logger.error(
          s"[Assign enrolment job] Work item cleanup finished with errors. ${result.n} work items deleted, ${result.writeErrors.length} write errors."
        )
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.friendlyNameJobLogRepoStatsQueueInitialDelaySeconds.seconds,
    interval = appConfig.friendlyNameJobLogRepoStatsQueueIntervalSeconds.seconds
  ) {
    friendlyNameWorkItemService.collectStats.map { stats =>
      logger.info(
        s"[Friendly name job] Work item stats: ${if (stats.isEmpty) "No work items" else Json.toJson(stats).toString}"
      )
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.assignEnrolmentJobLogRepoStatsQueueInitialDelaySeconds.seconds,
    interval = appConfig.assignEnrolmentJobLogRepoStatsQueueIntervalSeconds.seconds
  ) {
    assignmentsWorkItemService.collectStats.map { stats =>
      logger.info(
        s"[Assign enrolment job] Work item stats: ${if (stats.isEmpty) "No work items" else Json.toJson(stats).toString}"
      )
    }
  }
}
