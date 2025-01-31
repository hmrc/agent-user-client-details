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

import play.api.libs.json.{Json, OFormat, Reads}

case class AgentDetailsDesResponse(agencyDetails: Option[AgencyDetails])

case class AgencyDetails(agencyName: Option[String], agencyEmail: Option[String])

object AgencyDetails {
  implicit val agencyDetailsFormat: OFormat[AgencyDetails] = Json.format[AgencyDetails]
}

object AgentDetailsDesResponse {
  implicit val agencyDetailsRead: Reads[AgencyDetails] = Json.reads

  implicit val agentRecordDetailsRead: Reads[AgentDetailsDesResponse] = Json.reads

  implicit val agencyDetailsFormat: OFormat[AgentDetailsDesResponse] = Json.format[AgentDetailsDesResponse]
}
