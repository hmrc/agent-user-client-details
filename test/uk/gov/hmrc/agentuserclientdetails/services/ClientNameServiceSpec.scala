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

package uk.gov.hmrc.agentuserclientdetails.services

import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.Service
import uk.gov.hmrc.agentuserclientdetails.services.ClientNameService.InvalidServiceIdException
import uk.gov.hmrc.agentuserclientdetails.support.{FakeCache, FakeCitizenDetailsConnector, FakeDesConnector, FakeIfConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class ClientNameServiceSpec extends AnyWordSpec with Matchers with FakeCache {

  implicit val hc = HeaderCarrier()
  val cns = new ClientNameService(
    FakeCitizenDetailsConnector,
    FakeDesConnector,
    FakeIfConnector,
    agentCacheProvider
  )

  "retrieving the client's friendly name" should {
    "hit the correct endpoint for income tax" in {
      cns.getClientNameByService("someId", Service.MtdIt.id).futureValue shouldBe Some("IT Client")
    }
    "check the client details if des returns None for trading name" in {
      cns.getClientNameByService("GK873907D", Service.MtdIt.id).futureValue shouldBe Some("Tom Client")
    }
    "check the client details if des returns an empty string for trading name" in {
      cns.getClientNameByService("GK873908D", Service.MtdIt.id).futureValue shouldBe Some("Tom Client")
    }
    "hit the correct endpoint for income record viewer" in {
      cns.getClientNameByService("GK873907D", "HMRC-PT").futureValue shouldBe Some("Tom Client")
    }
    "hit the correct endpoint for VAT" in {
      cns.getClientNameByService("someId", Service.Vat.id).futureValue shouldBe Some("VAT Client")
    }
    "hit the correct endpoint for plastic packaging tax (PPT)" in {
      cns.getClientNameByService("someId", Service.Ppt.id).futureValue shouldBe Some("PPT Client")
    }
    "hit the correct endpoint for capital gains tax (CGT)" in {
      cns.getClientNameByService("someId", Service.CapitalGains.id).futureValue shouldBe Some("CGT Client")
    }
    "hit the correct endpoint for Trust" in {
      cns.getClientNameByService("someId", Service.Trust.id).futureValue shouldBe Some("Trust Client")
    }
    "hit the correct endpoint for non-taxable Trust" in {
      cns.getClientNameByService("someId", Service.TrustNT.id).futureValue shouldBe Some("Trust Client")
    }
    "For invalid service name" in {
      val invalidServiceId = "potatoes"
      val caught = intercept[InvalidServiceIdException] {
        await(cns.getClientNameByService("someId", invalidServiceId))
      }
      caught shouldBe InvalidServiceIdException(invalidServiceId)
    }
  }
}
