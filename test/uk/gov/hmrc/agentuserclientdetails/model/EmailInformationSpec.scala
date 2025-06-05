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
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class EmailInformationSpec extends BaseSpec {

  "EmailInformation" should {

    val model: EmailInformation = EmailInformation(
      Seq("me@email.com"),
      "ABC123",
      Map("param1" -> "value1"),
      true,
      Some("/event-url"),
      Some("/on-send-url")
    )

    val json = Json.obj(
      "to" -> Json.arr("me@email.com"),
      "templateId" -> "ABC123",
      "parameters" -> Json.obj("param1" -> "value1"),
      "force" -> true,
      "eventUrl" -> "/event-url",
      "onSendUrl" -> "/on-send-url"
    )

    "read from JSON" in {
      json.as[EmailInformation] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }
}
