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

import com.google.inject.AbstractModule
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.Configuration
import play.api.Environment
import org.apache.pekko.stream.Materializer
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

abstract class BaseIntegrationSpec
extends AnyWordSpec
with Matchers
with ScalaFutures
with IntegrationPatience
with GuiceOneServerPerSuite
with BeforeAndAfterEach {

  protected lazy val conf: Configuration = GuiceApplicationBuilder().configuration
  protected lazy val env: Environment = GuiceApplicationBuilder().environment

  implicit lazy val materializer: Materializer = app.injector.instanceOf[Materializer]

  implicit lazy val crypto: Encrypter
  & Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x")

  /** Child classes can override per their requirements
    */
  def moduleOverrides: AbstractModule = new AbstractModule {}

  override def fakeApplication(): Application = GuiceApplicationBuilder()
//      .disable[PlayModule]
    .configure("metrics.enabled" -> false)
    .configure("auditing.enabled" -> false)
    .configure("agent.cache.enabled" -> false)
    .overrides(moduleOverrides)
    .build()

}
