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

import play.api.libs.json.JsResultException
import play.api.libs.json.Json
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class CgtSubscriptionSpec
extends BaseSpec {

  "IndividualName" should {

    val model: IndividualName = IndividualName("First", "Last")
    val json = Json.obj("firstName" -> "First", "lastName" -> "Last")

    "read from JSON" in {
      json.as[IndividualName] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }

  "OrganisationName" should {

    val model: OrganisationName = OrganisationName("Org")
    val json = Json.obj("name" -> "Org")

    "read from JSON" in {
      json.as[OrganisationName] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }

  "TypeOfPersonDetails" when {

    "the person type is Individual" should {

      val model: TypeOfPersonDetails = TypeOfPersonDetails("Individual", Left(IndividualName("First", "Last")))
      val json = Json.obj(
        "typeOfPerson" -> "Individual",
        "firstName" -> "First",
        "lastName" -> "Last"
      )

      "read from JSON" in {
        json.as[TypeOfPersonDetails] shouldBe model
      }

      "write to JSON" in {
        Json.toJson(model) shouldBe json
      }
    }

    "the person type is Organisation" should {
      val model: TypeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("Org")))
      val json = Json.obj("typeOfPerson" -> "Trustee", "organisationName" -> "Org")

      "read from JSON" in {
        json.as[TypeOfPersonDetails] shouldBe model
      }

      "write to JSON" in {
        Json.toJson(model) shouldBe json
      }
    }

    "the person type is unrecognised" should {

      "fail to read from JSON" in {
        val json = Json.obj("typeOfPerson" -> "Anon", "organisationName" -> "Org")
        intercept[JsResultException](json.as[TypeOfPersonDetails])
      }
    }
  }

  "SubscriptionDetails" should {

    val typeOfPerson: TypeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("Org")))
    val model: SubscriptionDetails = SubscriptionDetails(typeOfPerson)
    val json = Json.obj("typeOfPersonDetails" -> Json.toJson(typeOfPerson))

    "read from JSON" in {
      json.as[SubscriptionDetails] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }

  "CgtSubscription" should {

    val typeOfPerson: TypeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("Org")))
    val subscriptionDetails: SubscriptionDetails = SubscriptionDetails(typeOfPerson)
    val model = CgtSubscription(subscriptionDetails)
    val json = Json.obj("subscriptionDetails" -> Json.toJson(subscriptionDetails))

    "read from JSON" in {
      json.as[CgtSubscription] shouldBe model
    }

    "write to JSON" in {
      Json.toJson(model) shouldBe json
    }
  }

}
