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

package uk.gov.hmrc.agentuserclientdetails.repositories

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem}
import uk.gov.hmrc.agentuserclientdetails.services.AssignmentsWorkItemServiceImpl
import uk.gov.hmrc.agentuserclientdetails.util.MongoProvider
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem._

import scala.concurrent.ExecutionContext.Implicits.global

class AssignmentsWorkItemRepositoryISpec extends BaseIntegrationSpec with MongoSpecSupport {

  implicit val mongoProvider = MongoProvider(mongo)

  lazy val config = app.injector.instanceOf[Config]
  lazy val wir = AssignmentsWorkItemRepository(config)
  lazy val wis = new AssignmentsWorkItemServiceImpl(wir)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testUserId = "ABCEDEFGI1234568"
  val enrolmentKey1 = "HMRC-MTD-VAT~VRN~101747641"
  val enrolmentKey2 = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
  val enrolmentKey3 = "HMRC-CGT-PD~CgtRef~XMCGTP123456789"
  val enrolmentKey4 = "HMRC-MTD-VAT~VRN~VRN"
  val testArn = Arn("BARN9706518")

  lazy val mockAuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
  }

  def mkWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
    val now = DateTime.now()
    WorkItem(
      id = BSONObjectID.generate(),
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
          Seq(
            AssignmentWorkItem(Assign, testUserId, enrolmentKey1, testArn.value),
            AssignmentWorkItem(Assign, testUserId, enrolmentKey2, testArn.value)
          ),
          DateTime.now(),
          ToDo
        )
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey3, testArn.value)), DateTime.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey4, testArn.value)), DateTime.now(), Failed)
        .futureValue
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
          Seq(
            AssignmentWorkItem(Assign, testUserId, enrolmentKey1, testArn.value),
            AssignmentWorkItem(Assign, testUserId, enrolmentKey2, testArn.value)
          ),
          DateTime.now(),
          ToDo
        )
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey3, testArn.value)), DateTime.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey4, testArn.value)), DateTime.now(), Failed)
        .futureValue
      wis.query(Seq(ToDo)).futureValue.length shouldBe 2
      wis.query(Seq(ToDo, Succeeded)).futureValue.length shouldBe 3
      wis.query(Seq(Duplicate, PermanentlyFailed)).futureValue.length shouldBe 0
    }
  }

  "query by ARN" should {
    "return the correct items based on a query" in {
      wis
        .pushNew(
          Seq(
            AssignmentWorkItem(Assign, testUserId, enrolmentKey1, testArn.value),
            AssignmentWorkItem(Assign, testUserId, enrolmentKey2, testArn.value)
          ),
          DateTime.now(),
          ToDo
        )
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey3, testArn.value)), DateTime.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey4, testArn.value)), DateTime.now(), Failed)
        .futureValue

      wis.queryBy(testArn).futureValue.length shouldBe 4
    }
  }

  "cleanup" should {
    "remove any items that are either succeeded or duplicate" in {
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey1, testArn.value)), DateTime.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey2, testArn.value)), DateTime.now(), Duplicate)
        .futureValue
      wis
        .pushNew(
          Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey3, testArn.value)),
          DateTime.now(),
          PermanentlyFailed
        )
        .futureValue
      wis
        .pushNew(Seq(AssignmentWorkItem(Assign, testUserId, enrolmentKey4, testArn.value)), DateTime.now(), Failed)
        .futureValue
      wis.cleanup().futureValue
      wis.query(Seq(Succeeded)).futureValue.length shouldBe 0
      wis.query(Seq(Duplicate)).futureValue.length shouldBe 0
      wis.query(Seq(PermanentlyFailed)).futureValue.length shouldBe 1
      wis.query(Seq(Failed)).futureValue.length shouldBe 1
    }
  }

}
