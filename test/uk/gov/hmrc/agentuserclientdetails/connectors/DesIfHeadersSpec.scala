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

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.support.TestAppConfig
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.http.RequestId
import uk.gov.hmrc.http.SessionId

import java.util.UUID

class DesIfHeadersSpec
extends BaseSpec
with MockFactory {

  "headersConfig" should {
    "contain correct headers for DES" when {
      "service URL is internal" in new TestScope {

        val desUrl = "http://localhost:9009/registration/personal-details/arn/HARN000123"

        implicit val hc: HeaderCarrier =
          new HeaderCarrier(
            authorization = Some(Authorization("Bearer session-xyz")),
            sessionId = Option(SessionId("testSession")),
            requestId = Option(RequestId("testRequestId"))
          )

        val headersConfig: HeadersConfig = underTest.headersConfig(
          viaIF = false,
          desUrl,
          "getAgencyDetails"
        )

        val explicitHeaders: Map[String, String] = headersConfig.explicitHeaders.toMap
        val headerCarrier: HeaderCarrier = headersConfig.hc

        explicitHeaders should contain key "CorrelationId"
        UUID.fromString(explicitHeaders("CorrelationId")) should not be null
        explicitHeaders should contain("Environment" -> "desEnv")
        explicitHeaders should not contain (HeaderNames.authorisation)
        explicitHeaders should not contain (HeaderNames.xSessionId)
        explicitHeaders should not contain (HeaderNames.xRequestId)

        headerCarrier.sessionId.get.value shouldBe "testSession"
        headerCarrier.requestId.get.value shouldBe "testRequestId"
        headerCarrier.authorization.get.value shouldBe "Bearer desToken"
      }

      "service url is external" in new TestScope {

        val desUrl = "https://des.ws.ibt.hmrc.gov.uk/registration/personal-details/arn/HARN000123"

        implicit val hc: HeaderCarrier =
          new HeaderCarrier(
            authorization = Some(Authorization("Bearer session-xyz")),
            sessionId = Option(SessionId("testSession")),
            requestId = Option(RequestId("testRequestId"))
          )

        val headersConfig: HeadersConfig = underTest.headersConfig(
          viaIF = false,
          desUrl,
          "getAgencyDetails"
        )

        val explicitHeaders: Map[String, String] = headersConfig.explicitHeaders.toMap
        val headerCarrier: HeaderCarrier = headersConfig.hc

        explicitHeaders should contain key "CorrelationId"
        UUID.fromString(explicitHeaders("CorrelationId")) should not be null
        explicitHeaders should contain("Environment" -> "desEnv")
        explicitHeaders should contain(HeaderNames.authorisation -> "Bearer desToken")
        explicitHeaders should contain(HeaderNames.xSessionId -> "testSession")
        explicitHeaders should contain(HeaderNames.xRequestId -> "testRequestId")

        headerCarrier.authorization.get.value shouldBe "Bearer session-xyz"

      }
    }

    "contain correct headers for IF" when {
      "service URL is internal" in new TestScope {

        val ifUrl = "http://localhost:9009/trust-known-fact-check/UTR/1234567890"

        implicit val hc: HeaderCarrier =
          new HeaderCarrier(
            authorization = Some(Authorization("Bearer session-xyz")),
            sessionId = Option(SessionId("testSession")),
            requestId = Option(RequestId("testRequestId"))
          )

        val headersConfig: HeadersConfig = underTest.headersConfig(
          viaIF = true,
          ifUrl,
          "getTrustName"
        )

        val explicitHeaders: Map[String, String] = headersConfig.explicitHeaders.toMap
        val headerCarrier: HeaderCarrier = headersConfig.hc

        explicitHeaders should contain key "CorrelationId"
        UUID.fromString(explicitHeaders("CorrelationId")) should not be null
        explicitHeaders should contain("Environment" -> "IFEnv")
        explicitHeaders should not contain (HeaderNames.authorisation)
        explicitHeaders should not contain (HeaderNames.xSessionId)
        explicitHeaders should not contain (HeaderNames.xRequestId)

        headerCarrier.sessionId.get.value shouldBe "testSession"
        headerCarrier.requestId.get.value shouldBe "testRequestId"
        headerCarrier.authorization.get.value shouldBe "Bearer API1495"
      }

      "service url is external" in new TestScope {

        val ifUrl = "https://ifs.ws.ibt.hmrc.gov.uk/trust-known-fact-check/UTR/1234567890"

        implicit val hc: HeaderCarrier =
          new HeaderCarrier(
            authorization = Some(Authorization("Bearer session-xyz")),
            sessionId = Option(SessionId("testSession")),
            requestId = Option(RequestId("testRequestId"))
          )

        val headersConfig: HeadersConfig = underTest.headersConfig(
          viaIF = true,
          ifUrl,
          "getTrustName"
        )

        val explicitHeaders: Map[String, String] = headersConfig.explicitHeaders.toMap
        val headerCarrier: HeaderCarrier = headersConfig.hc

        explicitHeaders should contain key "CorrelationId"
        UUID.fromString(explicitHeaders("CorrelationId")) should not be null
        explicitHeaders should contain("Environment" -> "IFEnv")
        explicitHeaders should contain(HeaderNames.authorisation -> "Bearer API1495")
        explicitHeaders should contain(HeaderNames.xSessionId -> "testSession")
        explicitHeaders should contain(HeaderNames.xRequestId -> "testRequestId")

        headerCarrier.authorization.get.value shouldBe "Bearer session-xyz"

      }

    }

    "throw an exception for IF requests" when {
      "apiName is not recognised" in new TestScope {

        val ifUrl = "http://localhost:9009/trust-known-fact-check/UTR/1234567890"

        implicit val hc: HeaderCarrier = new HeaderCarrier()

        an[RuntimeException] shouldBe thrownBy {
          underTest.headersConfig(
            viaIF = true,
            ifUrl,
            "unknown_API"
          )
        }
      }
    }
  }

  trait TestScope {

    val appConfig: AppConfig = new TestAppConfig
    val underTest: DesIfHeaders = new DesIfHeaders(appConfig)

  }

}
