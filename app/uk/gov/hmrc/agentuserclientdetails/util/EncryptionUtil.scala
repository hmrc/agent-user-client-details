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

package uk.gov.hmrc.agentuserclientdetails.util

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption.sensitiveDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

object EncryptionUtil {

  def decryptToSensitive(
    fieldName: String,
    isEncrypted: Option[Boolean],
    json: JsValue
  )(implicit
    crypto: Encrypter
      with Decrypter
  ): SensitiveString = {
    val stringValue = (json \ fieldName).as[String]
    isEncrypted match {
      case Some(true) =>
        val decrypter = sensitiveDecrypter(SensitiveString.apply)
        decrypter.reads(JsString(stringValue)).get
      case _ => SensitiveString(stringValue)
    }
  }
}
