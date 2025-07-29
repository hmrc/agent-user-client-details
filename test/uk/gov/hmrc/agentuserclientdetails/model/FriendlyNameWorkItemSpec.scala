/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.libs.json.JsObject
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel.SensitiveClient
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

class FriendlyNameWorkItemSpec
extends BaseSpec {

  implicit val crypto: Encrypter
    with Decrypter = aesCrypto

  "FriendlyNameWorkItem" should {
    val client: Client = Client("HMRC-MTD-VAT~VRN~123456789", "Smith Roberts")
    val sensitiveClient = SensitiveClient(client)

    val model: FriendlyNameWorkItem = FriendlyNameWorkItem(
      "ID123",
      sensitiveClient,
      Some("abcedfg-qwerty")
    )

    val json = Json.obj(
      "groupId" -> "ID123",
      "client" -> Json.obj(
        "enrolmentKey" -> "ddtpL0YcymEiA6dH+XLNcN2oYy6tDgEBCZrecQlriRE=",
        "friendlyName" -> "RRhGxwmDG4jML/ChHcNOYA==",
        "encrypted" -> true
      ),
      "sessionId" -> "abcedfg-qwerty"
    )

    "read from JSON" in {
      json.as[FriendlyNameWorkItem] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }

}
