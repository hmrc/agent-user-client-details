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

package uk.gov.hmrc.agentuserclientdetails.services

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import play.api.Logging
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.Assign
import uk.gov.hmrc.agentuserclientdetails.model.AssignmentWorkItem
import uk.gov.hmrc.agentuserclientdetails.model.Unassign
import uk.gov.hmrc.agentuserclientdetails.util.StatusUtil
import uk.gov.hmrc.clusterworkthrottling.Rate
import uk.gov.hmrc.clusterworkthrottling.ServiceInstances
import uk.gov.hmrc.clusterworkthrottling.ThrottledWorkItemProcessor
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class AssignmentsWorker @Inject() (
  workItemService: AssignmentsWorkItemService,
  esConnector: EnrolmentStoreProxyConnector,
  serviceInstances: ServiceInstances,
  actorSystem: ActorSystem,
  appConfig: AppConfig,
  mat: Materializer
)(implicit ec: ExecutionContext)
extends Logging {

  lazy val assignmentsThrottler: ThrottledWorkItemProcessor =
    new ThrottledWorkItemProcessor(
      "es11-es12-assign-unassign-enrolment",
      actorSystem,
      rateLimit = Some(Rate.parse(appConfig.assignmentsThrottlingRate))
    ) {
      def instanceCount: Int =
        Option(serviceInstances).fold(1)(
          _.instanceCount
        ) // must handle serviceInstances == null case (can happen in testing)
    }

  private val running = new AtomicBoolean(false)

  def isRunning: Boolean = running.get()

  def cancel(): Unit = running.set(false)

  def start(): Future[Unit] =
    running.get() match {
      case true =>
        logger.debug("Assignments processing triggered but was already running.")
        Future.successful(())
      case false =>
        logger.debug("Assignments processing triggered. Starting...")
        running.set(true)

        val workItems: Source[WorkItem[AssignmentWorkItem], NotUsed] = Source.unfoldAsync(())(_ => pullWorkItemWhile(isRunning).map(_.map(() -> _)))
        val processWorkItems: Sink[WorkItem[AssignmentWorkItem], Future[Unit]] = Sink.foldAsync(()) { case ((), item) => processItem(item) }
        val result: Future[Unit] = workItems.runWith(processWorkItems)(mat)
        result.onComplete { _ =>
          logger.debug("Assignments processing finished.")
          running.set(false)
        }
        result
    }

  def pullWorkItemWhile(
    continue: => Boolean
  )(implicit ec: ExecutionContext): Future[Option[WorkItem[AssignmentWorkItem]]] =
    if (continue) {
      workItemService.pullOutstanding(
        failedBefore = Instant.now().minusSeconds(appConfig.assignEnrolmentWorkItemRepoFailedBeforeSeconds),
        availableBefore = Instant.now().minusSeconds(appConfig.assignEnrolmentWorkItemRepoAvailableBeforeSeconds)
      )
    }
    else {
      Future.successful(None)
    }

  /*
   Main logic
   */
  def processItem(workItem: WorkItem[AssignmentWorkItem]): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier().copy(sessionId = workItem.item.sessionId.map(SessionId.apply))
    val userId = workItem.item.userId
    val enrolmentKey = workItem.item.enrolmentKey
    val endpoint =
      workItem.item.operation match {
        case Assign => "ES11"
        case Unassign => "ES12"
      }

    throttledAssignEnrolment(workItem.item).transformWith {
      case Success(_) =>
        logger.debug(s"Successful $endpoint call (userId: $userId, enrolmentKey: $enrolmentKey).")
        workItemService.complete(workItem.id, Succeeded).map(_ => ())
      case Failure(e) if StatusUtil.isRetryable(e) =>
        if (giveUp(workItem)) {
          logger.error(
            s"Retryable failure for $endpoint call (userId: $userId, enrolmentKey: $enrolmentKey). Reason: ${logFriendlyMessage(e)}. Giving up."
          )
          workItemService.complete(workItem.id, PermanentlyFailed).map(_ => ())
        }
        else {
          logger.warn(
            s"Retryable failure for $endpoint call (userId: $userId, enrolmentKey: $enrolmentKey). Reason: ${logFriendlyMessage(e)}. Will retry."
          )
          workItemService.complete(workItem.id, Failed).map(_ => ())
        }
      case Failure(e) => // non-retryable failure
        logger.error(
          s"Permanent failure for $endpoint call (userId: $userId, enrolmentKey: $enrolmentKey). Reason: ${logFriendlyMessage(e)}."
        )
        workItemService.complete(workItem.id, PermanentlyFailed).map(_ => ())
    }
  }

  // Determine whether we should give up trying to process this work item if it fails again.
  protected def giveUp(
    wi: WorkItem[AssignmentWorkItem]
  ): Boolean = wi.receivedAt.isBefore(Instant.now().minusSeconds(60 * appConfig.assignEnrolmentWorkItemRepoGiveUpAfterMinutes))

  private def logFriendlyMessage(e: Throwable): String =
    e match {
      case uer: UpstreamErrorResponse => s"Upstream status ${uer.statusCode}: ${uer.message}"
      case e => e.getMessage
    }

  private[services] def throttledAssignEnrolment(awi: AssignmentWorkItem)(implicit
    hc: HeaderCarrier
  ): Future[Unit] = {
    // return a Future[Option[Throwable]] instead of a failed future because the throttler library
    // doesn't seem to throttle failed futures correctly.
    def f(): Future[Option[Throwable]] = {
      val (endpoint, result) =
        awi.operation match {
          case Assign => ("ES11", esConnector.assignEnrolment(awi.userId, awi.enrolmentKey))
          case Unassign => ("ES12", esConnector.unassignEnrolment(awi.userId, awi.enrolmentKey))
        }
      result.map(_ => None).recover {
        case e @ UpstreamErrorResponse(_, status, _, _) =>
          logger.info(s"$status status received from $endpoint.")
          Some(e)
        case e =>
          logger.info(s"Error received when calling $endpoint: $e")
          Some(e)
      }
    }
    val result =
      if (appConfig.enableThrottling)
        assignmentsThrottler.throttledStartingFrom(Instant.now())(f())
      else
        f()
    result.flatMap(_.fold(Future.successful(()))(Future.failed))
  }

}
