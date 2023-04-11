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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.crypto.Sensitive.SensitiveString

class Es3CacheSpec extends AnyWordSpec with Matchers {

  val clients = (1 to 15).map(i => SensitiveEnrolment("", "", SensitiveString(i + " name"), Nil))

  "split()" should {

    "return Es3Cache when clients are empty" in {
      val cache = Es3Cache("whatever", Nil)
      Es3Cache.split(cache, 20) shouldBe Seq(cache)
    }

    "return a sequence of Es3Caches when client count is greater than split group size parameter" in {
      val cache = Es3Cache("whatever", clients)
      Es3Cache.split(cache, 10).toList shouldBe Seq(
        cache.copy(clients = clients.take(10)),
        cache.copy(clients = clients.takeRight(5))
      )
    }
  }
  "merge()" should {

    "return a single Es3Cache by merging multiple caches" in {
      val cache = Es3Cache("whatever", Nil)

      val caches = Seq(
        cache.copy(clients = clients.take(10)),
        cache.copy(clients = clients.takeRight(5))
      )
      Es3Cache.merge(caches).get.clients.size shouldBe clients.size
      Es3Cache.merge(caches).get.clients shouldBe clients
    }
  }

}
