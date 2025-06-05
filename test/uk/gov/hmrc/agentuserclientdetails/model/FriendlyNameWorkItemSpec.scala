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
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class FriendlyNameWorkItemSpec extends BaseSpec {

  "FriendlyNameWorkItem" should {

    val model: FriendlyNameWorkItem = FriendlyNameWorkItem(
      "ID123",
      Client("HMRC-MTD-IT", "Roy"),
      Some("abcedfg-qwerty")
    )

    val json = Json.obj(
      "groupId" -> "ID123",
      "client" -> Json.obj("enrolmentKey" -> "HMRC-MTD-IT", "friendlyName" -> "Roy"),
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
