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

package uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel

import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.model.accessgroups.Client
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

class SensitiveClientSpec
extends BaseSpec {

  implicit val crypto: Encrypter
  & Decrypter = aesCrypto

  val client: Client = Client("HMRC-MTD-VAT~VRN~123456789", "Smith Roberts")
  val sensitiveClient: SensitiveClient = SensitiveClient(client)
  val sensitiveJson: JsObject = Json.obj(
    "enrolmentKey" -> "ddtpL0YcymEiA6dH+XLNcN2oYy6tDgEBCZrecQlriRE=",
    "friendlyName" -> "RRhGxwmDG4jML/ChHcNOYA=="
  )

  "SensitiveClient" should {

    "write to JSON" in {
      Json.toJson(sensitiveClient) shouldBe sensitiveJson
    }

    "read from JSON" in {
      sensitiveJson.as[SensitiveClient].decryptedValue shouldBe client
    }
  }

}
