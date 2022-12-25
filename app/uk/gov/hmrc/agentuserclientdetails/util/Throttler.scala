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

package uk.gov.hmrc.agentuserclientdetails.util

import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, ThrottleMode}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

object Throttler {

  def process[A, B](itemsToThrottle: Seq[A], maxItemsPerSecond: Int)(
    operation: A => Future[Seq[B]]
  )(implicit ec: ExecutionContext, materializer: Materializer): Future[Seq[B]] = {

    val throttledFLow = Flow[A].throttle(
      elements = maxItemsPerSecond,
      per = 1.second,
      maximumBurst = 0,
      mode = ThrottleMode.Shaping
    )

    Source
      .fromIterator(() => itemsToThrottle.iterator)
      .via(throttledFLow)
      .runWith(
        Sink.foldAsync(Seq.empty[B]) { (accumulatedOutputItems, inputItem) =>
          operation(inputItem).map(_ ++ accumulatedOutputItems)
        }
      )
  }

}
