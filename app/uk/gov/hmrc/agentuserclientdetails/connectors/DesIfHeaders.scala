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

package uk.gov.hmrc.agentuserclientdetails.connectors

import play.api.Logging
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class DesIfHeaders @Inject() (appConfig: AppConfig) extends Logging {

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"
  private val Authorization = "Authorization"
  private val SessionId = "X-Session-ID"

  private lazy val desEnvironment: String = appConfig.desEnvironment
  private lazy val desAuthorizationToken: String = appConfig.desAuthToken
  private lazy val ifEnvironment: String = appConfig.ifEnvironment
  private lazy val ifAuthTokenAPI1712: String = appConfig.ifAuthTokenAPI1712
  private lazy val ifAuthTokenAPI1495: String = appConfig.ifAuthTokenAPI1495

  // Note that the implicit header carrier passed-in can in most cases be an empty one.
  // Only when testing locally against stubs we need to have one in order that we may include a session id.
  def outboundHeaders(viaIF: Boolean, apiName: Option[String] = None)(implicit
    hc: HeaderCarrier
  ): Seq[(String, String)] = {

    val baseHeaders = Seq(
      Environment -> s"${if (viaIF) { ifEnvironment }
        else { desEnvironment }}",
      CorrelationId -> UUID.randomUUID().toString
    ) ++ hc.sessionId.toSeq.map { sessionId =>
      SessionId -> sessionId.value
    }

    if (viaIF) {
      apiName.fold(baseHeaders) {
        case "getTrustName"              => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI1495"
        case "GetPptSubscriptionDisplay" => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI1712"
        case _ =>
          logger.warn(s"Could not set $Authorization header for IF API '$apiName'")
          baseHeaders
      }
    } else {
      baseHeaders :+ Authorization -> s"Bearer $desAuthorizationToken"
    }

  }

}
