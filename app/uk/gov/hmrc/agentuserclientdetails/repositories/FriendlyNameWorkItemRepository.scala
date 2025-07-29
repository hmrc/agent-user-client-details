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

package uk.gov.hmrc.agentuserclientdetails.repositories

import com.typesafe.config.Config
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.model.Filters
import org.mongodb.scala.FindObservable
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Filters.exists
import play.api.i18n.Lang.logger
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel.SensitiveClient
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mongo.workitem.WorkItemFields
import uk.gov.hmrc.mongo.workitem.WorkItemRepository

import scala.concurrent.duration.DurationInt
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class FriendlyNameWorkItemRepository @Inject() (
  config: Config,
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext,
  mat: Materializer,
  crypto: Encrypter & Decrypter
)
extends WorkItemRepository[FriendlyNameWorkItem](
  collectionName = "client-name-work-items",
  mongoComponent = mongoComponent,
  itemFormat = FriendlyNameWorkItem.format,
  workItemFields = WorkItemFields.default
) {

  override lazy val requiresTtlIndex = false

  override def now(): Instant = Instant.now

  override def inProgressRetryAfter: Duration = config.getDuration("work-item-repository.friendly-name.retry-in-progress-after")

  // test-only to remove perf-test data.
  def deleteWorkItems(groupId: String): Future[Long] = collection.deleteMany(Filters.equal("item.groupId", groupId)).toFuture().map(_.getDeletedCount)
  def countUnencrypted(): Future[Long] = collection.countDocuments(exists("item.client.encrypted", exists = false)).toFuture()

  def encryptOldRecords(rate: Int = 10): Unit = {
    val observable: FindObservable[WorkItem[FriendlyNameWorkItem]] = collection.find(exists("item.client.encrypted", exists = false))
    countUnencrypted().map { count =>
      logger.warn(s"[FriendlyNameWorkItemRepository] automatic encryption has started, $count applications left to encrypt")
    }
    Source
      .fromPublisher(observable)
      .throttle(rate, 1.second)
      .runForeach { record =>
        collection
          .replaceOne(equal("item.groupId", record.item.groupId), record)
          .toFuture()
          .map { _ =>
            logger.warn("[FriendlyNameWorkItemRepository] successfully encrypted record")
          }
          .recover { case ex: Throwable => logger.warn("[FriendlyNameWorkItemRepository] failed to encrypt record", ex) }
        ()
      }
      .onComplete { _ =>
        countUnencrypted().map { count =>
          logger.warn(s"[FriendlyNameWorkItemRepository] encryption completed, $count applications left unencrypted")
        }
      }
  }

  encryptOldRecords()

}
