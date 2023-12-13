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
  lazy val ifAuthTokenAPI1171: String = getConf("if.authorization-token.API1171")
  lazy val ifAuthTokenAPI1712: String = getConf("if.authorization-token.API1712")
  lazy val ifAuthTokenAPI1495: String = getConf("if.authorization-token.API1495")

  lazy val enableThrottling: Boolean = servicesConfig.getBoolean("throttling-rate.enable")
  lazy val es0ThrottlingRate: String = servicesConfig.getString("throttling-rate.es0")
  lazy val es19ThrottlingRate: String = servicesConfig.getString("throttling-rate.es19")
  lazy val assignmentsThrottlingRate: String = servicesConfig.getString("throttling-rate.assignments")

  lazy val friendlyNameWorkItemRepoAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.available-before-seconds")
  lazy val friendlyNameWorkItemRepoFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.failed-before-seconds")
  lazy val friendlyNameWorkItemRepoGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.give-up-after-minutes")
  lazy val friendlyNameWorkItemRepoDeleteFinishedItemsAfterSeconds: Int =
    servicesConfig.getInt("work-item-repository.friendly-name.delete-finished-items-after-seconds")

  lazy val assignEnrolmentWorkItemRepoAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.assignments.available-before-seconds")
  lazy val assignEnrolmentWorkItemRepoFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.assignments.failed-before-seconds")
  lazy val assignEnrolmentWorkItemRepoGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.assignments.give-up-after-minutes")
  lazy val assignEnrolmentWorkItemRepoDeleteFinishedItemsAfterSeconds: Int =
    servicesConfig.getInt("work-item-repository.assignments.delete-finished-items-after-seconds")

  lazy val stubsCompatibilityMode: Boolean = servicesConfig.getBoolean("stubs-compatibility-mode")

  lazy val friendlyNameJobRestartRepoQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.initialDelaySeconds")
  lazy val friendlyNameJobRestartRepoQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.friendly-name.restart-repo-queue.intervalSeconds")

  lazy val assignEnrolmentJobRestartRepoQueueInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.restart-repo-queue.initialDelaySeconds")
  lazy val assignEnrolmentJobRestartRepoQueueIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.assign-enrolment.restart-repo-queue.intervalSeconds")

  lazy val jobMonitoringWorkerIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.job-monitoring.initialDelaySeconds")
  lazy val jobMonitoringWorkerInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.job-monitoring.intervalSeconds")

  lazy val serviceJobInitialDelaySeconds: Int =
    servicesConfig.getInt("job-scheduling.service-job.initialDelaySeconds")
  lazy val serviceJobIntervalSeconds: Int =
    servicesConfig.getInt("job-scheduling.service-job.intervalSeconds")

  lazy val jobMonitoringAvailableBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.available-before-seconds")
  lazy val jobMonitoringFailedBeforeSeconds: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.failed-before-seconds")
  lazy val jobMonitoringGiveUpAfterMinutes: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.give-up-after-minutes")
  lazy val jobMonitoringDeleteFinishedItemsAfterSeconds: Int =
    servicesConfig.getInt("work-item-repository.job-monitoring.delete-finished-items-after-seconds")

  lazy val maxFriendlyNameUpdateBatchSize: Int = servicesConfig.getInt("max-friendly-name-update-batch-size")

  lazy val agentsizeRefreshDuration: Duration = servicesConfig.getDuration("agentsize.refreshduration")

  lazy val es3CacheRefreshDuration: Duration = servicesConfig.getDuration("es3Cache.refreshduration")

  lazy val userGroupsSearchUrl: String = servicesConfig.baseUrl("users-groups-search")
  override lazy val es3MaxRecordsFetchCount: Int = servicesConfig.getInt("es3.max-records-fetch-count")

  override lazy val enableCbcFeature: Boolean = servicesConfig.getBoolean("features.enable-cbc")
  override lazy val enablePillar2Feature: Boolean = servicesConfig.getBoolean("features.enable-pillar2")
}
