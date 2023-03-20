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
  def merge(es3Caches: Seq[Es3Cache]): Option[Es3Cache] =
    es3Caches.headOption.map { head =>
      require(es3Caches.map(_.groupId).distinct.size == 1)
      Es3Cache(head.groupId, es3Caches.map(_.clients).reduce(_ ++ _))
    }
  def split(es3Cache: Es3Cache, groupSize: Int): Seq[Es3Cache] =
    es3Cache.clients.grouped(groupSize).toSeq.map { group =>
      es3Cache.copy(clients = group)
    }
}

@ImplementedBy(classOf[Es3CacheRepositoryImpl])
trait Es3CacheRepository {

  def save(groupId: String, clients: Seq[Enrolment]): Future[Es3Cache]

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

  override def save(groupId: String, clients: Seq[Enrolment]): Future[Es3Cache] = {
    val timestamp = timestampSupport.timestamp()

    val es3Cache = Es3Cache(groupId, clients, timestamp)

    val documents = Es3Cache.split(es3Cache, COUNT_OF_CLIENTS_PER_DOCUMENT)

    for {
      deleteResult <- collection.deleteMany(equal(FIELD_GROUP_ID, groupId)).toFuture()
      _ = logger.info(s"Deleted ${deleteResult.getDeletedCount} existing documents for $groupId")
      savedCount <- if (documents.isEmpty) Future.successful(0)
                    else collection.insertMany(documents.map(encrypt)).toFuture().map(_.getInsertedIds.size())
      _ = logger.info(s"Inserted $savedCount documents for $groupId")
    } yield es3Cache
  }

  override def fetch(groupId: String): Future[Option[Es3Cache]] =
    collection
      .find(equal(FIELD_GROUP_ID, groupId))
      .map(decrypt)
      .toFuture()
      .map(Es3Cache.merge)

  private def encrypt(es3Cache: Es3Cache): Es3Cache =
    es3Cache.copy(clients =
      es3Cache.clients.map(enrolment =>
        enrolment.copy(
          friendlyName = crypto.encrypt(PlainText(enrolment.friendlyName)).value,
          identifiers = enrolment.identifiers.map(id => id.copy(value = crypto.encrypt(PlainText(id.value)).value))
        )
      )
    )

  private def decrypt(encryptedEs3Cache: Es3Cache): Es3Cache =
    encryptedEs3Cache.copy(clients =
      encryptedEs3Cache.clients.map(encryptedEnrolment =>
        encryptedEnrolment.copy(
          friendlyName = crypto.decrypt(Crypted(encryptedEnrolment.friendlyName)).value,
          identifiers =
            encryptedEnrolment.identifiers.map(id => id.copy(value = crypto.decrypt(Crypted(id.value)).value))
        )
      )
    )

}
