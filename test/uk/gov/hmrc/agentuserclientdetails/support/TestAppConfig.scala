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

package uk.gov.hmrc.agentuserclientdetails.support

import uk.gov.hmrc.agentuserclientdetails.config.AppConfig

import scala.concurrent.duration.{DAYS, Duration}
import scala.util.matching.Regex

class TestAppConfig extends AppConfig {
  val citizenDetailsBaseUrl: String = ""
  val enrolmentStoreProxyUrl: String = ""
  val desBaseUrl: String = ""
  val desEnvironment: String = "desEnv"
  val desAuthToken: String = "desToken"
  val ifPlatformBaseUrl: String = ""
  val ifEnvironment: String = "IFEnv"
  val ifAuthTokenAPI1171: String = "API1171"
  val ifAuthTokenAPI1712: String = "API1712"
  val ifAuthTokenAPI1495: String = "API1495"
  val enableThrottling: Boolean = false
  val clientNameFetchThrottlingRate: String = "1 / second"
  override val es0ThrottlingRate: String = "20 / second"
  val es19ThrottlingRate: String = "20 / second"
  val assignmentsThrottlingRate: String = "20 / second"
  val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int = 0
  val friendlyNameWorkItemRepoFailedBeforeSeconds: Int = 1
  val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int = 1440
  val friendlyNameWorkItemRepoDeleteFinishedItemsAfterSeconds: Int = 900
  val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int = 0
  val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int = 1
  val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int = 1440
  val assignEnrolmentWorkItemRepoDeleteFinishedItemsAfterSeconds: Int = 900
  val jobMonitoringAvailableBeforeSeconds: Int = 0
  val jobMonitoringFailedBeforeSeconds: Int = 1
  val jobMonitoringGiveUpAfterMinutes: Int = 1440
  val stubsCompatibilityMode: Boolean = true
  val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int = 60
  val friendlyNameJobRestartRepoQueueIntervalSeconds: Int = 60
  val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int = 60
  val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int = 60
  val agentsizeRefreshDuration: Duration = Duration(7, DAYS)
  val es3CacheRefreshDuration: Duration = Duration(1, DAYS)
  val userGroupsSearchUrl: String = "http://localhost"
  val maxFriendlyNameUpdateBatchSize: Int = 20
  val emailBaseUrl: String = ""
  val jobMonitoringWorkerIntervalSeconds: Int = 60
  val jobMonitoringWorkerInitialDelaySeconds: Int = 10
  val jobMonitoringDeleteFinishedItemsAfterSeconds: Int = 900
  val serviceJobIntervalSeconds: Int = 60
  val serviceJobInitialDelaySeconds: Int = 60
  override val es3MaxRecordsFetchCount: Int = 1000
  override val enableCbcFeature: Boolean = true
  override val enablePillar2Feature: Boolean = true
  override val internalHostPatterns: Seq[Regex] = Seq("^.*\\.service$", "^.*\\.mdtp$", "^localhost$").map(_.r)
}
