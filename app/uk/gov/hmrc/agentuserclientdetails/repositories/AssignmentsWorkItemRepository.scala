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

import com.typesafe.config.Config
import org.joda.time.DateTime
import play.api.libs.json._
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.BSONObjectIDFormat
import uk.gov.hmrc.agentuserclientdetails.model.AssignmentWorkItem
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.workitem._

import javax.inject.Inject

case class AssignmentsWorkItemRepository @Inject() (config: Config)(implicit mongo: () => DB)
    extends WorkItemRepository[AssignmentWorkItem, BSONObjectID](
      "assignments-work-items",
      mongo,
      WorkItem.workItemMongoFormat[AssignmentWorkItem],
      config
    ) {

  implicit val dateFormats: Format[DateTime] =
    ReactiveMongoFormats.dateTimeFormats

  lazy val inProgressRetryAfterProperty = "work-item-repository.assignments.inProgressRetryAfter.millis"

  lazy val workItemFields: WorkItemFieldNames = new WorkItemFieldNames {
    val receivedAt = "createdAt"
    val updatedAt = "lastUpdated"
    val availableAt = "availableAt"
    val status = "status"
    val id = "_id"
    val failureCount = "failures"
  }

  override def now: DateTime = DateTime.now

}
