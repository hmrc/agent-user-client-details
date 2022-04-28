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

package uk.gov.hmrc.agentuserclientdetails.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig) {

  private def getConf(key: String) =
    servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))
  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  val appName: String = "agent-user-client-details"

  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")

  val enrolmentStoreProxyUrl = servicesConfig.baseUrl("enrolment-store-proxy")

  val desBaseUrl: String = baseUrl("des")
  val desEnvironment: String = getConf("des.environment")
  val desAuthToken: String = getConf("des.authorization-token")

  val ifPlatformBaseUrl: String = baseUrl("if")
  val ifEnvironment: String = getConf("if.environment")
  val ifAuthTokenAPI1712: String = getConf("if.authorization-token.API1712")
  val ifAuthTokenAPI1495: String = getConf("if.authorization-token.API1495")

  val desIFEnabled: Boolean = servicesConfig.getBoolean("des-if.enabled")

  val clientNameFetchThrottlingRate: String = servicesConfig.getString("throttling-rate.client-name-fetch")
  val es19ThrottlingRate: String = servicesConfig.getString("throttling-rate.es19")
}
