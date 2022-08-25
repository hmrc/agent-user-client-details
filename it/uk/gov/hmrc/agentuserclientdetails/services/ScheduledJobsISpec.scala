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

package uk.gov.hmrc.agentuserclientdetails.services

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.PlayRunners
import play.api.inject.bind
import uk.gov.hmrc.agentmtdidentifiers.model.Client
import uk.gov.hmrc.agentuserclientdetails.AgentUserClientDetailsMain
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, FriendlyNameJobData, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.JobMonitoringRepository
import uk.gov.hmrc.agentuserclientdetails.util.MongoProvider
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem.Succeeded

import scala.concurrent.ExecutionContext.Implicits.global

class ScheduledJobsISpec
    extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterEach with IntegrationPatience
    with MongoSpecSupport with MockFactory with PlayRunners {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val testEnrolmentKey = "HMRC-MTD-VAT~VRN~101747641"
  val testArn = "BARN9706518"
  val client1 = Client(testEnrolmentKey, "John Innes")

  val configOverrides = Seq( // override config values to reduce delays required to test scheduled jobs
    "job-scheduling.friendly-name.restart-repo-queue.initialDelaySeconds"    -> 0,
    "job-scheduling.friendly-name.restart-repo-queue.intervalSeconds"        -> 60,
    "job-scheduling.friendly-name.repo-cleanup.initialDelaySeconds"          -> 0,
    "job-scheduling.friendly-name.repo-cleanup.intervalSeconds"              -> 2,
    "job-scheduling.friendly-name.log-repo-stats.initialDelaySeconds"        -> 0,
    "job-scheduling.friendly-name.log-repo-stats.intervalSeconds"            -> 1,
    "job-scheduling.assign-enrolment.restart-repo-queue.initialDelaySeconds" -> 0,
    "job-scheduling.assign-enrolment.restart-repo-queue.intervalSeconds"     -> 60,
    "job-scheduling.assign-enrolment.repo-cleanup.initialDelaySeconds"       -> 0,
    "job-scheduling.assign-enrolment.repo-cleanup.intervalSeconds"           -> 2,
    "job-scheduling.assign-enrolment.log-repo-stats.initialDelaySeconds"     -> 0,
    "job-scheduling.assign-enrolment.log-repo-stats.intervalSeconds"         -> 1,
    "job-scheduling.job-monitoring.initialDelaySeconds"                      -> 0,
    "job-scheduling.job-monitoring.intervalSeconds"                          -> 1,
    "agent.cache.enabled"                                                    -> false
  )

  "'friendly name' repository cleanup job" should {
    "clean up the repository periodically" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoProvider].toInstance(MongoProvider(this.mongo)))
      ) { app =>
        lazy val wis = app.injector.instanceOf[FriendlyNameWorkItemService]

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        wis.removeAll().futureValue
        wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), Succeeded).futureValue
        wis.collectStats.futureValue.values.sum shouldBe 1
        Thread.sleep(5000) // Wait for the scheduled job to be executed
        wis.collectStats.futureValue.values.sum shouldBe 0
      }
    }
  }

  "'assign enrolment' repository cleanup job" should {
    "clean up the repository periodically" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoProvider].toInstance(MongoProvider(this.mongo)))
      ) { app =>
        lazy val wis = app.injector.instanceOf[AssignmentsWorkItemService]

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        wis.removeAll().futureValue
        wis
          .pushNew(Seq(AssignmentWorkItem(Assign, testGroupId, testEnrolmentKey, testArn)), DateTime.now(), Succeeded)
          .futureValue
        wis.collectStats.futureValue.values.sum shouldBe 1
        Thread.sleep(5000) // Wait for the scheduled job to be executed
        wis.collectStats.futureValue.values.sum shouldBe 0
      }
    }
  }

  "job monitoring job" should {
    "check job completion periodically and mark as complete accordingly" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoProvider].toInstance(MongoProvider(this.mongo)))
      ) { app =>
        lazy val jmr = app.injector.instanceOf[JobMonitoringRepository]
        lazy val jms = app.injector.instanceOf[JobMonitoringService]

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        jmr.collection.drop(failIfNotFound = false).futureValue
        jms
          .createFriendlyNameFetchJobData(
            FriendlyNameJobData(
              groupId = "myGroupId",
              enrolmentKeys = Seq("HMRC-MTD-VAT~VRN~123456789"),
              sendEmailOnCompletion = false,
              agencyName = None,
              email = None,
              emailLanguagePreference = Some("en")
            )
          )
          .futureValue

        Thread.sleep(5000) // Wait for the scheduled job to be executed

        // The scheduled job should be marked as complete (since there are no outstanding items in the repo that belong to it)

        jms.getNextJobToCheck.futureValue shouldBe empty
      }
    }
  }

}
