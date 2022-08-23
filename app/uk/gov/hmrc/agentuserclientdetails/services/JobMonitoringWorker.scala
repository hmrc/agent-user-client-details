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

package uk.gov.hmrc.agentuserclientdetails.services

import com.mongodb.client.result.UpdateResult
import play.api.Logging
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.agentuserclientdetails.connectors.EmailConnector
import uk.gov.hmrc.agentuserclientdetails.model.{EmailInformation, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.{FriendlyNameJobData, JobMonitoringRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.workitem.{Failed, PermanentlyFailed, ToDo, WorkItem}

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class JobMonitoringWorker @Inject() (
  jobMonitoring: JobMonitoringRepository,
  friendlyNameWorkItemService: FriendlyNameWorkItemService,
  emailConnector: EmailConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  val running = new AtomicBoolean(false)

  def continue: Boolean = running.get()

  def cancel(): Unit = running.set(false)

  def start(): Future[Unit] =
    if (running.get()) {
      logger.debug("Job completion monitor triggered but was already running.")
      Future.successful(())
    } else {
      logger.debug("Job completion monitor triggered. Starting...")
      running.set(true)
      val result = jobMonitoring.getUnfinishedFriendlyNameFetchJobData.flatMap { jobDatas =>
        logger.debug(s"[Job monitor] ${jobDatas.length} jobs to check for completion. Starting...")
        val workItems = Enumerator.enumerate(jobDatas)
        val processWorkItems: Iteratee[FriendlyNameJobData, Unit] = Iteratee.foldM(()) { case ((), item) =>
          processItem(item)
        }
        workItems.run(processWorkItems)
      }

      result.onComplete { _ =>
        logger.debug("Job completion monitor finished.")
        running.set(false)
      }
      result
    }

  /*
   Main logic
   */
  def processItem(workItem: FriendlyNameJobData): Future[Unit] =
    workItem match {
      case job: FriendlyNameJobData =>
        if (job.finishTime.nonEmpty) // Already finished? This should not happen but just in case we handle it.
          Future.successful(())
        else {
          hasCompleted(workItem).flatMap {
            case false =>
              logger.info(s"Job monitor: Job ${job._id} has not yet finished.")
              Future.successful(()) // Job not yet completed, do nothing.
            case true =>
              logger.info(s"Job monitor: Job ${job._id} has finished.")
              for {
                _ <- job._id.fold[Future[Option[UpdateResult]]](Future.successful(None))(
                       jobMonitoring
                         .markAsFinishedFriendlyNameFetchJobData(_, finishTime = LocalDateTime.now)
                         .map(Option(_))
                     )
                _ <- if (job.sendEmailOnCompletion) {
                       failuresFor(job).flatMap { failures =>
                         val emailTemplateName =
                           (if (failures.isEmpty) "agent_permissions_success"
                            else "agent_permissions_some_failed") + (if (job.emailLanguagePreference.contains("cy"))
                                                                       "_cy"
                                                                     else "")
                         logger.info(
                           s"Sending email $emailTemplateName to ${job.email.getOrElse("")} for agent ${job.agencyName}"
                         )
                         implicit val hc: HeaderCarrier = HeaderCarrier()
                         emailConnector.sendEmail(
                           EmailInformation(
                             job.email.toSeq,
                             emailTemplateName,
                             Map("agencyName" -> job.agencyName.getOrElse(""))
                           )
                         )
                       }
                     } else Future.successful(false)
              } yield ()
          }
        }
    }

  def hasCompleted(jobData: FriendlyNameJobData): Future[Boolean] = for {
    outstandingItems: Seq[WorkItem[FriendlyNameWorkItem]] <-
      friendlyNameWorkItemService.query(jobData.groupId, status = Some(Seq(Failed, ToDo)))
    outstandingEnrolmentKeys = outstandingItems.map(_.item.client.enrolmentKey)
    isCompleted = outstandingEnrolmentKeys.toSet.intersect(jobData.enrolmentKeys.toSet).isEmpty
  } yield isCompleted

  def failuresFor(jobData: FriendlyNameJobData): Future[Set[String]] = for {
    permanentlyFailedItems: Seq[WorkItem[FriendlyNameWorkItem]] <-
      friendlyNameWorkItemService.query(jobData.groupId, status = Some(Seq(PermanentlyFailed)))
    permanentlyFailedEnrolmentKeys = permanentlyFailedItems.map(_.item.client.enrolmentKey)
    failures = permanentlyFailedEnrolmentKeys.toSet.intersect(jobData.enrolmentKeys.toSet)
  } yield failures
}
