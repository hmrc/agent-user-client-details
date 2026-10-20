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
import uk.gov.hmrc.agentuserclientdetails.model.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel.SensitiveClient
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

class FriendlyNameWorkItemSpec
extends BaseSpec {

  given crypto: Encrypter
    & Decrypter = aesCrypto

  "FriendlyNameWorkItem" should {
    val client: Client = Client("HMRC-MTD-VAT~VRN~123456789", "Smith Roberts")
    val sensitiveClient = SensitiveClient(client)

    val model: FriendlyNameWorkItem = FriendlyNameWorkItem(
      "ID123",
      sensitiveClient,
      Some("abcedfg-qwerty")
    )

    "round-trip to and from JSON" in {
      val json = Json.toJson(model)
      json.as[FriendlyNameWorkItem].shouldBe(model)
    }

    "write non-encrypted fields as plain text and encrypt the client fields" in {
      val json = Json.toJson(model).as[JsObject]

      (json \ "groupId").as[String].shouldBe("ID123")
      (json \ "sessionId").as[String].shouldBe("abcedfg-qwerty")

      val clientJson = (json \ "client").as[JsObject]
      (clientJson \ "enrolmentKey").as[String].shouldNot(be("HMRC-MTD-VAT~VRN~123456789"))
      (clientJson \ "friendlyName").as[String].shouldNot(be("Smith Roberts"))
    }
  }

}
