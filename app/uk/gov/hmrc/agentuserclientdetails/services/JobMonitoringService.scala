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

import com.google.inject.ImplementedBy
import org.joda.time.{DateTime => JodaDateTime}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.{FriendlyNameJobData, JobData}
import uk.gov.hmrc.agentuserclientdetails.repositories.JobMonitoringRepository
import uk.gov.hmrc.workitem.{Failed, Succeeded, WorkItem}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JobMonitoringServiceImpl])
trait JobMonitoringService {
  def getNextJobToCheck(implicit ec: ExecutionContext): Future[Option[WorkItem[JobData]]]
  def createFriendlyNameFetchJobData(jobData: FriendlyNameJobData)(implicit ec: ExecutionContext): Future[BSONObjectID]
  def markAsFinished(objectId: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit]
  def markAsNotFinished(objectId: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit]
}

@Singleton
class JobMonitoringServiceImpl @Inject() (jobMonitoringRepository: JobMonitoringRepository, appConfig: AppConfig)
    extends JobMonitoringService {
  def getNextJobToCheck(implicit ec: ExecutionContext): Future[Option[WorkItem[JobData]]] =
    jobMonitoringRepository.pullOutstanding(
      JodaDateTime.now().minusSeconds(appConfig.jobMonitoringFailedBeforeSeconds),
      JodaDateTime.now().minusSeconds(appConfig.jobMonitoringAvailableBeforeSeconds)
    )

  def createFriendlyNameFetchJobData(
    jobData: FriendlyNameJobData
  )(implicit ec: ExecutionContext): Future[BSONObjectID] =
    jobMonitoringRepository
      .pushNew(jobData, receivedAt = JodaDateTime.now())
      .map(_.id)

  def markAsFinished(objectId: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit] =
    jobMonitoringRepository.markAs(objectId, Succeeded).map(_ => ())

  def markAsNotFinished(objectId: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit] =
    jobMonitoringRepository.markAs(objectId, Failed).map(_ => ())

}
