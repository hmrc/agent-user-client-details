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

  def emailBaseUrl: String

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
  val assignmentsThrottlingRate: String

  val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int
  val friendlyNameWorkItemRepoFailedBeforeSeconds: Int
  val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int

  val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int
  val friendlyNameJobRestartRepoQueueIntervalSeconds: Int
  val friendlyNameJobRepoCleanupIntervalSeconds: Int
  val friendlyNameJobRepoCleanupInitialDelaySeconds: Int
  val friendlyNameJobLogRepoStatsQueueInitialDelaySeconds: Int
  val friendlyNameJobLogRepoStatsQueueIntervalSeconds: Int

  val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int
  val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int
  val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int

  val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int
  val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int
  val assignEnrolmentJobRepoCleanupIntervalSeconds: Int
  val assignEnrolmentJobRepoCleanupInitialDelaySeconds: Int
  val assignEnrolmentJobLogRepoStatsQueueInitialDelaySeconds: Int
  val assignEnrolmentJobLogRepoStatsQueueIntervalSeconds: Int

  val jobMonitoringWorkerIntervalSeconds: Int
  val jobMonitoringWorkerInitialDelaySeconds: Int

  val jobMonitoringAvailableBeforeSeconds: Int
  val jobMonitoringFailedBeforeSeconds: Int
  val jobMonitoringGiveUpAfterMinutes: Int

  val maxFriendlyNameUpdateBatchSize: Int

  val stubsCompatibilityMode: Boolean

  val agentsizeRefreshDuration: Duration

  val userGroupsSearchUrl: String
}

@Singleton
class AppConfigImpl @Inject() (servicesConfig: ServicesConfig) extends AppConfig {

  private def getConf(key: String) =
    servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))

  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  lazy val citizenDetailsBaseUrl: String = baseUrl("citizen-details")

  override lazy val emailBaseUrl: String = baseUrl("email")

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
  lazy val assignmentsThrottlingRate: String = servicesConfig.getString("throttling-rate.assignments")

  lazy val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.available-before-seconds")
  lazy val friendlyNameWorkItemRepoFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.failed-before-seconds")
  lazy val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.give-up-after-minutes")

  lazy val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.assignments.available-before-seconds")
  lazy val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.assignments.failed-before-seconds")
  lazy val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.assignments.give-up-after-minutes")

  lazy val stubsCompatibilityMode: Boolean = servicesConfig.getBoolean("stubs-compatibility-mode")

  lazy val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.initialDelaySeconds")
  lazy val friendlyNameJobRestartRepoQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.intervalSeconds")
  lazy val friendlyNameJobRepoCleanupInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.repo-cleanup.initialDelaySeconds")
  lazy val friendlyNameJobRepoCleanupIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.repo-cleanup.intervalSeconds")
  lazy val friendlyNameJobLogRepoStatsQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.log-repo-stats.initialDelaySeconds")
  lazy val friendlyNameJobLogRepoStatsQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.log-repo-stats.intervalSeconds")

  lazy val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.restart-repo-queue.initialDelaySeconds")
  lazy val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.restart-repo-queue.intervalSeconds")
  lazy val assignEnrolmentJobRepoCleanupInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.repo-cleanup.initialDelaySeconds")
  lazy val assignEnrolmentJobRepoCleanupIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.repo-cleanup.intervalSeconds")
  lazy val assignEnrolmentJobLogRepoStatsQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.log-repo-stats.initialDelaySeconds")
  lazy val assignEnrolmentJobLogRepoStatsQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.log-repo-stats.intervalSeconds")

  lazy val jobMonitoringWorkerIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.job-monitoring.initialDelaySeconds")
  lazy val jobMonitoringWorkerInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.job-monitoring.intervalSeconds")

  val jobMonitoringAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.available-before-seconds")
  val jobMonitoringFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.failed-before-seconds")
  val jobMonitoringGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.give-up-after-minutes")

  lazy val maxFriendlyNameUpdateBatchSize: Int = servicesConfig.getInt("max-friendly-name-update-batch-size")

  lazy val agentsizeRefreshDuration: Duration = servicesConfig.getDuration("agentsize.refreshduration")

  lazy val userGroupsSearchUrl: String = servicesConfig.baseUrl("users-groups-search")
}
