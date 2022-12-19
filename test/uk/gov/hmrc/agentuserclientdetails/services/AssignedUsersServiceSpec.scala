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

import org.scalamock.handlers.CallHandler4
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AssignedUsersServiceSpec extends BaseSpec {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait TestScope {
    val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]

    val assignedUsersService: AssignedUsersService = new AssignedUsersServiceImpl(mockEnrolmentStoreProxyConnector)

    def mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(
      userIds: Seq[String]
    ): CallHandler4[String, String, HeaderCarrier, ExecutionContext, Future[Seq[String]]] =
      (mockEnrolmentStoreProxyConnector
        .getUsersAssignedToEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future successful userIds)
  }

  "Calculate assigned users" when {

    "input clients list is empty" should {
      "return empty list of clients" in new TestScope {
        assignedUsersService
          .calculateClientsWithAssignedUsers(GroupDelegatedEnrolments(Seq.empty))
          .futureValue shouldBe Seq.empty
      }
    }

    "input clients list is not empty" should {
      "return non-empty list of clients" in new TestScope {
        mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(Seq("abcA01", "abcA02"))

        val groupDelegatedEnrolments: GroupDelegatedEnrolments = GroupDelegatedEnrolments(
          Seq(
            AssignedClient("HMRC-MTD-VAT~VRN~101747641", None, "0"),
            AssignedClient(
              "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345",
              None,
              "000000123321123"
            ),
            AssignedClient("HMRC-CGT-PD~CgtRef~XMCGTP123456789", None, "2")
          )
        )

        assignedUsersService.calculateClientsWithAssignedUsers(groupDelegatedEnrolments).futureValue shouldBe List(
          AssignedClient(
            "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345",
            None,
            "000000123321123"
          ),
          AssignedClient("HMRC-CGT-PD~CgtRef~XMCGTP123456789", None, "abcA01"),
          AssignedClient("HMRC-CGT-PD~CgtRef~XMCGTP123456789", None, "abcA02")
        )
      }
    }

  }

}
