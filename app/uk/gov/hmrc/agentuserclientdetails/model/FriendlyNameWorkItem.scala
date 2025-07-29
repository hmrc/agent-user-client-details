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

package uk.gov.hmrc.agentuserclientdetails.model

import play.api.libs.json.Format
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel.SensitiveClient

import java.time.Instant

case class FriendlyNameWorkItem(
  groupId: String,
  client: SensitiveClient,
  sessionId: Option[String] = None // Only required for local testing against stubs. Always set to None for QA/Prod
)

object FriendlyNameWorkItem {
  implicit def format(implicit crypto: Encrypter & Decrypter): Format[FriendlyNameWorkItem] = Json.format[FriendlyNameWorkItem]
}
