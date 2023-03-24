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

package uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{Enrolment, Identifier}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

case class SensitiveEnrolment(
  service: String,
  state: String,
  friendlyName: SensitiveString,
  identifiers: Seq[SensitiveIdentifier],
  activationDate: Option[String] = None,
  enrolmentDate: Option[String] = None
) extends Sensitive[Enrolment] {
  def decryptedValue: Enrolment = Enrolment(
    service = service,
    state = state,
    friendlyName = friendlyName.decryptedValue,
    identifiers = identifiers.map(_.decryptedValue),
    activationDate = activationDate,
    enrolmentDate = enrolmentDate
  )
}

object SensitiveEnrolment {
  def apply(enrolment: Enrolment): SensitiveEnrolment = SensitiveEnrolment(
    service = enrolment.service,
    state = enrolment.state,
    friendlyName = SensitiveString(enrolment.friendlyName),
    identifiers = enrolment.identifiers.map(SensitiveIdentifier(_)),
    activationDate = enrolment.activationDate,
    enrolmentDate = enrolment.enrolmentDate
  )
  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveEnrolment] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveEnrolment]
  }
}

case class SensitiveIdentifier(key: String, value: SensitiveString) extends Sensitive[Identifier] {
  def decryptedValue: Identifier = Identifier(key = key, value = value.decryptedValue)
}

object SensitiveIdentifier {
  def apply(identifier: Identifier): SensitiveIdentifier = SensitiveIdentifier(
    key = identifier.key,
    value = SensitiveString(identifier.value)
  )
  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveIdentifier] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveIdentifier]
  }

  implicit val ordering: Ordering[SensitiveIdentifier] = Ordering.by(_.key)
}
