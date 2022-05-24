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

package uk.gov.hmrc.agentuserclientdetails.model

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.support._
import uk.gov.hmrc.agentuserclientdetails.util.EnrolmentKey
import uk.gov.hmrc.workitem._

class EnrolmentKeySpec extends AnyWordSpec with Matchers with MockFactory with FakeCache {

  def mkWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
    val now = DateTime.now()
    WorkItem(id = BSONObjectID.generate(), receivedAt = now, updatedAt = now, availableAt = now, status = status, failureCount = 0, item = item)
  }
  "EnrolmentKey" should {
    "generate enrolment keys correctly" in {
      EnrolmentKey.enrolmentKey("HMRC-MTD-IT", "someId") shouldBe "HMRC-MTD-IT~MTDITID~someId"
      EnrolmentKey.enrolmentKey("HMRC-MTD-VAT", "someId") shouldBe "HMRC-MTD-VAT~VRN~someId"
      EnrolmentKey.enrolmentKey("HMRC-TERS-ORG", "someId") shouldBe "HMRC-TERS-ORG~SAUTR~someId"
      EnrolmentKey.enrolmentKey("HMRC-TERSNT-ORG", "someId") shouldBe "HMRC-TERSNT-ORG~URN~someId"
      EnrolmentKey.enrolmentKey("HMRC-CGT-PD", "someId") shouldBe "HMRC-CGT-PD~CGTPDRef~someId"
      EnrolmentKey.enrolmentKey("HMRC-PPT-ORG", "someId") shouldBe "HMRC-PPT-ORG~EtmpRegistrationNumber~someId"
      EnrolmentKey.enrolmentKey("HMRC-PT", "someId") shouldBe "HMRC-PT~NINO~someId"
      an[Exception] shouldBe thrownBy(EnrolmentKey.enrolmentKey("badServiceId", "someId"))
    }
    "deconstruct enrolment keys correctly" in {
      EnrolmentKey.deconstruct("HMRC-MTD-IT~MTDITID~someId") shouldBe ("HMRC-MTD-IT", "someId")
      EnrolmentKey.deconstruct("HMRC-MTD-VAT~VRN~someId") shouldBe ("HMRC-MTD-VAT", "someId")
      EnrolmentKey.deconstruct("HMRC-TERS-ORG~SAUTR~someId") shouldBe ("HMRC-TERS-ORG", "someId")
      EnrolmentKey.deconstruct("HMRC-TERSNT-ORG~URN~someId") shouldBe ("HMRC-TERSNT-ORG", "someId")
      EnrolmentKey.deconstruct("HMRC-CGT-PD~CGTPDRef~someId") shouldBe ("HMRC-CGT-PD", "someId")
      EnrolmentKey.deconstruct("HMRC-PPT-ORG~EtmpRegistrationNumber~someId") shouldBe ("HMRC-PPT-ORG", "someId")
      EnrolmentKey.deconstruct("HMRC-PT~NINO~someId") shouldBe ("HMRC-PT", "someId")
      an[Exception] shouldBe thrownBy(EnrolmentKey.deconstruct("HMRC-FAKE-SVC~NINO~AB123456Z"))
    }

  }
}


