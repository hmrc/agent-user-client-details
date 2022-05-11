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

package uk.gov.hmrc.agentuserclientdetails.repositories

import org.joda.time.DateTime
import play.api.Configuration
import play.api.libs.json._
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.workitem.{WorkItem, _}
import reactivemongo.play.json.ImplicitBSONHandlers.{BSONObjectIDFormat, JsObjectDocumentWriter}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class FriendlyNameWorkItemRepository @Inject()(
                                 configuration: Configuration)(implicit mongo: () => DB)
  extends WorkItemRepository[FriendlyNameWorkItem, BSONObjectID](
    "client-name-work-items",
    mongo,
    WorkItem.workItemMongoFormat[FriendlyNameWorkItem],
    configuration.underlying) {

  implicit val dateFormats: Format[DateTime] =
    ReactiveMongoFormats.dateTimeFormats

  lazy val inProgressRetryAfterProperty = "work-item-repository.inProgressRetryAfter.millis"

  lazy val workItemFields: WorkItemFieldNames = new WorkItemFieldNames {
    val receivedAt = "createdAt"
    val updatedAt = "lastUpdated"
    val availableAt = "availableAt"
    val status = "status"
    val id = "_id"
    val failureCount = "failures"
  }

  override def now: DateTime = DateTime.now

  def totalTodo(implicit ec: ExecutionContext): Future[Int] = count(ToDo)
  def totalFailed(implicit ec: ExecutionContext): Future[Int] = count(Failed)
  def totalOutstanding(implicit ec: ExecutionContext): Future[Int] = for {
    todo <- totalTodo
    failed <- totalFailed
  } yield todo + failed

  /**
   * Query by groupId and optionally by status (leave status as None to include all statuses)
   */
  def query(groupId: String, status: Option[Seq[ProcessingStatus]], limit: Int = -1)(implicit ec: ExecutionContext): Future[Seq[WorkItem[FriendlyNameWorkItem]]] = {
    val selector = status match {
      case Some(statuses) => Json.obj("item.groupId" -> JsString(groupId), "status" -> Json.obj("$in" -> JsArray(statuses.map(s => JsString(s.name)))))
      case None => Json.obj("item.groupId" -> JsString(groupId))
    }
    collection
      .find(selector, projection = None)
      .cursor[WorkItem[FriendlyNameWorkItem]]()
      .collect[Seq](limit, Cursor.FailOnError())
  }

  /**
   * Removes any items that have been marked as successful or duplicated.
   */
  def cleanup()(implicit ec: ExecutionContext): Future[WriteResult] = {
    this.remove(
      "status" -> Json.obj("$in" -> JsArray(Seq(JsString(Succeeded.name), JsString(Duplicate.name))))
    )
  }

  /**
   * Counts the number of work items in the repository in each status (to-do, succeeded, failed etc.)
   */
  def collectStats(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    import collection.BatchCommands.AggregationFramework.{Group, GroupFunction, SumAll}
    collection
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
}
