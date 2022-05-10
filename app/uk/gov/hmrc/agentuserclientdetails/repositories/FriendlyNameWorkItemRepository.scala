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

  def queryByGroupId(groupId: String, limit: Int = -1)(implicit ec: ExecutionContext): Future[Seq[WorkItem[FriendlyNameWorkItem]]] = {
    collection
      .find(selector = Json.obj("item.groupId" -> JsString(groupId)), projection = None)
      .cursor[WorkItem[FriendlyNameWorkItem]]()
      .collect[Seq](limit, Cursor.FailOnError())
  }

  def queryPermanentlyFailedByGroupId(groupId: String, limit: Int = -1)(implicit ec: ExecutionContext): Future[Seq[WorkItem[FriendlyNameWorkItem]]] = {
    collection
      .find(selector = Json.obj("item.groupId" -> JsString(groupId), "status" -> JsString(PermanentlyFailed.name)), projection = None)
      .cursor[WorkItem[FriendlyNameWorkItem]]()
      .collect[Seq](limit, Cursor.FailOnError())
  }
}