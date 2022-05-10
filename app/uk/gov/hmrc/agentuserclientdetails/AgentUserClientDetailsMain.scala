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

import akka.actor.ActorSystem

import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.Logging
import uk.gov.hmrc.agentuserclientdetails.services.FriendlyNameWorker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AgentUserClientDetailsMain @Inject()(
                           actorSystem: ActorSystem,
                           lifecycle: ApplicationLifecycle,
                           friendlyNameWorker: FriendlyNameWorker)(implicit val ec: ExecutionContext)
  extends Logging {

  lifecycle.addStopHook(() =>
    Future {
      actorSystem.terminate()
    })

  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 10.second) {
    friendlyNameWorker.running.get() match {
      case true =>
        logger.debug("Friendly name fetching job was already running, so I did not trigger it again.")
      case false =>
        logger.debug("Friendly name fetching job triggered.")
        friendlyNameWorker.start()
    }
  }
}