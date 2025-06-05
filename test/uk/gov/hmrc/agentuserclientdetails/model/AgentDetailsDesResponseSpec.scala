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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class AgentDetailsDesResponseSpec extends BaseSpec {

  val agencyDetailsModel: AgencyDetails = AgencyDetails(Some("ABC Agents"), Some("abc@agents.com"))
  val agencyDetailsJson: JsObject = Json.obj("agencyName" -> "ABC Agents", "agencyEmail" -> "abc@agents.com")
  
  "AgencyDetails" should {

    "read from JSON" in {
      agencyDetailsJson.as[AgencyDetails] shouldBe agencyDetailsModel
    }
    
    "write to JSON" in {
      Json.toJson(agencyDetailsModel) shouldBe agencyDetailsJson
    }
  }

  "AgentDetailsDesResponse" should {

    val model: AgentDetailsDesResponse = AgentDetailsDesResponse(Some(agencyDetailsModel))
    val json = Json.obj("agencyDetails" -> agencyDetailsJson)

    "read from JSON" in {
      json.as[AgentDetailsDesResponse] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }
}
