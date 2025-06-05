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
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.agentuserclientdetails.services.ClientNameService.InvalidServiceIdException
import uk.gov.hmrc.agentuserclientdetails.support.FakeCitizenDetailsConnector
import uk.gov.hmrc.agentuserclientdetails.support.FakeDesConnector
import uk.gov.hmrc.agentuserclientdetails.support.FakeHipConnector
import uk.gov.hmrc.agentuserclientdetails.support.FakeIfConnector
import uk.gov.hmrc.agentuserclientdetails.support.TestAppConfig
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class ClientNameServiceSpec
extends AnyWordSpec
with Matchers {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val cns =
    new ClientNameService(
      FakeCitizenDetailsConnector,
      FakeDesConnector,
      FakeIfConnector,
      FakeHipConnector,
      new TestAppConfig {}
    )

  "retrieving the client's friendly name" should {
    "hit the correct endpoint for income tax MAIN" in {
      cns.getClientName("HMRC-MTD-IT~NINO~someId").futureValue shouldBe Some("IT Client")
    }
    "hit the correct endpoint for income tax SUPP" in {
      cns.getClientName("HMRC-MTD-IT-SUPP~NINO~someId").futureValue shouldBe Some("IT Client")
    }
    "check the client details if IF returns None for trading name (main)" in {
      cns.getClientName("HMRC-MTD-IT~NINO~GK873907D").futureValue shouldBe Some("Tom Client")
    }
    "check the client details if IF returns None for trading name (supp)" in {
      cns.getClientName("HMRC-MTD-IT-SUPP~NINO~GK873907D").futureValue shouldBe Some("Tom Client")
    }
    "check the client details if IF returns an empty string for trading name (main)" in {
      cns.getClientName("HMRC-MTD-IT~NINO~GK873908D").futureValue shouldBe Some("Tom Client")
    }
    "check the client details if IF returns an empty string for trading name (supp)" in {
      cns.getClientName("HMRC-MTD-IT-SUPP~NINO~GK873908D").futureValue shouldBe Some("Tom Client")
    }
    "hit the correct endpoint for income record viewer" in {
      cns.getClientName("HMRC-PT~NINO~GK873907D").futureValue shouldBe Some("Tom Client")
    }
    "hit the correct endpoint for VAT" in {
      cns.getClientName("HMRC-MTD-VAT~VRN~someId").futureValue shouldBe Some("VAT Client")
    }
    "hit the correct endpoint for plastic packaging tax (PPT)" in {
      cns.getClientName("HMRC-PPT-ORG~EtmpRegistrationNumber~someId").futureValue shouldBe Some("PPT Client")
    }
    "hit the correct endpoint for capital gains tax (CGT)" in {
      cns.getClientName("HMRC-CGT-PD~CGTPDRef~someId").futureValue shouldBe Some("CGT Client")
    }
    "hit the correct endpoint for Trust" in {
      cns.getClientName("HMRC-TERS-ORG~SAUTR~someId").futureValue shouldBe Some("Trust Client")
    }
    "hit the correct endpoint for non-taxable Trust" in {
      cns.getClientName("HMRC-TERSNT-ORG~URN~someId").futureValue shouldBe Some("Trust Client")
    }
    "For invalid service name" in {
      val caught = intercept[InvalidServiceIdException] {
        await(cns.getClientName("potatoes~idType~someId"))
      }
      caught shouldBe InvalidServiceIdException("potatoes")
    }
  }

}
