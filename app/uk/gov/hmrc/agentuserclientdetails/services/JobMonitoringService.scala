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
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.SingleObservableFuture
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameJobData
import uk.gov.hmrc.agentuserclientdetails.model.JobData
import uk.gov.hmrc.agentuserclientdetails.repositories.JobMonitoringRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[JobMonitoringServiceImpl])
trait JobMonitoringService {

  /*
   This has the side-effect of putting the job to be checked into a 'check in progress' state.
   Please remember to mark it as finished or not finished before calling this method again.
   */
  def getNextJobToCheck(implicit ec: ExecutionContext): Future[Option[WorkItem[JobData]]]
  def createFriendlyNameFetchJobData(jobData: FriendlyNameJobData)(implicit ec: ExecutionContext): Future[ObjectId]
  def markAsFinished(objectId: ObjectId)(implicit ec: ExecutionContext): Future[Unit]
  def markAsNotFinished(objectId: ObjectId)(implicit ec: ExecutionContext): Future[Unit]
  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult]

}

@Singleton
class JobMonitoringServiceImpl @Inject() (
  jobMonitoringRepository: JobMonitoringRepository,
  appConfig: AppConfig
)
extends JobMonitoringService {

  def getNextJobToCheck(implicit ec: ExecutionContext): Future[Option[WorkItem[JobData]]] = jobMonitoringRepository.pullOutstanding(
    Instant.now().minusSeconds(appConfig.jobMonitoringFailedBeforeSeconds),
    Instant.now().minusSeconds(appConfig.jobMonitoringAvailableBeforeSeconds)
  )

  def createFriendlyNameFetchJobData(
    jobData: FriendlyNameJobData
  )(implicit ec: ExecutionContext): Future[ObjectId] = jobMonitoringRepository
    .pushNew(jobData)
    .map(_.id)

  def markAsFinished(objectId: ObjectId)(implicit ec: ExecutionContext): Future[Unit] = jobMonitoringRepository.markAs(objectId, Succeeded).map(_ => ())

  def markAsNotFinished(objectId: ObjectId)(implicit ec: ExecutionContext): Future[Unit] = jobMonitoringRepository.markAs(objectId, Failed).map(_ => ())

  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult] = {
    val cutoff: Instant = now.minusSeconds(appConfig.jobMonitoringDeleteFinishedItemsAfterSeconds)
    jobMonitoringRepository.collection
      .deleteMany(
        Filters.and(
          Filters.in(
            "status",
            Succeeded.name,
            Duplicate.name,
            PermanentlyFailed.name
          ),
          Filters.lte("updatedAt", cutoff)
        )
      )
      .toFuture()
  }

}
