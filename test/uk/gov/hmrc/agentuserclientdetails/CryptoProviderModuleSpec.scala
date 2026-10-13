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

package uk.gov.hmrc.agentuserclientdetails

import com.typesafe.config.ConfigFactory
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.crypto.PlainBytes
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoProviderModuleSpec
extends PlaySpec {

  def configuration(fieldLevelEncryptionEnabled: Boolean) = Configuration(
    ConfigFactory.parseString(s"""fieldLevelEncryption {
                                 |  enable = $fieldLevelEncryptionEnabled
                                 |  key = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x"
                                 |}
                                 |""".stripMargin)
  )

  "CryptoProviderModule" should {
    "provide a real crypto instance if field-level encryption is enabled in config" in {
      val x = new CryptoProviderModule().cryptoInstance(configuration(fieldLevelEncryptionEnabled = true))
      x must not be a[NoCrypto]
    }
    "provide a no-op crypto instance if field-level encryption is enabled in config" in {
      val x = new CryptoProviderModule().cryptoInstance(configuration(fieldLevelEncryptionEnabled = false))
      x mustBe a[NoCrypto]
    }
  }

  "aesCryptoInstance" should {
    "encrypt new values with AES-GCM from field level encryption config" in {
      val crypto = new CryptoProviderModule().cryptoInstance(configuration(fieldLevelEncryptionEnabled = true))
      val firstEncrypted = crypto.encrypt(PlainText("user-answer"))
      val secondEncrypted = crypto.encrypt(PlainText("user-answer"))
      firstEncrypted.value must not be "user-answer"
      secondEncrypted.value must not be "user-answer"
      firstEncrypted must not be secondEncrypted
      crypto.decrypt(firstEncrypted).value mustBe "user-answer"
      crypto.decrypt(secondEncrypted).value mustBe "user-answer"
    }

    "decrypt old AES values using the same field level encryption key as fallback" in {
      val oldAesCrypto = SymmetricCryptoFactory.aesCrypto(configuration(fieldLevelEncryptionEnabled = true).get("fieldLevelEncryption.key"))
      val oldAesEncrypted = oldAesCrypto.encrypt(PlainText("pre-migration-answer"))

      val crypto = new CryptoProviderModule().cryptoInstance(configuration(fieldLevelEncryptionEnabled = true))

      crypto.decrypt(oldAesEncrypted).value mustBe "pre-migration-answer"
    }
  }

  "NoCrypto" should {
    val text = "Not a secret"
    val bytes: Array[Byte] = Array(0x13, 0x37)
    val base64Bytes = new String(Base64.getEncoder.encode(bytes), StandardCharsets.UTF_8)

    "passthrough data on encryption" in {
      NoCrypto.encrypt(PlainText(text)).value mustBe text
      NoCrypto.encrypt(PlainBytes(bytes)).value mustBe base64Bytes
    }

    "passthrough data on decryption" in {
      NoCrypto.decrypt(Crypted(text)).value mustBe text
      NoCrypto.decryptAsBytes(Crypted(base64Bytes)).value mustBe bytes
    }
  }

}
