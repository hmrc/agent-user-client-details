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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.ThrottleMode
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Throttler {

  def process[A, B](
    itemsToThrottle: Seq[A],
    maxItemsPerSecond: Int
  )(
    operation: A => Future[Seq[B]]
  ): Source[Seq[B], NotUsed] = {

    val throttledFLow = Flow[A].throttle(
      elements = maxItemsPerSecond,
      per = 1.second,
      maximumBurst = 0,
      mode = ThrottleMode.Shaping
    )

    Source
      .fromIterator(() => itemsToThrottle.iterator)
      .via(throttledFLow)
      .map(operation)
      .mapAsync(parallelism = 1)(eventualSeq => eventualSeq)
  }

}
