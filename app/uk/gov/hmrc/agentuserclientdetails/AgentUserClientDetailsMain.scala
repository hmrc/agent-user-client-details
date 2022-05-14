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
import uk.gov.hmrc.agentuserclientdetails.services.{FriendlyNameWorker, WorkItemService}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AgentUserClientDetailsMain @Inject()(
                           actorSystem: ActorSystem,
                           lifecycle: ApplicationLifecycle,
                           friendlyNameWorker: FriendlyNameWorker,
                           workItemService: WorkItemService,
                           appConfig: AppConfig)(implicit val ec: ExecutionContext)
  extends Logging {

  lifecycle.addStopHook(() =>
    Future {
      actorSystem.terminate()
    })

  actorSystem.scheduler.schedule(initialDelay = appConfig.jobRestartRepoQueueInitialDelaySeconds.seconds, interval = appConfig.jobRestartRepoQueueIntervalSeconds.second) {
    friendlyNameWorker.running.get() match {
      case true =>
        logger.debug("Friendly name fetching job was already running, so I did not trigger it again.")
      case false =>
        logger.debug("Friendly name fetching job triggered.")
        friendlyNameWorker.start()
    }
  }

  actorSystem.scheduler.schedule(initialDelay = appConfig.jobRepoCleanupInitialDelaySeconds.seconds, interval = appConfig.jobRepoCleanupIntervalSeconds.seconds) {
    logger.info("Starting work item repository cleanup.")
    workItemService.cleanup().map {
      case result if result.ok =>
        logger.info(s"Cleanup of work item repository complete. ${result.n} work items deleted.")
      case result if !result.ok =>
        logger.error(s"Cleanup of work item repository finished with errors. ${result.n} work items deleted, ${result.writeErrors.length} write errors.")
    }
  }

  actorSystem.scheduler.schedule(initialDelay = appConfig.jobLogRepoStatsQueueInitialDelaySeconds.seconds, interval = appConfig.jobLogRepoStatsQueueIntervalSeconds.seconds) {
    logger.info("Starting work item repository cleanup.")
    workItemService.collectStats.map { stats =>
      logger.info(s"Work item repository stats: ${Json.toJson(stats)}")
    }
  }
}
