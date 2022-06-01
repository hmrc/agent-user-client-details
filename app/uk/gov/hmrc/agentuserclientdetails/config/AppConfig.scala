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

import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.Duration

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {

  val appName: String = "agent-user-client-details"

  val citizenDetailsBaseUrl: String

  val enrolmentStoreProxyUrl: String

  val desBaseUrl: String
  val desEnvironment: String
  val desAuthToken: String

  val ifPlatformBaseUrl: String
  val ifEnvironment: String
  val ifAuthTokenAPI1712: String
  val ifAuthTokenAPI1495: String

  val enableThrottling: Boolean
  val clientNameFetchThrottlingRate: String
  val es19ThrottlingRate: String

  val workItemRepoAvailableBeforeSeconds: Int
  val workItemRepoFailedBeforeSeconds: Int
  val workItemRepoGiveUpAfterMinutes: Int

  val jobRestartRepoQueueInitialDelaySeconds: Int
  val jobRestartRepoQueueIntervalSeconds: Int
  val jobRepoCleanupIntervalSeconds: Int
  val jobRepoCleanupInitialDelaySeconds: Int
  val jobLogRepoStatsQueueInitialDelaySeconds: Int
  val jobLogRepoStatsQueueIntervalSeconds: Int

  val maxFriendlyNameUpdateBatchSize: Int

  val stubsCompatibilityMode: Boolean

  val agentsizeRefreshDuration: Duration

  val userGroupsSearchUrl: String
}

@Singleton
class AppConfigImpl @Inject()(servicesConfig: ServicesConfig) extends AppConfig {

  private def getConf(key: String) = servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))

  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  lazy val citizenDetailsBaseUrl: String = baseUrl("citizen-details")

  lazy val enrolmentStoreProxyUrl: String = baseUrl("enrolment-store-proxy")

  lazy val desBaseUrl: String = baseUrl("des")
  lazy val desEnvironment: String = getConf("des.environment")
  lazy val desAuthToken: String = getConf("des.authorization-token")

  lazy val ifPlatformBaseUrl: String = baseUrl("if")
  lazy val ifEnvironment: String = getConf("if.environment")
  lazy val ifAuthTokenAPI1712: String = getConf("if.authorization-token.API1712")
  lazy val ifAuthTokenAPI1495: String = getConf("if.authorization-token.API1495")

  lazy val enableThrottling: Boolean = servicesConfig.getBoolean("throttling-rate.enable")
  lazy val clientNameFetchThrottlingRate: String = servicesConfig.getString("throttling-rate.client-name-fetch")
  lazy val es19ThrottlingRate: String = servicesConfig.getString("throttling-rate.es19")

  lazy val workItemRepoAvailableBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.available-before-seconds")
  lazy val workItemRepoFailedBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.failed-before-seconds")
  lazy val workItemRepoGiveUpAfterMinutes: Int = servicesConfig.getInt("work-item-repository.give-up-after-minutes")

  lazy val stubsCompatibilityMode: Boolean = servicesConfig.getBoolean("stubs-compatibility-mode")

  lazy val jobRestartRepoQueueInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.restart-repo-queue.initialDelaySeconds")
  lazy val jobRestartRepoQueueIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.restart-repo-queue.intervalSeconds")
  lazy val jobRepoCleanupInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.repo-cleanup.initialDelaySeconds")
  lazy val jobRepoCleanupIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.repo-cleanup.intervalSeconds")
  lazy val jobLogRepoStatsQueueInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.log-repo-stats.initialDelaySeconds")
  lazy val jobLogRepoStatsQueueIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.log-repo-stats.intervalSeconds")

  lazy val maxFriendlyNameUpdateBatchSize: Int = servicesConfig.getInt("max-friendly-name-update-batch-size")

  lazy val agentsizeRefreshDuration: Duration = servicesConfig.getDuration("agentsize.refreshduration")

  lazy val userGroupsSearchUrl: String = servicesConfig.baseUrl("users-groups-search")
}
