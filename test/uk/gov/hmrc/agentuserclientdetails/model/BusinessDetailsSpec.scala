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

import BusinessDetails._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

class BusinessDetailsSpec extends BaseSpec {

  val businessAddressDetailsModel: BusinessAddressDetails = BusinessAddressDetails("GB", Some("SK11AB"))
  val businessAddressDetailsJson: JsObject = Json.obj("countryCode" -> "GB", "postalCode" -> "SK11AB")

  val businessDataModel: BusinessData = BusinessData(Some(businessAddressDetailsModel))
  val businessDataJson: JsObject = Json.obj("businessAddressDetails" -> businessAddressDetailsJson)

  "BusinessAddressDetails" should {
    
    "read from JSON" in {
      businessAddressDetailsJson.as[BusinessAddressDetails] shouldBe businessAddressDetailsModel
    }
  }
  
  "BusinessData" should {

    "read from JSON" in {
      businessDataJson.as[BusinessData] shouldBe businessDataModel
    }
  }
  
  "BusinessDetails" should {

    val model: BusinessDetails = BusinessDetails(Seq(businessDataModel), Some(MtdItId("XAIT1234567890")))
    val json = Json.obj("businessData" -> Json.arr(businessDataJson), "mtdId" -> "XAIT1234567890")

    "read from JSON" in {
      json.as[BusinessDetails] shouldBe model
    }
  }
}
