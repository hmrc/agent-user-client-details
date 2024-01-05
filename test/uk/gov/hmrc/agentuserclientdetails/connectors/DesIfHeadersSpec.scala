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

package uk.gov.hmrc.agentuserclientdetails.connectors

import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.{AppConfig, AppConfigImpl}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId, SessionId}
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class DesIfHeadersSpec extends BaseSpec with MockFactory {

  "outboundHeaders" should {
    "contain correct headers" when {
      "sessionId and requestId found" in new TestScope {
        mockServicesConfigDesEnvironment(key = "des.environment", configString = "testEnv")
        mockServicesConfigDesEnvironment(key = "des.authorization-token", configString = "testAuthToken")

        val hc: HeaderCarrier = new HeaderCarrier(
          sessionId = Option(SessionId("testSession")),
          requestId = Option(RequestId("testRequestId"))
        )
        val headersMap: Map[String, String] =
          underTest.outboundHeaders(viaIF = false, Some("getAgencyDetails"))(hc).toMap

        headersMap should contain("Authorization" -> "Bearer testAuthToken")
        headersMap should contain("Environment" -> "testEnv")
        headersMap should contain("x-session-id" -> "testSession")
        headersMap should contain("x-request-id" -> "testRequestId")
      }

      "sessionId and requestId not found" in new TestScope {
        mockServicesConfigDesEnvironment(key = "des.environment", configString = "testEnv")
        mockServicesConfigDesEnvironment(key = "des.authorization-token", configString = "testAuthToken")

        val hc: HeaderCarrier = new HeaderCarrier
        val headersMap: Map[String, String] =
          underTest.outboundHeaders(viaIF = false, Some("getAgencyDetails"))(hc).toMap

        headersMap should contain("Authorization" -> "Bearer testAuthToken")
        headersMap should contain("Environment" -> "testEnv")
        headersMap.contains("x-session-id") shouldBe false
        headersMap.contains("x-request-id") shouldBe false
      }
    }
  }

  trait TestScope {
    val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
    val appConfig: AppConfig = new AppConfigImpl(mockServicesConfig)
    val underTest: DesIfHeaders = new DesIfHeaders(appConfig)

    def mockServicesConfigDesEnvironment(key: String, configString: String) =
      (mockServicesConfig.getConfString _)
        .expects(key, *)
        .returning(configString)

  }
}
