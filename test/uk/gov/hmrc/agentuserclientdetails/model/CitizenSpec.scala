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

class CitizenSpec
extends BaseSpec {

  "Citizen" should {

    "read from JSON" in {
      val model: Citizen = Citizen(Some("First"), Some("Last"))
      val json = Json.obj(
        "name" -> Json.obj(
          "current" -> Json.obj(
            "firstName" -> "First",
            "lastName" -> "Last"
          )
        )
      )
      json.as[Citizen] shouldBe model
    }

    "return a single name" when {

      "citizen has only a first name" in {
        val model: Citizen = Citizen(Some("First"), None)
        model.name shouldBe Some("First")
      }

      "citizen has only a last name" in {
        val model: Citizen = Citizen(None, Some("Last"))
        model.name shouldBe Some("Last")
      }

      "citizen has both first and last names" in {
        val model: Citizen = Citizen(Some("First"), Some("Last"))
        model.name shouldBe Some("First Last")
      }
    }

    "fail to return a name when citizen does not have any names" in {
      val model: Citizen = Citizen(None, None)
      model.name shouldBe None
    }
  }
}
