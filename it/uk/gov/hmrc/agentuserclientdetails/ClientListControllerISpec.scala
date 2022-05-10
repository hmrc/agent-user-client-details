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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

class ClientListControllerISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with GuiceOneServerPerSuite {

  "/groupid/:groupid/client-list" should {
    "respond with 200 status and a list of enrolments if all of the retrieved enrolments have friendly names" in {
      ???
    }
    "respond with 202 status if any of the retrieved enrolments don't have a friendly name" in {
      ???
    }
    "respond with 200 status if any of the retrieved enrolments don't have a friendly name but they have been tried before and marked as permanently failed" in {
      ???
    }
    "add work items to the repo for any enrolments that don't have a friendly name" in {
      ???
    }
    "don't add work items to the repo if they have been already added" in {
      ???
    }
  }
}
