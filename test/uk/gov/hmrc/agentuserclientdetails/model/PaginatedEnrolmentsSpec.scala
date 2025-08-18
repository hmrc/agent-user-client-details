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
import uk.gov.hmrc.agentuserclientdetails.model.accessgroups.Enrolment
import uk.gov.hmrc.agentuserclientdetails.model.accessgroups.Identifier
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class PaginatedEnrolmentsSpec
extends BaseSpec {

  "PaginatedEnrolments" should {

    val mtdVatEnrolment: Enrolment = Enrolment(
      "HMRC-MTD-VAT",
      "Activated",
      "John Innes",
      Seq(Identifier("VRN", "101747641"))
    )
    val model: PaginatedEnrolments = PaginatedEnrolments(
      1,
      2,
      Seq(mtdVatEnrolment)
    )
    val json = Json.obj(
      "startRecord" -> 1,
      "totalRecords" -> 2,
      "enrolments" -> Json.arr(
        Json.obj(
          "service" -> "HMRC-MTD-VAT",
          "state" -> "Activated",
          "friendlyName" -> "John Innes",
          "identifiers" -> Json.arr(Json.obj("key" -> "VRN", "value" -> "101747641"))
        )
      )
    )

    "read from JSON" in {
      json.as[PaginatedEnrolments] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }
}
