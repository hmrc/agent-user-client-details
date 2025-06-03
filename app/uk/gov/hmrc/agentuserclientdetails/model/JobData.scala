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

import play.api.libs.json.{Format, JsDefined, JsError, JsObject, JsResult, JsString, JsValue, Json, OFormat}

// Keeping it generic so our options are open in case we want to track other kind of jobs later (e.g. ES11/ES12 assignments, etc.)
sealed trait JobData

object JobData {
  implicit val format: Format[JobData] = new Format[JobData] {
    // Kludgy format unfortunately due to mongo codecs not generating correctly otherwise.
    def writes(o: JobData): JsValue = o match {
      case x: FriendlyNameJobData => Json.toJsObject(x)
    }
    def reads(json: JsValue): JsResult[JobData] = json match {
      case x: JsObject =>
        x \ "jobType" match {
          case JsDefined(JsString(FriendlyNameJobData.jobType)) => Json.fromJson[FriendlyNameJobData](x)
          case _                                                => JsError("Expected field 'jobType' was not present")
        }
      case _ => JsError("Expected JSON object")
    }
  }
}

case class FriendlyNameJobData(
  groupId: String,
  enrolmentKeys: Seq[String],
  sendEmailOnCompletion: Boolean,
  agencyName: Option[String],
  email: Option[String],
  emailLanguagePreference: Option[String], // "en" or "cy"
  jobType: String =
    FriendlyNameJobData.jobType, // do not change this. Must include it explicitly or the mongo codec will not generate correctly.
  sessionId: Option[String] = None // Only required for local testing against stubs. Always set to None for QA/Prod
) extends JobData

object FriendlyNameJobData {
  val jobType = "FriendlyNameJob"
  implicit val format: OFormat[FriendlyNameJobData] = Json.format[FriendlyNameJobData]
}
