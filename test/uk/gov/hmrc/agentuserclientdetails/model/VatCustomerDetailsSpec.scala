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

class VatCustomerDetailsSpec extends BaseSpec {

  val vatIndividualModel: VatIndividual = VatIndividual(Some("Mr"), Some("First"), Some("Middle"), Some("Last"))
  val vatIndividualJson: JsObject = Json.obj(
    "title" -> "0001",
    "firstName" -> "First",
    "middleName" -> "Middle",
    "lastName" -> "Last"
  )
  val vatIndividualJsonWrites: JsObject = Json.obj(
    "title" -> "Mr",
    "firstName" -> "First",
    "middleName" -> "Middle",
    "lastName" -> "Last"
  )

  "VatIndividual" should {

    "read from JSON" in {
      vatIndividualJson.as[VatIndividual] shouldBe vatIndividualModel
    }

    "write to JSON" in {
      Json.toJson(vatIndividualModel) shouldBe vatIndividualJsonWrites
    }
    
    "return a single individual name comprising of all name fields" in {
      vatIndividualModel.name shouldBe "Mr First Middle Last"
    }
  }

  "VatCustomerDetails" should {

    val model: VatCustomerDetails = VatCustomerDetails(Some("Org Name"), Some(vatIndividualModel), Some("Trading Name"))
    val json = Json.obj(
      "organisationName" -> "Org Name",
      "individual" -> vatIndividualJson,
      "tradingName" -> "Trading Name"
    )
    val jsonWrites = Json.obj(
      "organisationName" -> "Org Name",
      "individual" -> vatIndividualJsonWrites,
      "tradingName" -> "Trading Name"
    )

    "read from JSON" in {
      json.as[VatCustomerDetails] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe jsonWrites
    }
  }
}
