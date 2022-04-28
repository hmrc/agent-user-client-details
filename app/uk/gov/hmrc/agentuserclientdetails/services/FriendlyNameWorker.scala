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

import java.util.concurrent.atomic.AtomicBoolean
import org.joda.time.DateTime
import play.api.Logging
import play.api.libs.iteratee.{Enumerator, Iteratee}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.agentmtdidentifiers.model.Service
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.clusterworkthrottling.{Rate, ServiceInstances, ThrottledWorkItemProcessor, WorkThrottling}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.workitem._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FriendlyNameWorker @Inject()(
                                       workItemRepository: FriendlyNameWorkItemRepository,
                                       esConnector: EnrolmentStoreProxyConnector,
                                       serviceInstances: ServiceInstances,
                                       clientNameService: ClientNameService,
                                       actorSystem: ActorSystem,
                                       appConfig: AppConfig)(implicit ec: ExecutionContext)
  extends Logging {

  val clientNameThrottler: ThrottledWorkItemProcessor =
    new ThrottledWorkItemProcessor("client-name-fetching", actorSystem, rateLimit = Some(Rate.parse(appConfig.clientNameFetchThrottlingRate))) {
      def instanceCount: Int = serviceInstances.instanceCount
    }

  val es19Throttler: ThrottledWorkItemProcessor =
    new ThrottledWorkItemProcessor("es19-update-friendly-name", actorSystem, rateLimit = Some(Rate.parse(appConfig.es19ThrottlingRate))) {
      def instanceCount: Int = serviceInstances.instanceCount
    }

  val running = new AtomicBoolean(false)

  def continue: Boolean = running.get()

  def cancel(): Unit = running.set(false)

  def start(implicit hc: HeaderCarrier): Future[Unit] = {
    running.get() match {
      case true =>
        logger.info("Work processing triggered but was already running.")
        Future.successful(())
      case false =>
        logger.info("Work processing triggered. Starting...")
        running.set(true)
        val workItems: Enumerator[WorkItem[FriendlyNameWorkItem]] = Enumerator.generateM(pullWorkItemWhile(continue))
        val processWorkItems: Iteratee[WorkItem[FriendlyNameWorkItem], Unit] = Iteratee.foldM(()) { case ((), item) => processItem(item) }
        val result = workItems.run(processWorkItems)
        result.onComplete {
          case _ => running.set(false)
        }
        result
    }
  }

  def removeAll(implicit hc: HeaderCarrier): Future[WriteResult] =
    workItemRepository.removeAll()

  def pullWorkItemWhile(continue: => Boolean)(
    implicit ec: ExecutionContext): Future[Option[WorkItem[FriendlyNameWorkItem]]] =
    if (continue) {
      workItemRepository.pullOutstanding(failedBefore = DateTime.now().minusMinutes(1), availableBefore = DateTime.now()) // TODO configure these time parameters
    } else {
      Future.successful(None)
    }

  /*
   Main logic
   */
  def processItem(workItem: WorkItem[FriendlyNameWorkItem])(implicit hc: HeaderCarrier): Future[Unit] = {
    val groupId = workItem.item.groupId
    val enrolmentKey = workItem.item.enrolment.service
    val clientId = workItem.item.enrolment.identifiers.head.value
    // TODO handle case when identifier is empty and/or there is more than one identifier
    // TODO handle case where the service ID provided is not valid.

    workItem match {
      case wi if wi.item.enrolment.friendlyName.nonEmpty =>
        // There is already a friendlyName populated. All we need to do is store the enrolment.
        throttledUpdateFriendlyName(groupId, enrolmentKey, wi.item.enrolment.friendlyName).transformWith {
          case Success(_) =>
            logger.info("Previously fetched friendly name updated via ES19 successfully.")
            workItemRepository.complete(workItem.id, Succeeded).map(_ => ())
          case Failure(_) =>
            logger.info("Previously fetched friendly name update via ES19 failed. This will be retried.")
            workItemRepository.complete(workItem.id, Failed).map(_ => ())
        }
      case wi if wi.item.enrolment.friendlyName.isEmpty =>
        // The friendlyName is not populated; we need to fetch it, insert it in the enrolment and store the enrolment.
        throttledFetchFriendlyName(clientId, enrolmentKey).transformWith {
          case Failure(e) =>
            logger.info(s"Fetch of friendly name for enrolment failed. Reason: ${e.getMessage}. This will be retried.")
            workItemRepository.complete(workItem.id, Failed).map(_ => ())
          case Success(None) =>
            // A successful call returning None means the lookup succeeded but there is no name available.
            // There is no point in retrying; we set the status as permanently failed.
            logger.info("No friendly name is available: marking enrolment as permanently failed.")
            workItemRepository.complete(workItem.id, PermanentlyFailed).map(_ => ())
          case Success(Some(friendlyName)) =>
            throttledUpdateFriendlyName(groupId, enrolmentKey, wi.item.enrolment.friendlyName).transformWith {
              case Success(_) =>
                logger.info("Friendly name retrieved and updated via ES19")
                workItemRepository.complete(workItem.id, Succeeded).map(_ => ())
              case Failure(_) => for {
                // push a new work item with the friendly name already populated so the name lookup doesn't have to be done again
                _ <- workItemRepository.pushNew(wi.item.copy(enrolment = wi.item.enrolment.copy(friendlyName = friendlyName)), DateTime.now())
                // mark the old work item as duplicate
                _ <- workItemRepository.complete(workItem.id, Duplicate)
                _ = logger.info("Friendly name retrieved but could not be updated via ES19: scheduling retry")
              } yield ()
            }
        }
    }
  }

  private[services] def throttledFetchFriendlyName(clientId: String, service: String)(
    implicit hc: HeaderCarrier): Future[Option[String]] = {
    clientNameThrottler.throttledStartingFrom(DateTime.now()) {
      clientNameService.getClientNameByService(clientId, Service.forId(service))
    }
  }

  private[services] def throttledUpdateFriendlyName(groupId: String, enrolmentKey: String, friendlyName: String)(
    implicit hc: HeaderCarrier): Future[Unit] = {
    es19Throttler.throttledStartingFrom(DateTime.now()) {
      esConnector.updateEnrolmentFriendlyName(groupId, enrolmentKey, friendlyName).recover {
        case e@UpstreamErrorResponse(_, status, _, _) =>
          logger.info(s"$status status received from ES19.")
          throw e
        case e =>
          logger.info(s"Error received when calling ES19: $e")
          throw e
      }
    }
  }
}
