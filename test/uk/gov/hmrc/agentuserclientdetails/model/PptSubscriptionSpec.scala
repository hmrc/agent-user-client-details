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

import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class PptSubscriptionSpec extends BaseSpec {

  "PptSubscription" should {

    val model: PptSubscription = PptSubscription("Plastic Man")

    "read from JSON when customer type is Individual" in {
      val json = Json.obj(
        "legalEntityDetails" -> Json.obj(
          "customerDetails" -> Json.obj(
            "customerType" -> "Individual",
            "individualDetails" -> Json.obj(
              "firstName" -> "Plastic",
              "lastName" -> "Man"
            )
          )
        )
      )
      PptSubscription.reads(json).get shouldBe model
    }

    "read from JSON when customer type is Organisation" in {
      val json = Json.obj(
        "legalEntityDetails" -> Json.obj(
          "customerDetails" -> Json.obj(
            "customerType" -> "Organisation",
            "organisationDetails" -> Json.obj(
              "organisationName" -> "Plastic Man"
            )
          )
        )
      )
      PptSubscription.reads(json).get shouldBe model
    }

    "fail to read from JSON when customer type is not recognised" in {
      val json = Json.obj(
        "legalEntityDetails" -> Json.obj(
          "customerDetails" -> Json.obj(
            "customerType" -> "Anon"
          )
        )
      )
      PptSubscription.reads(json) shouldBe JsError("unknown customerType Anon")
    }
  }
}
