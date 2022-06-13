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

import play.api.http.Status._
import uk.gov.hmrc.agentuserclientdetails.services.ClientNameService.InvalidServiceIdException
import uk.gov.hmrc.http.UpstreamErrorResponse

object StatusUtil {
  val retryableStatuses =
    Seq(UNAUTHORIZED, TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT)
  def isRetryable(status: Int): Boolean = retryableStatuses.contains(status)
  def isRetryable(e: Throwable): Boolean = e match {
    case uer: UpstreamErrorResponse   => isRetryable(uer.statusCode)
    case _: InvalidServiceIdException => false
    // Expand this list if we identify any other cases to be handled specifically
    case _ => true
    // Any other exception, when the future does not complete successfully and thus there isn't a status code
    // is considered a temporary problem that is potentially retryable.
  }
}
