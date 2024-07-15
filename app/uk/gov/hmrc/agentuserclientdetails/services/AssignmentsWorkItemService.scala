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
import org.mongodb.scala.bson.{BsonValue, ObjectId}
import org.mongodb.scala.model.{Accumulators, Aggregates, Filters}
import org.mongodb.scala.result.DeleteResult
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.AssignmentWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.AssignmentsWorkItemRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Duplicate, Succeeded}
import uk.gov.hmrc.mongo.workitem._

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssignmentsWorkItemServiceImpl])
trait AssignmentsWorkItemService {

  /** Query by status
    */
  def query(status: Seq[ProcessingStatus])(implicit ec: ExecutionContext): Future[Seq[WorkItem[AssignmentWorkItem]]]

  /** Query by ARN
    */
  def queryBy(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[WorkItem[AssignmentWorkItem]]]

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult]

  /** Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
    */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]]

  def pushNew(items: Seq[AssignmentWorkItem], receivedAt: Instant, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit]

  def complete(id: ObjectId, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean]

  def removeAll(): Future[DeleteResult]

  def pullOutstanding(failedBefore: Instant, availableBefore: Instant)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[AssignmentWorkItem]]]
}

class AssignmentsWorkItemServiceImpl @Inject() (workItemRepo: AssignmentsWorkItemRepository, appConfig: AppConfig)
    extends AssignmentsWorkItemService {

  /** Query by status
    */
  def query(status: Seq[ProcessingStatus])(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[AssignmentWorkItem]]] =
    workItemRepo.collection
      .find[WorkItem[AssignmentWorkItem]](Filters.in("status", status.map(_.name): _*))
      .collect()
      .toFuture()

  override def queryBy(arn: Arn)(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[AssignmentWorkItem]]] =
    workItemRepo.collection
      .find[WorkItem[AssignmentWorkItem]](Filters.equal("item.arn", arn.value))
      .collect()
      .toFuture()

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult] = {
    val cutoff: Instant =
      now.minusSeconds(appConfig.assignEnrolmentWorkItemRepoDeleteFinishedItemsAfterSeconds)
    workItemRepo.collection
      .deleteMany(
        Filters.and(Filters.in("status", Succeeded.name, Duplicate.name), Filters.lte("updatedAt", cutoff))
      )
      .toFuture()
  }

  /** Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
    */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]] =
    workItemRepo.collection
      .aggregate[BsonValue](Seq(Aggregates.group("$status", Accumulators.sum("count", 1))))
      .collect()
      .toFuture()
      .map { xs: Seq[BsonValue] =>
        val elems = xs.map { x =>
          val document = x.asDocument()
          (document.getString("_id").getValue -> document.getNumber("count").intValue())
        }
        Map(elems: _*)
      }

  def pushNew(items: Seq[AssignmentWorkItem], receivedAt: Instant, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    if (items.nonEmpty)
      workItemRepo.pushNewBatch(items, receivedAt, _ => initialState).map(_ => ())
    else
      Future.successful(())

  def complete(id: ObjectId, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean] =
    workItemRepo.complete(id, newStatus)

  def removeAll(): Future[DeleteResult] =
    workItemRepo.collection.deleteMany(Filters.empty()).toFuture()

  def pullOutstanding(failedBefore: Instant, availableBefore: Instant)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[AssignmentWorkItem]]] =
    workItemRepo.pullOutstanding(failedBefore, availableBefore)
}
