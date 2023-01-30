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

import akka.actor.ActorSystem
import akka.stream.testkit.scaladsl.TestSink
import org.scalamock.handlers.{CallHandler3, CallHandler4}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.support.TestAppConfig
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AssignedUsersServiceSpec extends BaseSpec {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val actorSystem: ActorSystem = ActorSystem("AssignedUsersServiceSpec")

  "Calculate assigned users" should {

    "return non-empty list of clients" in new TestScope {

      mockEs3CacheManagerGetCachedClients(
        Seq(
          Client(vatEnrolment, ""),
          Client(pptenrolment, ""),
          Client(cgtEnrolment, "")
        )
      )

      mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(vatEnrolment, Seq("abcA01", "abcA02"))
      mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(pptenrolment, Seq("abcA03"))
      mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(cgtEnrolment, Seq("abcA01"))

      whenReady(assignedUsersService.calculateClientsWithAssignedUsers(groupId), Timeout(Span(10, Seconds))) {
        _.runWith(TestSink[Seq[AssignedClient]])
          .request(3)
          .expectNext(
            List(AssignedClient(vatEnrolment, None, "abcA01"), AssignedClient(vatEnrolment, None, "abcA02")),
            List(AssignedClient(pptenrolment, None, "abcA03")),
            List(AssignedClient(cgtEnrolment, None, "abcA01"))
          )
          .expectComplete()
      }
    }

    trait TestScope {
      val groupId = "2K6H-N1C1-7M7V-O4A3"

      val vatEnrolment = "HMRC-MTD-VAT~VRN~101747641"
      val pptenrolment = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val cgtEnrolment = "HMRC-CGT-PD~CgtRef~XMCGTP123456789"

      val mockEs3CacheManager: Es3CacheManager = mock[Es3CacheManager]
      val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]

      val assignedUsersService: AssignedUsersService =
        new AssignedUsersServiceImpl(
          mockEs3CacheManager,
          mockEnrolmentStoreProxyConnector,
          new TestAppConfig()
        )

      def mockEnrolmentStoreProxyConnectorGetUsersAssignedToEnrolment(
        enrolment: String,
        userIds: Seq[String]
      ): CallHandler4[String, String, HeaderCarrier, ExecutionContext, Future[Seq[String]]] =
        (mockEnrolmentStoreProxyConnector
          .getUsersAssignedToEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(enrolment, *, *, *)
          .returning(Future successful userIds)

      def mockEs3CacheManagerGetCachedClients(
        clients: Seq[Client]
      ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Seq[Client]]] =
        (mockEs3CacheManager
          .getCachedClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(groupId, *, *)
          .returning(Future successful clients)
    }

  }

}
