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

import play.api.libs.json._

case class TrustName(name: String)

object TrustName {
  implicit val format: Format[TrustName] = Json.format[TrustName]
}

case class InvalidTrust(code: String, reason: String)

object InvalidTrust {
  implicit val format: Format[InvalidTrust] = Json.format[InvalidTrust]
}

case class TrustResponse(response: Either[InvalidTrust, TrustName])
