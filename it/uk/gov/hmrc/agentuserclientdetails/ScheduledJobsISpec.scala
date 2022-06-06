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

package uk.gov.hmrc.agentuserclientdetails

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmtdidentifiers.model.Client
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.services.WorkItemServiceImpl
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem.Succeeded

import scala.concurrent.ExecutionContext.Implicits.global

class ScheduledJobsISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterEach
  with IntegrationPatience
  with MongoSpecSupport
  with MockFactory {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val client1 = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")

  "repository cleanup job" should {
    "clean up the repository periodically" in {
      val app = new GuiceApplicationBuilder().configure(
        "job-scheduling.restart-repo-queue.initialDelaySeconds" -> 0,
        "job-scheduling.restart-repo-queue.intervalSeconds" -> 60,
        "job-scheduling.repo-cleanup.initialDelaySeconds" -> 0,
        "job-scheduling.repo-cleanup.intervalSeconds" -> 2,
        "job-scheduling.log-repo-stats.initialDelaySeconds" -> 0,
        "job-scheduling.log-repo-stats.intervalSeconds" -> 1,
        "agent.cache.enabled" -> false
      ).build()
      lazy val wir = app.injector.instanceOf[FriendlyNameWorkItemRepository]
      lazy val wis = new WorkItemServiceImpl(wir)

      val main = app.injector.instanceOf[AgentUserClientDetailsMain] // starts the scheduled jobs
      wis.removeAll().futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), Succeeded).futureValue
      wis.collectStats.futureValue.values.sum shouldBe 1
      Thread.sleep(5000) // Wait for the scheduled job to be executed
      wis.collectStats.futureValue.values.sum shouldBe 0
    }
  }
}
