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

package uk.gov.hmrc.agentuserclientdetails.util

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

import java.time.Duration
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

class ThrottlerSpec
extends BaseSpec {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

  "Throttled processing" should {

    "take at least enough time" in {
      val count = 50

      val inputInts = (1 to count).toList

      val startedAt = Instant.now()

      whenReady(
        Throttler
          .process(itemsToThrottle = inputInts, maxItemsPerSecond = 50)((n: Int) => Future successful Seq(n))
          .runWith(Sink.seq),
        Timeout(Span(5, Seconds))
      ) { _ =>
        val endedAt = Instant.now()

        val oneSecond = 1000

        assert(Duration.between(startedAt, endedAt).toMillis > oneSecond)
      }
    }
  }

}
