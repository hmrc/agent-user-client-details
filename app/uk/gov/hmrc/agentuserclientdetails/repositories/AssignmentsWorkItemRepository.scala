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
import uk.gov.hmrc.agentuserclientdetails.model.AssignmentWorkItem
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{WorkItemFields, WorkItemRepository}

import java.time.{Duration, Instant}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

case class AssignmentsWorkItemRepository @Inject() (config: Config, mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext
) extends WorkItemRepository[AssignmentWorkItem](
      collectionName = "assignments-work-items",
      mongoComponent = mongoComponent,
      itemFormat = AssignmentWorkItem.format,
      workItemFields = WorkItemFields.default
    ) {

  override def now(): Instant = Instant.now()

  override def inProgressRetryAfter: Duration =
    config.getDuration("work-item-repository.assignments.retry-in-progress-after")
}
