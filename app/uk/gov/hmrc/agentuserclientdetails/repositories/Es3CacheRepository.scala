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

import com.google.inject.ImplementedBy
import play.api.libs.json.{Json, OFormat}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.Enrolment
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.mongo.cache.CacheIdType.SimpleCacheId
import uk.gov.hmrc.mongo.cache.{DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class Es3Cache(clients: Seq[Enrolment])

object Es3Cache {
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
  configuration: Configuration,
  timestampSupport: TimestampSupport,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends MongoCacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "es3-cache",
      ttl = appConfig.es3CacheRefreshDuration,
      cacheIdType = SimpleCacheId,
      timestampSupport = timestampSupport
    ) with Es3CacheRepository with Logging {

  override def save(groupId: String, clients: Seq[Enrolment]): Future[String] =
    put(groupId)(DataKey[Es3Cache](groupId), Es3Cache(clients)).map(_.id)

  override def fetch(groupId: String): Future[Option[Es3Cache]] =
    get[Es3Cache](groupId)(DataKey[Es3Cache](groupId))

}
