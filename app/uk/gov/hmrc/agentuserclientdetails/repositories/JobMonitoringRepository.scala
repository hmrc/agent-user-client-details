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
import uk.gov.hmrc.agentuserclientdetails.model.JobData
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{WorkItemFields, WorkItemRepository}

import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

// Notes:
// - Do not use this directly in your controllers/classes. Use JobMonitoringService, as it is much easier to stub in tests.
// - In order to line up with WorkItemRepository's internal design, item status should be interpreted as follows:
// --- ToDo: the associated job has not been checked yet
// --- Failed: we have checked whether the associated job was finished, but it wasn't yet finished (we will check later)
// --- Succeeded: the associated job has finished (whether successfully or unsuccessfully - check item payload for details)
@Singleton
class JobMonitoringRepository @Inject() (
  mongoComponent: MongoComponent,
  config: Config
)(implicit ec: ExecutionContext)
    extends WorkItemRepository[JobData](
      collectionName = "job-monitoring-work-items",
      mongoComponent = mongoComponent,
      itemFormat = JobData.format,
      workItemFields = WorkItemFields.default
    ) {

  override def now: Instant = Instant.now

  override def inProgressRetryAfter: Duration =
    config.getDuration("work-item-repository.job-monitoring.retry-in-progress-after")
}
