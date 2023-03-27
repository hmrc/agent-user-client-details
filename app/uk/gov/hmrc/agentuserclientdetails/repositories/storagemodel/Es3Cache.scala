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

package uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel

import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class Es3Cache(
  groupId: String,
  clients: Seq[SensitiveEnrolment],
  createdAt: Instant = Instant.now()
)

object Es3Cache {
  val ClientCountField: String = "clientCount"
  implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
  def format(implicit crypto: Encrypter with Decrypter): Format[Es3Cache] = Json.format[Es3Cache]

  def merge(es3Caches: Seq[Es3Cache]): Option[Es3Cache] =
    es3Caches.headOption.map { head =>
      require(es3Caches.map(_.groupId).distinct.size == 1)
      Es3Cache(head.groupId, es3Caches.map(_.clients).reduce(_ ++ _))
    }
  def split(es3Cache: Es3Cache, groupSize: Int): Seq[Es3Cache] =
    if (es3Cache.clients.isEmpty) Seq(es3Cache)
    else
      es3Cache.clients.grouped(groupSize).toSeq.map { group =>
        es3Cache.copy(clients = group)
      }
}
