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

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Client
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.repositories.{Es3Cache, Es3CacheRepository}
import uk.gov.hmrc.http.HeaderCarrier

import java.net.URLDecoder
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[Es3CacheManagerImpl])
trait Es3CacheManager {

  def getCachedClients(
    groupId: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Seq[Client]]

  def cacheRefresh(
    groupId: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Option[Unit]]

}

@Singleton
class Es3CacheManagerImpl @Inject() (
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  es3CacheRepository: Es3CacheRepository
) extends Es3CacheManager with Logging {

  override def getCachedClients(
    groupId: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Seq[Client]] = {

    def enrolmentsToClients(es3Cache: Es3Cache) =
      es3Cache.clients
        .map(Client.fromEnrolment)
        .map(client => client.copy(friendlyName = URLDecoder.decode(client.friendlyName, "UTF-8")))

    es3CacheRepository.fetch(groupId).flatMap {
      case None =>
        fetchEs3ClientsAndPersist(groupId) flatMap { _ =>
          es3CacheRepository.fetch(groupId)
        }
      case Some(es3Cache) =>
        Future.successful(Option(es3Cache))
    } map {
      case None =>
        Seq.empty[Client]
      case Some(es3Cache) =>
        enrolmentsToClients(es3Cache)
    }
  }

  override def cacheRefresh(
    groupId: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Option[Unit]] =
    es3CacheRepository
      .fetch(groupId)
      .map(_.map(_ => fetchEs3ClientsAndPersist(groupId)))

  private def fetchEs3ClientsAndPersist(groupId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    for {
      startedAt     <- Future successful System.currentTimeMillis()
      es3Enrolments <- enrolmentStoreProxyConnector.getEnrolmentsForGroupId(groupId)
      _             <- es3CacheRepository.save(groupId, es3Enrolments)
    } yield logger.info(s"Refreshed ES3 cache for $groupId in ${System.currentTimeMillis() - startedAt} millis")
}
