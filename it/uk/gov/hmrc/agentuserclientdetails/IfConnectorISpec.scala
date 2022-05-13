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

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.PptRef
import uk.gov.hmrc.agentuserclientdetails.connectors.IfConnector
import uk.gov.hmrc.agentuserclientdetails.model.{PptSubscription, TrustResponse}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class IfConnectorISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with GuiceOneServerPerSuite
  with MockFactory {

  lazy val ifConnector = app.injector.instanceOf[IfConnector]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "IfConnector" should {
    "getTrustName" in {
      ifConnector.getTrustName("testId").futureValue should matchPattern {
        case _: TrustResponse =>
          // TODO implement meaningful tests
      }
    }
    "getPptSubscription" in {
      ifConnector.getPptSubscription(PptRef("XAPPT0000012345")).futureValue should matchPattern {
        case _: Option[_] =>
          // TODO implement meaningful tests
      }
    }
  }
}
