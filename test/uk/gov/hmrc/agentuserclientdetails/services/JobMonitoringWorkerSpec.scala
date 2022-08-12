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

import com.mongodb.client.result.UpdateResult
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Client
import uk.gov.hmrc.agentuserclientdetails.connectors.EmailConnector
import uk.gov.hmrc.agentuserclientdetails.model.{EmailInformation, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.{FriendlyNameJobData, JobMonitoringRepository}
import uk.gov.hmrc.agentuserclientdetails.support._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.workitem._

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class JobMonitoringWorkerSpec extends AnyWordSpec with Matchers with MockFactory with FakeCache {

  val groupId = "myGroupId"
  val client1 = Client("HMRC-MTD-VAT~VRN~000000001", "Frank Wright")
  val client2 = Client("HMRC-MTD-VAT~VRN~000000002", "Howell & Son")

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

  "processItem" should {
    val jobId = BSONObjectID.generate()
    val jobData: FriendlyNameJobData = FriendlyNameJobData(
      groupId = groupId,
      enrolmentKeys = Seq("HMRC-MTD-VAT~VRN~000000001", "HMRC-MTD-VAT~VRN~000000002"),
      sendEmailOnCompletion = true,
      agencyName = Some("Perfect Accounts Ltd"),
      email = Some("a@b.com"),
      emailLanguagePreference = Some("en"),
      startTime = LocalDateTime.of(2020, 1, 1, 12, 0, 0),
      finishTime = None,
      _id = Some(jobId)
    )

    "mark the job as completed and send the email when the job is completed and sending email is enabled" in {
      val email = stub[EmailConnector]
      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val jobRepo = stub[JobMonitoringRepository]
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .when(*, *)
        .returns(Future.successful(UpdateResult.acknowledged(1, 1, null)))
      val fnwis = stub[FriendlyNameWorkItemService]
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(Failed, ToDo)), *, *)
        .returns(
          Future.successful(Seq.empty) // No outstanding items
        )
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(PermanentlyFailed)), *, *)
        .returns(Future.successful(Seq.empty)) // no permanently failed items

      val jmw = new JobMonitoringWorker(jobRepo, fnwis, email)
      jmw.processItem(jobData).futureValue

      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .verify(argThat((ei: EmailInformation) => ei.templateId == "agent_permissions_success"), *, *)
        .once()
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .verify(jobId, *)
        .once()
    }

    "mark the job as completed but don't send the email when the job is completed and sending email is disabled" in {
      val email = stub[EmailConnector]
      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val jobRepo = stub[JobMonitoringRepository]
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .when(*, *)
        .returns(Future.successful(UpdateResult.acknowledged(1, 1, null)))
      val fnwis = stub[FriendlyNameWorkItemService]
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(Failed, ToDo)), *, *)
        .returns(
          Future.successful(Seq.empty) // No outstanding items
        )

      val jmw = new JobMonitoringWorker(jobRepo, fnwis, email)
      jmw.processItem(jobData.copy(sendEmailOnCompletion = false)).futureValue

      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .verify(*, *, *)
        .never()
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .verify(jobId, *)
        .once()
    }

    "don't mark the job as completed and don't send the email when the job is NOT completed" in {
      val email = stub[EmailConnector]
      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val jobRepo = stub[JobMonitoringRepository]
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .when(*, *)
        .returns(Future.successful(UpdateResult.acknowledged(1, 1, null)))
      val fnwis = stub[FriendlyNameWorkItemService]
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(Failed, ToDo)), *, *)
        .returns(
          Future.successful(
            Seq(
              mkWorkItem(FriendlyNameWorkItem(groupId, client2), ToDo) // There is one item still to do
            )
          )
        )

      val jmw = new JobMonitoringWorker(jobRepo, fnwis, email)
      jmw.processItem(jobData).futureValue

      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .verify(*, *, *)
        .never()
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .verify(jobId, *)
        .never()
    }

    "mark the job as completed and send the 'partial failure' email when there have been any failures" in {
      val email = stub[EmailConnector]
      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .when(*, *, *)
        .returns(Future.successful(true))
      val jobRepo = stub[JobMonitoringRepository]
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .when(*, *)
        .returns(Future.successful(UpdateResult.acknowledged(1, 1, null)))
      val fnwis = stub[FriendlyNameWorkItemService]
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(Failed, ToDo)), *, *)
        .returns(
          Future.successful(Seq.empty) // No outstanding items
        )
      (fnwis
        .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
        .when(groupId, Some(Seq(PermanentlyFailed)), *, *)
        .returns(
          Future.successful(
            Seq(mkWorkItem(FriendlyNameWorkItem(groupId, client2), PermanentlyFailed))
          ) // one permanently failed item
        )

      val jmw = new JobMonitoringWorker(jobRepo, fnwis, email)
      jmw.processItem(jobData).futureValue

      (email
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .verify(argThat((ei: EmailInformation) => ei.templateId == "agent_permissions_some_failed"), *, *)
        .once()
      (jobRepo
        .markAsFinishedFriendlyNameFetchJobData(_: BSONObjectID, _: LocalDateTime))
        .verify(jobId, *)
        .once()
    }

  }
}
