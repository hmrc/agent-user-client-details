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

package uk.gov.hmrc.agentuserclientdetails.util

object EnrolmentKey {
  def enrolmentKey(serviceId: String, clientId: String): String = serviceId match {
    case "HMRC-MTD-IT"     => "HMRC-MTD-IT~MTDITID~" + clientId
    case "HMRC-MTD-VAT"    => "HMRC-MTD-VAT~VRN~" + clientId
    case "HMRC-MTD-IT"     => "HMRC-MTD-IT~NINO~" + clientId
    case "HMRC-TERS-ORG"   => "HMRC-TERS-ORG~SAUTR~" + clientId
    case "HMRC-TERSNT-ORG" => "HMRC-TERSNT-ORG~URN~" + clientId
    case "HMRC-CGT-PD"     => "HMRC-CGT-PD~CGTPDRef~" + clientId
    case "HMRC-PPT-ORG"    => "HMRC-PPT-ORG~EtmpRegistrationNumber~" + clientId
    case "HMRC-PT"         => "HMRC-PT~NINO~" + clientId // TODO Check: is this correct for HMRC-PT (IRV)?
    case _                 => throw new IllegalArgumentException(s"Service not supported: $serviceId")
  }

}
