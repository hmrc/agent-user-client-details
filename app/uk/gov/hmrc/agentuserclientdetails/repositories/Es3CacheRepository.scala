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

import com.google.inject.ImplementedBy
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.Enrolment
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.SECONDS
import scala.concurrent.{ExecutionContext, Future}

case class Es3Cache(groupId: String, clients: Seq[Enrolment], createdAt: Instant = Instant.now())

object Es3Cache {
  implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val formatEs3Cache: OFormat[Es3Cache] = Json.format[Es3Cache]
}

@ImplementedBy(classOf[Es3CacheRepositoryImpl])
trait Es3CacheRepository {

  def save(groupId: String, clients: Seq[Enrolment]): Future[String]

  def fetch(groupId: String): Future[Option[Es3Cache]]
}

/** Caches ES3 enrolments of an agent's groupId for a configurable duration.
  */
@Singleton
class Es3CacheRepositoryImpl @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  crypto: Encrypter with Decrypter,
  timestampSupport: TimestampSupport
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Es3Cache](
      mongoComponent = mongoComponent,
      collectionName = "es3-cache",
      domainFormat = Es3Cache.formatEs3Cache,
      indexes = Seq(
        IndexModel(
          ascending("groupId"),
          IndexOptions().name("idxGroupId")
        ),
        IndexModel(
          ascending("createdAt"),
          IndexOptions()
            .background(false)
            .name("idxCreatedAt")
            .expireAfter(appConfig.es3CacheRefreshDuration.toSeconds, SECONDS)
        )
      ),
      replaceIndexes = true
    ) with Es3CacheRepository with Logging {

  private val COUNT_OF_CLIENTS_PER_DOCUMENT = 20000

  private val FIELD_GROUP_ID = "groupId"

  override def save(groupId: String, clients: Seq[Enrolment]): Future[String] = {
    val timestamp = timestampSupport.timestamp()

    val es3CacheBatches = clients
      .grouped(COUNT_OF_CLIENTS_PER_DOCUMENT)
      .toSeq
      .map(batchOfClients => Es3Cache(groupId, encryptFields(batchOfClients), timestamp))

    collection.deleteMany(equal(FIELD_GROUP_ID, groupId)).toFuture() flatMap { deleteResult =>
      logger.info(s"Deleted ${deleteResult.getDeletedCount} existing documents for $groupId")

      collection.insertMany(es3CacheBatches).toFuture().map { insertManyResult =>
        logger.info(s"Saved in DB for $groupId across ${insertManyResult.getInsertedIds.size()} document(s)")
        groupId
      }
    }
  }

  override def fetch(groupId: String): Future[Option[Es3Cache]] = {

    def collateClients(es3Caches: Seq[Es3Cache]): Option[Es3Cache] =
      if (es3Caches.isEmpty) {
        Option.empty[Es3Cache]
      } else {
        val accumulatedEs3Cache =
          es3Caches.foldLeft(Es3Cache(groupId, Seq.empty[Enrolment])) { (acc, es3Cache) =>
            Es3Cache(groupId, acc.clients ++ decryptFields(es3Cache.clients))
          }
        Option(accumulatedEs3Cache)
      }

    collection
      .find(equal(FIELD_GROUP_ID, groupId))
      .toFuture()
      .map(collateClients)
  }

  private def encryptFields(clients: Seq[Enrolment]): Seq[Enrolment] =
    clients.map(enrolment =>
      enrolment.copy(
        friendlyName = crypto.encrypt(PlainText(enrolment.friendlyName)).value,
        identifiers = enrolment.identifiers.map(id => id.copy(value = crypto.encrypt(PlainText(id.value)).value))
      )
    )

  private def decryptFields(clients: Seq[Enrolment]): Seq[Enrolment] =
    clients.map(enrolment =>
      enrolment.copy(
        friendlyName = crypto.decrypt(Crypted(enrolment.friendlyName)).value,
        identifiers = enrolment.identifiers.map(id => id.copy(value = crypto.decrypt(Crypted(id.value)).value))
      )
    )

}
