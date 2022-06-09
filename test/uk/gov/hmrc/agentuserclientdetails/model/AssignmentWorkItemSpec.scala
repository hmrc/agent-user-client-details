/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, Json}

class AssignmentWorkItemSpec extends AnyWordSpec with Matchers {

  val testUserId = "ABCEDEFGI1234568"
  val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"

  "AssignmentWorkItemSpec" should {
    "serialise/deserialise correctly" in {
      val assignWi = AssignmentWorkItem(Assign, "ABCEDEFGI1234568", "HMRC-MTD-VAT~VRN~12345678", Some("testSessionId"))
      val unassignWi =
        AssignmentWorkItem(Unassign, "ABCEDEFGI1234568", "HMRC-MTD-VAT~VRN~12345678", Some("testSessionId"))

      Json.toJson(assignWi).as[AssignmentWorkItem] shouldBe assignWi
      Json.toJson(unassignWi).as[AssignmentWorkItem] shouldBe unassignWi
    }
    "throw a JS error for an invalid operation" in {
      Json.fromJson[AssignmentWorkItem](
        Json.parse("""{
                     |  "operation" : "foo",
                     |  "userId" : "myUserId",
                     |  "enrolmentKey" : "myEnrolmentKey"
                     |}""".stripMargin)
      ) should matchPattern { case JsError(_) =>
      }
    }
  }
}
