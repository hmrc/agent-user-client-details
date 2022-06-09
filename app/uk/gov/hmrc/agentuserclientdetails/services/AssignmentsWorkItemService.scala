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
import org.joda.time.DateTime
import uk.gov.hmrc.agentuserclientdetails.model.AssignmentWorkItem
import play.api.libs.json.{JsArray, JsDefined, JsNumber, JsObject, JsString, Json}
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.repositories.AssignmentsWorkItemRepository
import uk.gov.hmrc.workitem.{Duplicate, ProcessingStatus, ResultStatus, Succeeded, WorkItem}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssignmentsWorkItemServiceImpl])
trait AssignmentsWorkItemService {

  /** Query by status
    */
  def query(status: Seq[ProcessingStatus], limit: Int = -1)(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[AssignmentWorkItem]]]

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup()(implicit ec: ExecutionContext): Future[WriteResult]

  /** Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
    */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]]

  def pushNew(items: Seq[AssignmentWorkItem], receivedAt: DateTime, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit]

  def complete(id: BSONObjectID, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean]

  def removeAll()(implicit ec: ExecutionContext): Future[WriteResult]

  def pullOutstanding(failedBefore: DateTime, availableBefore: DateTime)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[AssignmentWorkItem]]]
}

class AssignmentsWorkItemServiceImpl @Inject() (workItemRepo: AssignmentsWorkItemRepository)
    extends AssignmentsWorkItemService {

  /** Query by status
    */
  def query(status: Seq[ProcessingStatus], limit: Int = -1)(implicit
    ec: ExecutionContext
  ): Future[Seq[WorkItem[AssignmentWorkItem]]] = {
    import workItemRepo._
    val selector = Json.obj("status" -> Json.obj("$in" -> JsArray(status.map(s => JsString(s.name)))))
    workItemRepo.collection
      .find(selector, projection = None)
      .cursor[WorkItem[AssignmentWorkItem]]()
      .collect[Seq](limit, Cursor.FailOnError())
  }

  /** Removes any items that have been marked as successful or duplicated.
    */
  def cleanup()(implicit ec: ExecutionContext): Future[WriteResult] =
    workItemRepo.remove(
      "status" -> Json.obj("$in" -> JsArray(Seq(JsString(Succeeded.name), JsString(Duplicate.name))))
    )

  /** Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
    */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    import workItemRepo.collection.BatchCommands.AggregationFramework.{Group, GroupFunction, SumAll}
    workItemRepo.collection
      .aggregateWith[JsObject]()(_ => (Group(JsString("$status"))("count" -> (SumAll: GroupFunction)), List.empty))
      .collect[Seq](-1, Cursor.FailOnError())
      .map { resultsJs =>
        // TODO is there a neater way to write the parsing logic below?
        val elems = resultsJs
          .map(jso => (jso \ "_id") -> (jso \ "count"))
          .map {
            case (JsDefined(JsString(status)), JsDefined(JsNumber(count))) => status -> count.toInt
            case _ => throw new RuntimeException("Malformed repository stats encountered.")
          }
        Map(elems: _*)
      }
  }

  def pushNew(items: Seq[AssignmentWorkItem], receivedAt: DateTime, initialState: ProcessingStatus)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    workItemRepo.pushNew(items, receivedAt, _ => initialState).map(_ => ())

  def complete(id: BSONObjectID, newStatus: ProcessingStatus with ResultStatus)(implicit
    ec: ExecutionContext
  ): Future[Boolean] =
    workItemRepo.complete(id, newStatus)

  def removeAll()(implicit ec: ExecutionContext): Future[WriteResult] = workItemRepo.removeAll()

  def pullOutstanding(failedBefore: DateTime, availableBefore: DateTime)(implicit
    ec: ExecutionContext
  ): Future[Option[WorkItem[AssignmentWorkItem]]] =
    workItemRepo.pullOutstanding(failedBefore, availableBefore)
}
