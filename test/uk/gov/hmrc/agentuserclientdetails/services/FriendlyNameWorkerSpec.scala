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

import akka.actor.ActorSystem
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, FriendlyNameWorkItem, Identifier}
import uk.gov.hmrc.agentuserclientdetails.support._
import uk.gov.hmrc.clusterworkthrottling.ServiceInstances
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.workitem.{Duplicate, Failed, PermanentlyFailed, ProcessingStatus, ResultStatus, Succeeded, ToDo, WorkItem}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class FriendlyNameWorkerSpec extends AnyWordSpec with Matchers with MockFactory with FakeCache {

  val testGroupId = "2K6H-N1C1-7M7V-O4A3"

  val mockSi: ServiceInstances = null // very hard to mock this class due to exceptions when the constructor gets called

  val mockCnsOK: ClientNameService = new ClientNameService(FakeCitizenDetailsConnector, FakeDesConnector, FakeIfConnector, agentCacheProvider)
  val mockCnsFail: ClientNameService = new ClientNameService(FailingCitizenDetailsConnector(429), FailingDesConnector(429), FailingIfConnector(429), agentCacheProvider)
  val mockCnsNoName: ClientNameService = new ClientNameService(NotFoundCitizenDetailsConnector, NotFoundDesConnector, NotFoundIfConnector, agentCacheProvider)


  val mockActorSystem: ActorSystem = stub[ActorSystem]
  val appConfig: AppConfig = new TestAppConfig() {
    override val enableThrottling: Boolean = false
  }

  def mkWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
    val now = DateTime.now()
    WorkItem(id = BSONObjectID.generate(), receivedAt = now, updatedAt = now, availableAt = now, status = status, failureCount = 0, item = item)
  }
  "processItem" should {
    "retrieve the friendly name and store it via ES19 and mark the item as succeeded when everything is successful" in {
      val stubWis: WorkItemService = stub[WorkItemService]
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *).returns(Future.successful(()))

      val fnWorker = new FriendlyNameWorker(stubWis, mockEsp, mockSi, mockCnsOK, mockActorSystem, appConfig)
      val workItem = mkWorkItem(FriendlyNameWorkItem(testGroupId, Enrolment("HMRC-MTD-VAT", "Activated", "", Seq(Identifier("VRN", "12345678")))), ToDo)
      fnWorker.processItem(workItem).futureValue

      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testGroupId, *, argThat((_: String).nonEmpty), *, *).once()
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Succeeded, *).once()
    }

    "mark the work item as permanently failed if the call to DES/IF is successful but no name is available" in {
      val stubWis: WorkItemService = stub[WorkItemService]
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *).returns(Future.successful(()))

      val fnWorker = new FriendlyNameWorker(stubWis, mockEsp, mockSi, mockCnsNoName, mockActorSystem, appConfig)
      val workItem = mkWorkItem(FriendlyNameWorkItem(testGroupId, Enrolment("HMRC-MTD-VAT", "Activated", "", Seq(Identifier("VRN", "12345678")))), ToDo)
      fnWorker.processItem(workItem).futureValue

      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testGroupId, *, *, *, *).never()
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, PermanentlyFailed, *).once()
    }

    "mark the work item as failed if the call to DES/IF fails" in {
      val stubWis: WorkItemService = stub[WorkItemService]
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(testGroupId, *, *, *, *).returns(Future.successful(()))

      val fnWorker = new FriendlyNameWorker(stubWis, mockEsp, mockSi, mockCnsFail, mockActorSystem, appConfig)
      val workItem = mkWorkItem(FriendlyNameWorkItem(testGroupId, Enrolment("HMRC-MTD-VAT", "Activated", "", Seq(Identifier("VRN", "12345678")))), ToDo)
      fnWorker.processItem(workItem).futureValue

      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testGroupId, *, *, *, *).never()
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Failed, *).once()
    }

    "when the ES19 storage call fails mark the old item as duplicate and create a new item with the friendly name already populated" in {
      val stubWis: WorkItemService = stub[WorkItemService]
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis.pushNew(_: Seq[FriendlyNameWorkItem], _: DateTime, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *).returns(Future.failed(UpstreamErrorResponse("", 429)))

      val fnWorker = new FriendlyNameWorker(stubWis, mockEsp, mockSi, mockCnsOK, mockActorSystem, appConfig)
      val workItem = mkWorkItem(FriendlyNameWorkItem(testGroupId, Enrolment("HMRC-MTD-VAT", "Activated", "", Seq(Identifier("VRN", "12345678")))), ToDo)
      fnWorker.processItem(workItem).futureValue

      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Duplicate, *).once()
      (stubWis.pushNew(_: Seq[FriendlyNameWorkItem], _: DateTime, _: ProcessingStatus)(_: ExecutionContext))
        .verify(argThat((_: Seq[FriendlyNameWorkItem]).head.enrolment.friendlyName.nonEmpty), *, ToDo, *).once()
    }

    "when encountering a work item with an already populated friendly name, should store it via ES19 without querying the name again" in {
      val stubWis: WorkItemService = stub[WorkItemService]
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *, *).returns(Future.successful(()))

      val fnWorker = new FriendlyNameWorker(stubWis, mockEsp, mockSi, mockCnsOK, mockActorSystem, appConfig)
      val workItem = mkWorkItem(FriendlyNameWorkItem(testGroupId, Enrolment("HMRC-MTD-VAT", "Activated", "Friendly Name", Seq(Identifier("VRN", "12345678")))), ToDo)
      fnWorker.processItem(workItem).futureValue

      (mockEsp.updateEnrolmentFriendlyName(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testGroupId, *, "Friendly Name", *, *).once()
      (stubWis.complete(_: BSONObjectID, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Succeeded, *).once()
    }

  }
}


