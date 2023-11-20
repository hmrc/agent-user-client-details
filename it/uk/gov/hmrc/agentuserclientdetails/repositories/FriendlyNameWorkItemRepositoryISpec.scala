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

package uk.gov.hmrc.agentuserclientdetails.repositories

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import org.bson.types.ObjectId
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.services.FriendlyNameWorkItemServiceImpl
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class FriendlyNameWorkItemRepositoryISpec extends BaseIntegrationSpec with MongoSupport {

  lazy val config = app.injector.instanceOf[Config]
  lazy val appConfig = app.injector.instanceOf[AppConfig]
  lazy val wir = FriendlyNameWorkItemRepository(config, mongoComponent)
  lazy val wis = new FriendlyNameWorkItemServiceImpl(wir, appConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val client1 = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
  val client2 = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
  val client3 = Client("HMRC-CGT-PD~CgtRef~XMCGTP123456789", "George Candy")
  val client4 = Client("HMRC-MTD-VAT~VRN~VRN", "Ross Barker")

  lazy val mockAuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wir.collection.drop().toFuture().futureValue
  }

  def mkWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
    val now = Instant.now()
    WorkItem(
      id = ObjectId.get(),
      receivedAt = now,
      updatedAt = now,
      availableAt = now,
      status = status,
      failureCount = 0,
      item = item
    )
  }

  "collectStats" should {
    "collect the correct statistics about work items in the repository" in {
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, client1), FriendlyNameWorkItem(testGroupId, client2)),
          Instant.now(),
          ToDo
        )
        .futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), Instant.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), Instant.now(), Failed).futureValue
      val stats = wis.collectStats.futureValue
      stats.get(ToDo.name) shouldBe Some(2)
      stats.get(Succeeded.name) shouldBe Some(1)
      stats.get(Failed.name) shouldBe Some(1)
      stats.get(PermanentlyFailed.name) shouldBe None
    }
  }

  "query" should {
    "return the correct items based on a query" in {
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, client1), FriendlyNameWorkItem(testGroupId, client2)),
          Instant.now(),
          ToDo
        )
        .futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), Instant.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), Instant.now(), Failed).futureValue
      wis.query(testGroupId, Some(Seq(ToDo))).futureValue.length shouldBe 2
      wis.query(testGroupId, Some(Seq(ToDo, Succeeded))).futureValue.length shouldBe 3
      wis.query(testGroupId, Some(Seq(Duplicate, PermanentlyFailed))).futureValue.length shouldBe 0
      wis.query(testGroupId, None).futureValue.length shouldBe 4
      wis.query(badGroupId, None).futureValue.length shouldBe 0
    }
  }

  "cleanup" should {
    "remove any items that are either succeeded or duplicate (if they were last updated earlier than the given cutoff)" in {
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client2)), Instant.now(), Duplicate).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), Instant.now(), PermanentlyFailed).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), Instant.now(), Failed).futureValue
      wis.cleanup(Instant.now().plusSeconds(24 * 3600 /* 1 day */ )).futureValue
      wis.query(testGroupId, Some(Seq(Succeeded))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(Duplicate))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(PermanentlyFailed))).futureValue.length shouldBe 1
      wis.query(testGroupId, Some(Seq(Failed))).futureValue.length shouldBe 1
    }
    "not remove an item that was recently updated" in {
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), Succeeded)
        .futureValue
      wis
        .cleanup(Instant.now().plusSeconds(60))
        .futureValue // item was updated only 1 minute before the cleanup time, so it should be kept
      wis.query(testGroupId, Some(Seq(Succeeded))).futureValue.length shouldBe 1
    }

  }

}
