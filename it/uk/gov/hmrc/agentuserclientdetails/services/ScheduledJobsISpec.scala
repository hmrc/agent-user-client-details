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

package uk.gov.hmrc.agentuserclientdetails.services

import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.bind
import play.api.test.PlayRunners
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.AgentUserClientDetailsMain
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, FriendlyNameJobData, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.JobMonitoringRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ScheduledJobsISpec
    extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterEach with IntegrationPatience
    with MongoSupport with MockFactory with PlayRunners {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val testEnrolmentKey = "HMRC-MTD-VAT~VRN~101747641"
  val testArn = "BARN9706518"
  val client1 = Client(testEnrolmentKey, "John Innes")
  lazy val mockAuthConnector = mock[AuthConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropDatabase()
  }

  val configOverrides = Seq( // override config values to reduce delays required to test scheduled jobs
    "job-scheduling.friendly-name.restart-repo-queue.initialDelaySeconds"     -> 0,
    "job-scheduling.friendly-name.restart-repo-queue.intervalSeconds"         -> 60,
    "job-scheduling.service-job.initialDelaySeconds"                          -> 0,
    "job-scheduling.service-job.intervalSeconds"                              -> 2,
    "job-scheduling.assign-enrolment.restart-repo-queue.initialDelaySeconds"  -> 0,
    "job-scheduling.assign-enrolment.restart-repo-queue.intervalSeconds"      -> 60,
    "job-scheduling.job-monitoring.initialDelaySeconds"                       -> 0,
    "job-scheduling.job-monitoring.intervalSeconds"                           -> 1,
    "work-item-repository.friendly-name.delete-finished-items-after-seconds"  -> 0,
    "work-item-repository.assignments.delete-finished-items-after-seconds"    -> 0,
    "work-item-repository.job-monitoring.delete-finished-items-after-seconds" -> 0,
    "agent.cache.enabled"                                                     -> false
  )

  "'friendly name' repository cleanup job" should {
    "clean up the repository periodically" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      ) { app =>
        lazy val wis = app.injector.instanceOf[FriendlyNameWorkItemService]

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        wis.removeAll().futureValue
        wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), Succeeded).futureValue
        wis.collectStats.futureValue.values.sum shouldBe 1
        // Wait for the scheduled job to be executed
        eventually(Timeout(Span(10, Seconds))) {
          wis.collectStats.futureValue.values.sum shouldBe 0
        }
      }
    }
  }

  "'assign enrolment' repository cleanup job" should {
    "clean up the repository periodically" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      ) { app =>
        lazy val wis = app.injector.instanceOf[AssignmentsWorkItemService]

        wis.removeAll().futureValue
        wis
          .pushNew(Seq(AssignmentWorkItem(Assign, testGroupId, testEnrolmentKey, testArn)), Instant.now(), Succeeded)
          .futureValue
        wis.collectStats.futureValue.values.sum shouldBe 1

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs

        eventually(Timeout(Span(10, Seconds))) {
          wis.collectStats.futureValue.values.sum shouldBe 0
        }
      }
    }
  }

  "job monitoring job" should {
    "check job completion periodically and mark as complete accordingly" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      ) { app =>
        lazy val jmr = app.injector.instanceOf[JobMonitoringRepository]
        lazy val jms = app.injector.instanceOf[JobMonitoringService]

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        jmr.collection.drop().toFuture().futureValue
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

    "clean up the repository periodically" in {
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      ) { app =>
        lazy val jmr = app.injector.instanceOf[JobMonitoringRepository]
        lazy val jms = app.injector.instanceOf[JobMonitoringService]

        jmr.collection.drop().toFuture().futureValue
        val objectId = jms
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

        jms.markAsFinished(objectId).futureValue
        jmr.metrics.futureValue.values.sum shouldBe 1

        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        // Wait for the scheduled job to be executed
        eventually(Timeout(Span(10, Seconds))) {
          jmr.metrics.futureValue.values.sum shouldBe 0
        }

      }
    }
  }

  "failed jobs" should {
    // to be fair this is mostly to placate the test coverage checks
    "be recovered so they do not hinder other jobs" in {
      val stubAwis = stub[AssignmentsWorkItemService]
      (stubAwis.collectStats(_: ExecutionContext)).when(*).returns(Future.failed(new RuntimeException("foo")))
      (stubAwis.cleanup(_: Instant)(_: ExecutionContext)).when(*, *).returns(Future.failed(new RuntimeException("foo")))
      val stubFwis = stub[FriendlyNameWorkItemService]
      (stubFwis.collectStats(_: ExecutionContext)).when(*).returns(Future.failed(new RuntimeException("bar")))
      (stubFwis.cleanup(_: Instant)(_: ExecutionContext)).when(*, *).returns(Future.failed(new RuntimeException("bar")))
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
          .overrides(bind[AssignmentsWorkItemService].toInstance(stubAwis))
          .overrides(bind[FriendlyNameWorkItemService].toInstance(stubFwis))
      ) { app =>
        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        Thread.sleep(5000)
        (stubAwis.collectStats(_: ExecutionContext)).verify(*).atLeastOnce()
        (stubAwis.cleanup(_: Instant)(_: ExecutionContext)).verify(*, *).atLeastOnce()
        (stubFwis.collectStats(_: ExecutionContext)).verify(*).atLeastOnce()
        (stubFwis.cleanup(_: Instant)(_: ExecutionContext)).verify(*, *).atLeastOnce()
      }
    }
  }

  "jobs that are already running" should {
    // to be fair this is mostly to placate the test coverage checks
    "not be triggered again" in {
      val stubAw = stub[AssignmentsWorker]
      (stubAw.isRunning _).when().returns(true)
      (stubAw.start _).when().returns(Future.successful(()))
      val stubFnw = stub[FriendlyNameWorker]
      (stubFnw.isRunning _).when().returns(true)
      (stubFnw.start _).when().returns(Future.successful(()))
      running(
        _.configure(configOverrides: _*)
          .overrides(bind[MongoComponent].toInstance(mongoComponent))
          .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
          .overrides(bind[AssignmentsWorker].toInstance(stubAw))
          .overrides(bind[FriendlyNameWorker].toInstance(stubFnw))
      ) { app =>
        val _ = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
        Thread.sleep(5000)
        (stubAw.start _).verify().never()
        (stubFnw.start _).verify().never()
      }
    }
  }

}
