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
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Duplicate, Succeeded}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, ResultStatus, WorkItem}

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FriendlyNameWorkItemServiceImpl])
trait FriendlyNameWorkItemService {

  /** Query by groupId and optionally by status (leave status as None to include all statuses)
    */
  def query(groupId: String, status: Option[Seq[ProcessingStatus]])(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[FriendlyNameWorkItem]]]

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult]

  /** Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
    */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]]

  def pushNew(items: Seq[FriendlyNameWorkItem], receivedAt: Instant, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit]

  def complete(id: ObjectId, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean]

  def removeAll()(implicit ec: ExecutionContext): Future[DeleteResult]

  def removeByGroupId(groupId: String)(implicit ec: ExecutionContext): Future[DeleteResult]

  def pullOutstanding(failedBefore: Instant, availableBefore: Instant)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[FriendlyNameWorkItem]]]
}

class FriendlyNameWorkItemServiceImpl @Inject() (workItemRepo: FriendlyNameWorkItemRepository, appConfig: AppConfig)
    extends FriendlyNameWorkItemService {

  /** Query by groupId and optionally by status (leave status as None to include all statuses)
    */
  def query(groupId: String, status: Option[Seq[ProcessingStatus]])(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[FriendlyNameWorkItem]]] = {
    val selector = status match {
      case Some(statuses) =>
        Filters.and(Filters.equal("item.groupId", groupId), Filters.in("status", statuses.map(_.name): _*))
      case None => Filters.equal("item.groupId", groupId)
    }
    workItemRepo.collection.find[WorkItem[FriendlyNameWorkItem]](selector).toFuture()
  }

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup(now: Instant)(implicit ec: ExecutionContext): Future[DeleteResult] = {
    val cutoff: Instant =
      now.minusSeconds(appConfig.friendlyNameWorkItemRepoDeleteFinishedItemsAfterSeconds)
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
      .collect
      .toFuture
      .map { xs: Seq[BsonValue] =>
        val elems = xs.map { x =>
          val document = x.asDocument()
          (document.getString("_id").getValue -> document.getNumber("count").intValue())
        }
        Map(elems: _*)
      }

  def pushNew(items: Seq[FriendlyNameWorkItem], receivedAt: Instant, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    if (items.nonEmpty)
      workItemRepo.pushNewBatch(items, receivedAt, _ => initialState).map(_ => ())
    else Future.successful(())

  def complete(id: ObjectId, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean] =
    workItemRepo.complete(id, newStatus)

  def removeAll()(implicit ec: ExecutionContext): Future[DeleteResult] =
    workItemRepo.collection.deleteMany(Filters.empty()).toFuture()

  def removeByGroupId(groupId: String)(implicit ec: ExecutionContext): Future[DeleteResult] =
    workItemRepo.collection.deleteMany(Filters.equal("item.groupId", groupId)).toFuture()

  def pullOutstanding(failedBefore: Instant, availableBefore: Instant)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[FriendlyNameWorkItem]]] =
    workItemRepo.pullOutstanding(failedBefore, availableBefore)
}
