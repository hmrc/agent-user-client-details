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

import com.typesafe.config.Config
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Client
import uk.gov.hmrc.agentuserclientdetails.model.FriendlyNameWorkItem
import uk.gov.hmrc.agentuserclientdetails.repositories.FriendlyNameWorkItemRepository
import uk.gov.hmrc.agentuserclientdetails.services.WorkItemServiceImpl
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem._

import scala.concurrent.ExecutionContext.Implicits.global

class FriendlyNameWorkItemRepositoryISpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterEach
  with IntegrationPatience
  with GuiceOneServerPerSuite
  with MongoSpecSupport
  with MockFactory {

  lazy val config = app.injector.instanceOf[Config]
  lazy val wir = FriendlyNameWorkItemRepository(config)
  lazy val wis = new WorkItemServiceImpl(wir)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val client1 = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
  val client2 = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
  val client3 = Client("HMRC-CGT-PD~CgtRef~XMCGTP123456789", "George Candy")
  val client4 = Client("HMRC-MTD-VAT~VRN~VRN", "Ross Barker")

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
  }

  def mkWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
    val now = DateTime.now()
    WorkItem(id = BSONObjectID.generate(), receivedAt = now, updatedAt = now, availableAt = now, status = status, failureCount = 0, item = item)
  }

  "collectStats" should {
    "collect the correct statistics about work items in the repository" in {
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1), FriendlyNameWorkItem(testGroupId, client2)), DateTime.now(), ToDo).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), DateTime.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), DateTime.now(), Failed).futureValue
      val stats = wis.collectStats.futureValue
      stats.get(ToDo.name) shouldBe Some(2)
      stats.get(Succeeded.name) shouldBe Some(1)
      stats.get(Failed.name) shouldBe Some(1)
      stats.get(PermanentlyFailed.name) shouldBe None
    }
  }

  "query" should {
    "return the correct items based on a query" in {
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1), FriendlyNameWorkItem(testGroupId, client2)), DateTime.now(), ToDo).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), DateTime.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), DateTime.now(), Failed).futureValue
      wis.query(testGroupId, Some(Seq(ToDo))).futureValue.length shouldBe 2
      wis.query(testGroupId, Some(Seq(ToDo, Succeeded))).futureValue.length shouldBe 3
      wis.query(testGroupId, Some(Seq(Duplicate, PermanentlyFailed))).futureValue.length shouldBe 0
      wis.query(testGroupId, None).futureValue.length shouldBe 4
      wis.query(badGroupId, None).futureValue.length shouldBe 0
    }
  }

  "cleanup" should {
    "remove any items that are either succeeded or duplicate" in {
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), Succeeded).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client2)), DateTime.now(), Duplicate).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), DateTime.now(), PermanentlyFailed).futureValue
      wis.pushNew(Seq(FriendlyNameWorkItem(testGroupId, client4)), DateTime.now(), Failed).futureValue
      wis.cleanup().futureValue
      wis.query(testGroupId, Some(Seq(Succeeded))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(Duplicate))).futureValue.length shouldBe 0
      wis.query(testGroupId, Some(Seq(PermanentlyFailed))).futureValue.length shouldBe 1
      wis.query(testGroupId, Some(Seq(Failed))).futureValue.length shouldBe 1
    }
  }

}
