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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.BSONObjectIDFormat
import uk.gov.hmrc.agentuserclientdetails.model.JobData
import uk.gov.hmrc.agentuserclientdetails.util.MongoProvider
import uk.gov.hmrc.workitem.{WorkItem, WorkItemFieldNames, WorkItemRepository}

import javax.inject.{Inject, Singleton}

// Notes:
// - Do not use this directly in your controllers/classes. Use JobMonitoringService, as it is much easier to stub in tests.
// - In order to line up with WorkItemRepository's internal design, item status should be interpreted as follows:
// --- ToDo: the associated job has not been checked yet
// --- Failed: we have checked whether the associated job was finished, but it wasn't yet finished (we will check later)
// --- Succeeded: the associated job has finished (whether successfully or unsuccessfully - check item payload for details)
@Singleton
class JobMonitoringRepository @Inject() (
  config: Config
)(implicit mongo: MongoProvider)
    extends WorkItemRepository[JobData, BSONObjectID](
      "job-monitoring-work-items",
      mongo.value,
      WorkItem.workItemMongoFormat[JobData],
      config
    ) {
  lazy val inProgressRetryAfterProperty = "work-item-repository.job-monitoring.retry-in-progress-after-millis"

  lazy val workItemFields: WorkItemFieldNames = new WorkItemFieldNames {
    val receivedAt = "createdAt"
    val updatedAt = "lastUpdated"
    val availableAt = "availableAt"
    val status = "status"
    val id = "_id"
    val failureCount = "failures"
  }

  override def now: DateTime = DateTime.now

  override def indexes: Seq[Index] = super.indexes ++ Seq(
    Index(key = Seq("item.groupId" -> IndexType.Ascending), unique = false, background = true),
    Index(key = Seq("item.jobType" -> IndexType.Ascending), unique = false, background = true)
  )
}
