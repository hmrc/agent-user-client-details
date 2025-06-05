/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.http.Status.*
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.services.ClientNameService.InvalidServiceIdException
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

class StatusUtilSpec extends BaseSpec {

  "isRetryable" should {
    
    "return true" when {
      
      "a status code is provided that is on the list of retryable statuses" in {
        StatusUtil.isRetryable(UNAUTHORIZED) shouldBe true
        StatusUtil.isRetryable(TOO_MANY_REQUESTS) shouldBe true
        StatusUtil.isRetryable(INTERNAL_SERVER_ERROR) shouldBe true
        StatusUtil.isRetryable(BAD_GATEWAY) shouldBe true
        StatusUtil.isRetryable(SERVICE_UNAVAILABLE) shouldBe true
        StatusUtil.isRetryable(GATEWAY_TIMEOUT) shouldBe true
      }
      
      "an UpstreamErrorResponse is provided that has a status code on the list of retryable statuses" in {
        StatusUtil.isRetryable(UpstreamErrorResponse("Too many requests!!", TOO_MANY_REQUESTS)) shouldBe true
      }
      
      "an unrecognised exception is provided (assumed temporary issue)" in {
        StatusUtil.isRetryable(HttpException("Umm", INTERNAL_SERVER_ERROR)) shouldBe true
      }
    }
    
    "return false" when {

      "a status code is provided that is not on the list of retryable statuses" in {
        StatusUtil.isRetryable(BAD_REQUEST) shouldBe false
      }

      "an UpstreamErrorResponse is provided that has a status code not on the list of retryable statuses" in {
        StatusUtil.isRetryable(UpstreamErrorResponse("Bad request", BAD_REQUEST)) shouldBe false
      }
      
      "an InvalidServiceIdException is provided" in {
        StatusUtil.isRetryable(InvalidServiceIdException("ABC")) shouldBe false
      }
    }
  }
}
