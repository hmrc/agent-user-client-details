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

package uk.gov.hmrc.agentuserclientdetails.config

import com.google.inject.ImplementedBy
import play.api.Configuration

import javax.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.Duration
import scala.util.matching.Regex

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {

  val hipEnabled: Boolean // Remove flag and the IF connector in June 2025 when it's live
  val hipBaseUrl: String
  val hipAuthToken: String

  val appName: String = "agent-user-client-details"

  val agentAssuranceBaseUrl: String

  val citizenDetailsBaseUrl: String

  val emailBaseUrl: String

  val enrolmentStoreProxyUrl: String

  val desBaseUrl: String
  val desEnvironment: String
  val desAuthToken: String

  val ifPlatformBaseUrl: String
  val ifEnvironment: String
  val ifAuthTokenAPI1171: String
  val ifAuthTokenAPI1712: String
  val ifAuthTokenAPI1495: String

  val enableThrottling: Boolean
  val es0ThrottlingRate: String
  val es19ThrottlingRate: String
  val assignmentsThrottlingRate: String

  val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int
  val friendlyNameWorkItemRepoFailedBeforeSeconds: Int
  val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int
  val friendlyNameWorkItemRepoDeleteFinishedItemsAfterSeconds: Int

  val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int
  val friendlyNameJobRestartRepoQueueIntervalSeconds: Int

  val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int
  val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int
  val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int
  val assignEnrolmentWorkItemRepoDeleteFinishedItemsAfterSeconds: Int

  val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int
  val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int

  val jobMonitoringWorkerIntervalSeconds: Int
  val jobMonitoringWorkerInitialDelaySeconds: Int

  val jobMonitoringAvailableBeforeSeconds: Int
  val jobMonitoringFailedBeforeSeconds: Int
  val jobMonitoringGiveUpAfterMinutes: Int

  val jobMonitoringDeleteFinishedItemsAfterSeconds: Int

  val serviceJobIntervalSeconds: Int
  val serviceJobInitialDelaySeconds: Int

  val maxFriendlyNameUpdateBatchSize: Int

  val stubsCompatibilityMode: Boolean

  val agentsizeRefreshDuration: Duration

  val es3CacheRefreshDuration: Duration

  val userGroupsSearchUrl: String

  val es3MaxRecordsFetchCount: Int

  val enableCbcFeature: Boolean

  val enablePillar2Feature: Boolean

  val internalHostPatterns: Seq[Regex]

}

@Singleton
class AppConfigImpl @Inject() (
  servicesConfig: ServicesConfig,
  config: Configuration
)
extends AppConfig {

  val hipEnabled: Boolean = servicesConfig.getBoolean("features.hip-enabled")
  val hipBaseUrl: String = servicesConfig.baseUrl("hip")
  val hipAuthToken: String = getConf("hip.authorization-token")

  private def getConf(key: String) = servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))

  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")

  val agentAssuranceBaseUrl: String = baseUrl("agent-assurance")

  val emailBaseUrl: String = baseUrl("email")

  val enrolmentStoreProxyUrl: String = baseUrl("enrolment-store-proxy")

  val desBaseUrl: String = baseUrl("des")
  val desEnvironment: String = getConf("des.environment")
  val desAuthToken: String = getConf("des.authorization-token")

  val ifPlatformBaseUrl: String = baseUrl("if")
  val ifEnvironment: String = getConf("if.environment")
  val ifAuthTokenAPI1171: String = getConf("if.authorization-token.API1171")
  val ifAuthTokenAPI1712: String = getConf("if.authorization-token.API1712")
  val ifAuthTokenAPI1495: String = getConf("if.authorization-token.API1495")

  val enableThrottling: Boolean = servicesConfig.getBoolean("throttling-rate.enable")
  val es0ThrottlingRate: String = servicesConfig.getString("throttling-rate.es0")
  val es19ThrottlingRate: String = servicesConfig.getString("throttling-rate.es19")
  val assignmentsThrottlingRate: String = servicesConfig.getString("throttling-rate.assignments")

  val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.friendly-name.available-before-seconds")
  val friendlyNameWorkItemRepoFailedBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.friendly-name.failed-before-seconds")
  val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int = servicesConfig.getInt("work-item-repository.friendly-name.give-up-after-minutes")
  val friendlyNameWorkItemRepoDeleteFinishedItemsAfterSeconds: Int = servicesConfig.getInt(
    "work-item-repository.friendly-name.delete-finished-items-after-seconds"
  )

  val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.assignments.available-before-seconds")
  val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.assignments.failed-before-seconds")
  val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int = servicesConfig.getInt("work-item-repository.assignments.give-up-after-minutes")
  val assignEnrolmentWorkItemRepoDeleteFinishedItemsAfterSeconds: Int = servicesConfig.getInt(
    "work-item-repository.assignments.delete-finished-items-after-seconds"
  )

  val stubsCompatibilityMode: Boolean = servicesConfig.getBoolean("stubs-compatibility-mode")

  val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.initialDelaySeconds")
  val friendlyNameJobRestartRepoQueueIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.intervalSeconds")

  val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int = servicesConfig.getInt(
    "job-scheduling.assign-enrolment.restart-repo-queue.initialDelaySeconds"
  )
  val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.assign-enrolment.restart-repo-queue.intervalSeconds")

  val jobMonitoringWorkerIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.job-monitoring.initialDelaySeconds")
  val jobMonitoringWorkerInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.job-monitoring.intervalSeconds")

  val serviceJobInitialDelaySeconds: Int = servicesConfig.getInt("job-scheduling.service-job.initialDelaySeconds")
  val serviceJobIntervalSeconds: Int = servicesConfig.getInt("job-scheduling.service-job.intervalSeconds")

  val jobMonitoringAvailableBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.job-monitoring.available-before-seconds")
  val jobMonitoringFailedBeforeSeconds: Int = servicesConfig.getInt("work-item-repository.job-monitoring.failed-before-seconds")
  val jobMonitoringGiveUpAfterMinutes: Int = servicesConfig.getInt("work-item-repository.job-monitoring.give-up-after-minutes")
  val jobMonitoringDeleteFinishedItemsAfterSeconds: Int = servicesConfig.getInt("work-item-repository.job-monitoring.delete-finished-items-after-seconds")

  val maxFriendlyNameUpdateBatchSize: Int = servicesConfig.getInt("max-friendly-name-update-batch-size")

  val agentsizeRefreshDuration: Duration = servicesConfig.getDuration("agentsize.refreshduration")

  val es3CacheRefreshDuration: Duration = servicesConfig.getDuration("es3Cache.refreshduration")

  val userGroupsSearchUrl: String = servicesConfig.baseUrl("users-groups-search")
  val es3MaxRecordsFetchCount: Int = servicesConfig.getInt("es3.max-records-fetch-count")

  val enableCbcFeature: Boolean = servicesConfig.getBoolean("features.enable-cbc")
  val enablePillar2Feature: Boolean = servicesConfig.getBoolean("features.enable-pillar2")

  val internalHostPatterns: Seq[Regex] = config.get[Seq[String]]("internalServiceHostPatterns").map(_.r)

}
