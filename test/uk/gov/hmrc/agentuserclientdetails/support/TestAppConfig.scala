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
  val es19ThrottlingRate: String = "20 / second"
  val assignmentsThrottlingRate: String = "20 / second"
  val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int = 0
  val friendlyNameWorkItemRepoFailedBeforeSeconds: Int = 1
  val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int = 1440
  val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int = 0
  val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int = 1
  val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int = 1440
  val stubsCompatibilityMode: Boolean = true
  val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int = 60
  val friendlyNameJobRestartRepoQueueIntervalSeconds: Int = 60
  val friendlyNameJobRepoCleanupIntervalSeconds: Int = 300
  val friendlyNameJobRepoCleanupInitialDelaySeconds: Int = 300
  val friendlyNameJobLogRepoStatsQueueInitialDelaySeconds: Int = 60
  val friendlyNameJobLogRepoStatsQueueIntervalSeconds: Int = 60
  val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int = 60
  val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int = 60
  val assignEnrolmentJobRepoCleanupIntervalSeconds: Int = 300
  val assignEnrolmentJobRepoCleanupInitialDelaySeconds: Int = 300
  val assignEnrolmentJobLogRepoStatsQueueInitialDelaySeconds: Int = 60
  val assignEnrolmentJobLogRepoStatsQueueIntervalSeconds: Int = 60
  val agentsizeRefreshDuration: Duration = Duration(7, DAYS)
  val userGroupsSearchUrl: String = "http://localhost"
  val maxFriendlyNameUpdateBatchSize: Int = 20
}
