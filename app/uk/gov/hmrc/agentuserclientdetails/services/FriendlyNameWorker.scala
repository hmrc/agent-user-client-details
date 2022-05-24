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

import akka.actor.ActorSystem
import org.joda.time.DateTime
import play.api.Logging
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.util.{EnrolmentKey, StatusUtil}
import uk.gov.hmrc.clusterworkthrottling.{Rate, ServiceInstances, ThrottledWorkItemProcessor}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId, UpstreamErrorResponse}
import uk.gov.hmrc.workitem._

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FriendlyNameWorker @Inject()(
                                       workItemService: WorkItemService,
                                       esConnector: EnrolmentStoreProxyConnector,
                                       serviceInstances: ServiceInstances,
                                       clientNameService: ClientNameService,
                                       actorSystem: ActorSystem,
                                       appConfig: AppConfig)(implicit ec: ExecutionContext)
  extends Logging {

  lazy val clientNameThrottler: ThrottledWorkItemProcessor =
    new ThrottledWorkItemProcessor("client-name-fetching", actorSystem, rateLimit = Some(Rate.parse(appConfig.clientNameFetchThrottlingRate))) {
      def instanceCount: Int = Option(serviceInstances).fold(1)(_.instanceCount) // must handle serviceInstances == null case (can happen in testing)
    }

  lazy val es19Throttler: ThrottledWorkItemProcessor =
    new ThrottledWorkItemProcessor("es19-update-friendly-name", actorSystem, rateLimit = Some(Rate.parse(appConfig.es19ThrottlingRate))) {
      def instanceCount: Int = Option(serviceInstances).fold(1)(_.instanceCount) // must handle serviceInstances == null case (can happen in testing)
    }

  val running = new AtomicBoolean(false)

  def continue: Boolean = running.get()

  def cancel(): Unit = running.set(false)

  def start(): Future[Unit] = {
    running.get() match {
      case true =>
        logger.debug("Work processing triggered but was already running.")
        Future.successful(())
      case false =>
        logger.debug("Work processing triggered. Starting...")
        running.set(true)
        val workItems: Enumerator[WorkItem[FriendlyNameWorkItem]] = Enumerator.generateM(pullWorkItemWhile(continue))
        val processWorkItems: Iteratee[WorkItem[FriendlyNameWorkItem], Unit] = Iteratee.foldM(()) { case ((), item) => processItem(item) }
        val result = workItems.run(processWorkItems)
        result.onComplete {
          case _ =>
            logger.debug("Work processing finished.")
            running.set(false)
        }
        result
    }
  }

  def pullWorkItemWhile(continue: => Boolean)(
    implicit ec: ExecutionContext): Future[Option[WorkItem[FriendlyNameWorkItem]]] =
    if (continue) {
      workItemService.pullOutstanding(
        failedBefore = DateTime.now().minusSeconds(appConfig.workItemRepoFailedBeforeSeconds),
        availableBefore = DateTime.now().minusSeconds(appConfig.workItemRepoAvailableBeforeSeconds)
      )
    } else {
      Future.successful(None)
    }

  /*
   Main logic
   */
  def processItem(workItem: WorkItem[FriendlyNameWorkItem]): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier().copy(sessionId = workItem.item.sessionId.map(SessionId))
    val groupId = workItem.item.groupId
    val enrolmentKey = workItem.item.client.enrolmentKey
    // TODO handle case when identifier is empty and/or there is more than one identifier
    // TODO handle case where the service ID provided is not valid.

    workItem match {
      case wi if wi.item.client.friendlyName.nonEmpty =>
        // There is already a friendlyName populated. All we need to do is store the enrolment.
        throttledUpdateFriendlyName(groupId, enrolmentKey, wi.item.client.friendlyName).transformWith {
          case Success(_) =>
            logger.info(s"Previously fetched friendly name for ${enrolmentKey} updated via ES19 successfully.")
            workItemService.complete(workItem.id, Succeeded).map(_ => ())
          case Failure(_) =>
            logger.info(s"Previously fetched friendly name for ${enrolmentKey} could not be updated via ES19. This will be retried.")
            workItemService.complete(workItem.id, Failed).map(_ => ())
        }
      case wi if wi.item.client.friendlyName.isEmpty =>
        // The friendlyName is not populated; we need to fetch it, insert it in the enrolment and store the enrolment.
        throttledFetchFriendlyName(enrolmentKey).transformWith {
          case Success(None) =>
            // A successful call returning None means the lookup succeeded but there is no name available.
            // There is no point in retrying; we set the status as permanently failed.
            logger.info(s"No friendly name is available for ${enrolmentKey}: marking enrolment as permanently failed.")
            workItemService.complete(workItem.id, PermanentlyFailed).map(_ => ())
          case Success(Some(friendlyName)) =>
            throttledUpdateFriendlyName(groupId, enrolmentKey, friendlyName).transformWith {
              case Success(_) =>
                logger.info(s"Friendly name for ${enrolmentKey} retrieved and updated via ES19.")
                workItemService.complete(workItem.id, Succeeded).map(_ => ())
              case Failure(_) => for {
                // push a new work item with the friendly name already populated so the name lookup doesn't have to be done again
                _ <- workItemService.pushNew(Seq(wi.item.copy(client = wi.item.client.copy(friendlyName = friendlyName))), DateTime.now(), ToDo)
                // mark the old work item as duplicate
                _ <- workItemService.complete(workItem.id, Duplicate)
                _ = logger.info(s"Friendly name for ${enrolmentKey} retrieved but could not be updated via ES19: scheduling retry")
              } yield ()
            }
          case Failure(e) if StatusUtil.isRetryable(e) =>
            if (giveUp(wi)) {
              logger.info(s"Fetch of friendly name failed for ${enrolmentKey}. Reason: ${e.getMessage}. Giving up.")
              workItemService.complete(workItem.id, PermanentlyFailed).map(_ => ())
            } else {
              logger.info(s"Fetch of friendly name failed for ${enrolmentKey}. Reason: ${e.getMessage}. This will be retried.")
              workItemService.complete(workItem.id, Failed).map(_ => ())
            }
          case Failure(e) => // non-retryable failure
            logger.info(s"Fetch of friendly name permanently failed for ${enrolmentKey}. Reason: ${e.getMessage}.")
            workItemService.complete(workItem.id, PermanentlyFailed).map(_ => ())
        }
    }
  }

  // Determine whether we should give up trying to process this work item if it fails again.
  protected def giveUp(wi: WorkItem[FriendlyNameWorkItem]): Boolean = {
    wi.receivedAt.isBefore(DateTime.now().minusMinutes(appConfig.workItemRepoGiveUpAfterMinutes))
  }

  private[services] def throttledFetchFriendlyName(enrolmentKey: String)(
    implicit hc: HeaderCarrier): Future[Option[String]] = {
    def f(): Future[Option[String]] = {
      val (service, clientId) = EnrolmentKey.deconstruct(enrolmentKey)
      clientNameService.getClientNameByService(clientId, service)
    }
    if (appConfig.enableThrottling) clientNameThrottler.throttledStartingFrom(DateTime.now())(f())
    else f()
  }

  private[services] def throttledUpdateFriendlyName(groupId: String, enrolmentKey: String, friendlyName: String)(
    implicit hc: HeaderCarrier): Future[Unit] = {
    def f(): Future[Unit] =
      esConnector.updateEnrolmentFriendlyName(groupId, enrolmentKey, friendlyName).recover {
        case e@UpstreamErrorResponse(_, status, _, _) =>
          logger.info(s"$status status received from ES19.")
          throw e

        case e =>
          logger.info(s"Error received when calling ES19: $e")
          throw e
      }
    if (appConfig.enableThrottling) es19Throttler.throttledStartingFrom(DateTime.now())(f())
    else f()
  }
}
