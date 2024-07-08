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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.bson.types.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, Unassign}
import uk.gov.hmrc.agentuserclientdetails.support._
import uk.gov.hmrc.clusterworkthrottling.ServiceInstances
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, ResultStatus, WorkItem}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AssignmentsWorkerSpec extends AnyWordSpec with Matchers with MockFactory with FakeCache {

  val testUserId = "ABCEDEFGI1234568"
  val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"
  val testArn = "BARN9706518"
  val mockSi: ServiceInstances = null // very hard to mock this class due to exceptions when the constructor gets called

  val mockActorSystem: ActorSystem = stub[ActorSystem]
  val appConfig: AppConfig = new TestAppConfig() {
    override val enableThrottling: Boolean = false
  }

  val materializer: Materializer = NoMaterializer

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
  "processItem (ES11)" should {
    "make a call to ES11 and mark the item as succeeded when call succeeds" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Assign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testUserId, testEnrolmentKey, *, *)
        .once()
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Succeeded, *)
        .once()
    }

    "when the ES11 call fails with a retryable failure such as a 429 status, mark the item as failed" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 429)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Assign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Failed, *)
        .once()
    }

    "when the ES11 call fails with a non-response exception, mark the item as failed (retryable)" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(new RuntimeException("Unknown error!")))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Assign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Failed, *)
        .once()
    }

    "when the ES11 call fails with a non-retryable failure, mark the item as permanently failed" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 404)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Assign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, PermanentlyFailed, *)
        .once()
    }

    "mark the work item as permanently failed if it is determined that we should give up" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .assignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 500)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem =
        mkWorkItem(AssignmentWorkItem(Assign, testUserId, testEnrolmentKey, testArn), ToDo)
          .copy(receivedAt = Instant.now().minusSeconds(2 * 24 * 3600 /* 2 days */ ))
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, PermanentlyFailed, *)
        .once()
    }
  }

  "processItem (ES12)" should {
    "make a call to ES12 and mark the item as succeeded when call succeeds" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Unassign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .verify(testUserId, testEnrolmentKey, *, *)
        .once()
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Succeeded, *)
        .once()
    }

    "when the ES12 call fails with a retryable failure such as a 429 status, mark the item as failed" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 429)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Unassign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Failed, *)
        .once()
    }

    "when the ES12 call fails with a non-response exception, mark the item as failed (retryable)" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(new RuntimeException("Unknown error!")))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Unassign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, Failed, *)
        .once()
    }

    "when the ES12 call fails with a non-retryable failure, mark the item as permanently failed" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      (stubWis
        .pushNew(_: Seq[AssignmentWorkItem], _: Instant, _: ProcessingStatus)(_: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.successful(()))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 404)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem = mkWorkItem(AssignmentWorkItem(Unassign, testUserId, testEnrolmentKey, testArn), ToDo)
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, PermanentlyFailed, *)
        .once()
    }

    "mark the work item as permanently failed if it is determined that we should give up" in {
      val stubWis: AssignmentsWorkItemService = stub[AssignmentsWorkItemService]
      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val mockEsp: EnrolmentStoreProxyConnector = stub[EnrolmentStoreProxyConnector]
      (mockEsp
        .unassignEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *, *)
        .returns(Future.failed(UpstreamErrorResponse("", 500)))

      val worker = new AssignmentsWorker(stubWis, mockEsp, mockSi, mockActorSystem, appConfig, materializer)
      val workItem =
        mkWorkItem(AssignmentWorkItem(Unassign, testUserId, testEnrolmentKey, testArn), ToDo)
          .copy(receivedAt = Instant.now().minusSeconds(2 * 24 * 3600 /* 2 days */ ))
      worker.processItem(workItem).futureValue

      (stubWis
        .complete(_: ObjectId, _: ProcessingStatus with ResultStatus)(_: ExecutionContext))
        .verify(workItem.id, PermanentlyFailed, *)
        .once()
    }
  }
}
