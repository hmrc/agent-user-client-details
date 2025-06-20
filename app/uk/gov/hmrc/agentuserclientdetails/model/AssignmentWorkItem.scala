/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

case class AssignmentWorkItem(
  operation: Operation,
  userId: String,
  enrolmentKey: String,
  arn: String,
  sessionId: Option[String] = None // Only required for local testing against stubs. Always set to None for QA/Prod
)

object AssignmentWorkItem {
  implicit val format: Format[AssignmentWorkItem] = Json.format[AssignmentWorkItem]
}

sealed trait Operation
case object Assign
extends Operation
case object Unassign
extends Operation

object Operation {
  implicit val format: Format[Operation] =
    new Format[Operation] {
      def writes(o: Operation): JsValue =
        o match {
          case Assign => JsString("assign")
          case Unassign => JsString("unassign")
        }
      def reads(json: JsValue): JsResult[Operation] =
        json match {
          case JsString("assign") => JsSuccess(Assign)
          case JsString("unassign") => JsSuccess(Unassign)
          case x => JsError("unexpected value for Operation: " + x.toString)
        }
    }
}
