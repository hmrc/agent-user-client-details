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

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

import java.time.LocalDateTime

class JobDataSpec extends BaseSpec {

  "JobData" should {

    "serialise and deseralise FriendlyNameJobData correctly" in {
      val id: BSONObjectID = BSONObjectID.generate()
      val jobData: JobData = FriendlyNameJobData(
        groupId = "someGroupId",
        enrolmentKeys = Seq("A~B~C", "D~E~F"),
        sendEmailOnCompletion = true,
        agencyName = Some("Perfect Accounts Ltd"),
        email = Some("a@b.com"),
        emailLanguagePreference = Some("en"),
        startTime = LocalDateTime.of(2020, 1, 1, 12, 0, 0),
        finishTime = None,
        _id = Some(id)
      )

      val json = Json.toJson(jobData)
      val parsed: JobData = Json.fromJson[JobData](json).get
      parsed should matchPattern { case x: FriendlyNameJobData =>
      }
      parsed shouldBe jobData
    }

  }
}
