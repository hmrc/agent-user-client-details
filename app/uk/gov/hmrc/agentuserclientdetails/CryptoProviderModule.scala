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

package uk.gov.hmrc.agentuserclientdetails

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText, SymmetricCryptoFactory}

import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoProviderModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(bind[Encrypter with Decrypter].toInstance(cryptoInstance(configuration)))

  def cryptoInstance(configuration: Configuration): Encrypter with Decrypter =
    if (configuration.underlying.getBoolean("fieldLevelEncryption.enable"))
      SymmetricCryptoFactory.aesGcmCryptoFromConfig("fieldLevelEncryption", configuration.underlying)
    else
      NoCrypto
}

/** Encrypter/decrypter that does nothing (i.e. leaves content in plaintext). Only to be used for debugging.
  */
trait NoCrypto extends Encrypter with Decrypter {
  def encrypt(plain: PlainContent): Crypted = plain match {
    case PlainText(text)   => Crypted(text)
    case PlainBytes(bytes) => Crypted(new String(Base64.getEncoder.encode(bytes), StandardCharsets.UTF_8))
  }
  def decrypt(notEncrypted: Crypted): PlainText = PlainText(notEncrypted.value)
  def decryptAsBytes(nullEncrypted: Crypted): PlainBytes = PlainBytes(Base64.getDecoder.decode(nullEncrypted.value))
}

object NoCrypto extends NoCrypto