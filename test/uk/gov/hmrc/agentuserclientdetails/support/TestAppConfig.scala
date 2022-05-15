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

package uk.gov.hmrc.agentuserclientdetails.support

import uk.gov.hmrc.agentuserclientdetails.config.AppConfig

import scala.concurrent.duration.{DAYS, Duration}

class TestAppConfig extends AppConfig {
  val citizenDetailsBaseUrl: String = ""
  val enrolmentStoreProxyUrl: String = ""
  val desBaseUrl: String = ""
  val desEnvironment: String = ""
  val desAuthToken: String = ""
  val ifPlatformBaseUrl: String = ""
  val ifEnvironment: String = ""
  val ifAuthTokenAPI1712: String = ""
  val ifAuthTokenAPI1495: String = ""
  val enableThrottling: Boolean = false
  val clientNameFetchThrottlingRate: String = "1 / second"
  val es19ThrottlingRate: String = "1 / second"
  val workItemRepoAvailableBeforeSeconds: Int = 0
  val workItemRepoFailedBeforeSeconds: Int = 1
  val stubsCompatibilityMode: Boolean = true
  val jobRestartRepoQueueInitialDelaySeconds: Int = 60
  val jobRestartRepoQueueIntervalSeconds: Int = 60
  val jobRepoCleanupIntervalSeconds: Int = 300
  val jobRepoCleanupInitialDelaySeconds: Int = 300
  val jobLogRepoStatsQueueInitialDelaySeconds: Int = 60
  val jobLogRepoStatsQueueIntervalSeconds: Int = 60
  override val agentsizeRefreshDuration: Duration = Duration(7, DAYS)
  override val userGroupsSearchUrl: String = ""
}
