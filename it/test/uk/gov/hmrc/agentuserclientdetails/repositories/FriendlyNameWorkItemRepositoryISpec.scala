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
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates
import com.typesafe.config.Config
import org.bson.types.ObjectId
import org.mongodb.scala.SingleObservableFuture
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.repositories.storagemodel.SensitiveClient
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.services.FriendlyNameWorkItemServiceImpl
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class FriendlyNameWorkItemRepositoryISpec
extends BaseIntegrationSpec
with MongoSupport
with MockFactory {

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

  val sensitiveClient1: SensitiveClient = SensitiveClient(client1)
  val sensitiveClient2: SensitiveClient = SensitiveClient(client2)
  val sensitiveClient3: SensitiveClient = SensitiveClient(client3)
  val sensitiveClient4: SensitiveClient = SensitiveClient(client4)

  val model: FriendlyNameWorkItem = FriendlyNameWorkItem(
    "ID123",
    sensitiveClient1,
    Some("abcedfg-qwerty")
  )

  lazy val mockAuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule =
    new AbstractModule {
      override def configure(): Unit = bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wir.collection.drop().toFuture().futureValue
  }

  def mkWorkItem[A](
    item: A,
    status: ProcessingStatus
  ): WorkItem[A] = {
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
          Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient1), FriendlyNameWorkItem(testGroupId, sensitiveClient2)),
          Instant.now(),
          ToDo
        )
        .futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient3)),
        Instant.now(),
        Succeeded
      ).futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient4)),
        Instant.now(),
        Failed
      ).futureValue
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
          Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient1), FriendlyNameWorkItem(testGroupId, sensitiveClient2)),
          Instant.now(),
          ToDo
        )
        .futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient3)),
        Instant.now(),
        Succeeded
      ).futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient4)),
        Instant.now(),
        Failed
      ).futureValue
      wis.query(testGroupId, Some(Seq(ToDo))).futureValue.length shouldBe 2
      wis.query(testGroupId, Some(Seq(ToDo, Succeeded))).futureValue.length shouldBe 3
      wis.query(testGroupId, Some(Seq(Duplicate, PermanentlyFailed))).futureValue.length shouldBe 0
      wis.query(testGroupId, None).futureValue.length shouldBe 4
      wis.query(badGroupId, None).futureValue.length shouldBe 0
    }
  }

  "cleanup" should {
    "remove any items that are either succeeded or duplicate (if they were last updated earlier than the given cutoff)" in {
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient1)),
        Instant.now(),
        Succeeded
      ).futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient2)),
        Instant.now(),
        Duplicate
      ).futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient3)),
        Instant.now(),
        PermanentlyFailed
      ).futureValue
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient4)),
        Instant.now(),
        Failed
      ).futureValue
      wis.cleanup(Instant.now().plusSeconds(24 * 3600 /* 1 day */ )).futureValue
      wis.query(testGroupId, Some(Seq(Succeeded))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(Duplicate))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(PermanentlyFailed))).futureValue.length shouldBe 1
      wis.query(testGroupId, Some(Seq(Failed))).futureValue.length shouldBe 1
    }
    "not remove an item that was recently updated" in {
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient1)),
          Instant.now(),
          Succeeded
        )
        .futureValue
      wis
        .cleanup(Instant.now().plusSeconds(60))
        .futureValue // item was updated only 1 minute before the cleanup time, so it should be kept
      wis.query(testGroupId, Some(Seq(Succeeded))).futureValue.length shouldBe 1
    }

  }

  "deleteWorkItems" should {
    "delete all work items" in {
      wis.pushNew(
        Seq(FriendlyNameWorkItem(testGroupId, sensitiveClient1)),
        Instant.now(),
        Succeeded
      ).futureValue
      wir.deleteWorkItems(testGroupId).futureValue shouldBe 1L
    }
  }

  "FriendlyNameWorkItemRepository" when {
    "counting unencrypted records" should {

      "provide a total count of records that do not have the encrypted flag" in {

        val workItemDef = mkWorkItem(model, Succeeded)

        wir.collection.insertOne(workItemDef)
          .toFuture()
          .futureValue

        wir.countUnencrypted().futureValue shouldBe 0

        wir.collection
          .updateOne(
            Filters.equal("item.groupId", model.groupId),
            Updates.unset("item.client.encrypted")
          ).toFuture().futureValue

        wir.countUnencrypted().futureValue shouldBe 1
      }
    }

    "encrypting old records" should {

      "find records that do not have the encrypted flag and encrypt them" in {

        val workItemDef = mkWorkItem(model, Succeeded)

        wir.collection.insertOne(workItemDef)
          .toFuture()
          .futureValue

        wir.collection
          .updateOne(
            Filters.equal("item.groupId", model.groupId),
            Updates.unset("item.client.encrypted")
          ).toFuture().futureValue

        val throttleRate = 2
        wir.encryptOldRecords(throttleRate)

        eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
          wir.countUnencrypted().futureValue shouldBe 0
        }
      }
    }
  }

}
