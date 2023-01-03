/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.agentuserclientdetails.services.{AssignmentsWorkItemService, AssignmentsWorker, FriendlyNameWorkItemService, FriendlyNameWorker, JobMonitoringService, JobMonitoringWorker}
import uk.gov.hmrc.clusterworkthrottling.ServiceInstances

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AgentUserClientDetailsMain @Inject() (
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle,
  friendlyNameWorker: FriendlyNameWorker,
  friendlyNameWorkItemService: FriendlyNameWorkItemService,
  assignmentsWorker: AssignmentsWorker,
  assignmentsWorkItemService: AssignmentsWorkItemService,
  jobMonitoringWorker: JobMonitoringWorker,
  jobMonitoringService: JobMonitoringService,
  serviceInstances: ServiceInstances,
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
    initialDelay = appConfig.jobMonitoringWorkerInitialDelaySeconds.seconds,
    interval = appConfig.jobMonitoringWorkerIntervalSeconds.seconds
  ) {
    jobMonitoringWorker.running.get() match {
      case true =>
        logger.debug("[Job monitor] Was already running, so I did not trigger it again.")
      case false =>
        logger.debug("[Job monitor] Triggered")
        jobMonitoringWorker.start()
    }
  }

  actorSystem.scheduler.schedule(
    initialDelay = appConfig.serviceJobInitialDelaySeconds.seconds,
    interval = appConfig.serviceJobIntervalSeconds.seconds
  ) {
    // This is necessary so that we can keep track of how many instances are running, for request throttling purposes.
    def heartbeat(): Future[Unit] = serviceInstances
      .heartbeat()
      .map { nrInstances =>
        logger.info(s"[ServiceInstances] $nrInstances running instance(s) detected.")
      }
      .recover { case e =>
        logger.error(s"[ServiceInstance] Heartbeat failed: $e")
      }

    // Print repo stats.
    def friendlyNameRepoStats(): Future[Unit] = friendlyNameWorkItemService.collectStats
      .map { stats =>
        logger.info(
          s"[Friendly name job] Work item stats: ${if (stats.isEmpty) "No work items" else Json.toJson(stats).toString}"
        )
      }
      .recover { case _ => () }
    def assignmentsRepoStats(): Future[Unit] = assignmentsWorkItemService.collectStats
      .map { stats =>
        logger.info(
          s"[Assign enrolment job] Work item stats: ${if (stats.isEmpty) "No work items"
            else Json.toJson(stats).toString}"
        )
      }
      .recover { case _ => () }

    // Perform cleanup of completed items.
    val cleanupJobs = Seq(
      "Assign enrolment job" -> (() => assignmentsWorkItemService.cleanup(Instant.now())),
      "Friendly name job"    -> (() => friendlyNameWorkItemService.cleanup(Instant.now())),
      "Job monitor"          -> (() => jobMonitoringService.cleanup(Instant.now()))
    )
    def cleanup(): Future[Unit] = Future
      .traverse(cleanupJobs) { case (name, f) =>
        logger.info(s"[$name] Starting work item cleanup.")
        f()
          .map { result =>
            logger.info(s"[$name] Work item cleanup complete. ${result.getDeletedCount} work items deleted.")
          }
          .recover { case e =>
            logger.error(s"[$name] Work item cleanup threw an exception: $e.")
            () // We do not want any of these jobs failing to bring down the whole thread.
          }
      }
      .map(_ => ())

    logger.info("Service job started.")
    for {
      _ <- heartbeat()
      _ <- friendlyNameRepoStats()
      _ <- assignmentsRepoStats()
      _ <- cleanup()
      _ = logger.info("Service job finished.")
    } yield ()
  }
}
