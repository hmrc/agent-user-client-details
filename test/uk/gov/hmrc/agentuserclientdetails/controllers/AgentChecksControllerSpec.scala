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

package uk.gov.hmrc.agentuserclientdetails.controllers

import org.scalamock.handlers.{CallHandler2, CallHandler3}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, UserDetails}
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.repositories.AgentSize
import uk.gov.hmrc.agentuserclientdetails.services.AgentChecksService
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AgentChecksControllerSpec extends BaseSpec {

  private val arn: Arn = Arn("TARN0000001")
  private val clientCount = 5
  private val agentSize: AgentSize = AgentSize(arn, clientCount, LocalDateTime.now())

  trait TestScope {

    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()

    val mockAgentChecksService: AgentChecksService = mock[AgentChecksService]

    val agentChecksController = new AgentChecksController(mockAgentChecksService)

    def mockAgentChecksServiceGetAgentSizeWithoutException(
      maybeAgentSize: Option[AgentSize]
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[AgentSize]]] =
      (mockAgentChecksService
        .getAgentSize(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful maybeAgentSize)

    def mockAgentChecksServiceGetAgentSizeWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[AgentSize]]] =
      (mockAgentChecksService
        .getAgentSize(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)

    def mockAgentChecksServiceUserCheckWithoutException(
      countGroupUsers: Int
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Int]] =
      (mockAgentChecksService
        .userCheck(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful countGroupUsers)

    def mockAgentChecksServiceUserCheckWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Int]] =
      (mockAgentChecksService
        .userCheck(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)

    def mockAgentChecksServiceOutstandingWorkItemsExistWithoutException(
      exist: Boolean
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Boolean]] =
      (mockAgentChecksService
        .outstandingWorkItemsExist(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful exist)

    def mockAgentChecksServiceOutstandingWorkItemsExistWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Boolean]] =
      (mockAgentChecksService
        .outstandingWorkItemsExist(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)

    def mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithoutException(
      exist: Boolean
    ): CallHandler2[Arn, ExecutionContext, Future[Boolean]] =
      (mockAgentChecksService
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future successful exist)

    def mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithException(
      ex: Exception
    ): CallHandler2[Arn, ExecutionContext, Future[Boolean]] =
      (mockAgentChecksService
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future failed ex)

    def mockAgentChecksServiceGetTeamMembersWithoutException(
      teamMembers: Seq[UserDetails]
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Seq[UserDetails]]] =
      (mockAgentChecksService
        .getTeamMembers(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful teamMembers)

    def mockAgentChecksServiceGetTeamMembersWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Seq[UserDetails]]] =
      (mockAgentChecksService
        .getTeamMembers(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)
  }

  "Call to get agent size" when {

    "dependency service returns nothing" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithoutException(None)

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    "dependency service returns a value" should {

      def extractClientCountFrom(body: String) = (Json.parse(body) \ "client-count").get.as[Int]

      s"return $OK with matching client count" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithoutException(Some(agentSize))

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe OK
        extractClientCountFrom(contentAsString(result)) shouldBe clientCount
      }
    }

    s"dependency service throws an upstream error having status $NOT_FOUND" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithException(
          UpstreamErrorResponse("not found message", NOT_FOUND, NOT_FOUND)
        )

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    s"dependency service throws an upstream error having status $UNAUTHORIZED" should {
      s"return $UNAUTHORIZED" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithException(
          UpstreamErrorResponse("unauthorized message", UNAUTHORIZED, UNAUTHORIZED)
        )

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }
    }

    s"dependency service throws a 5xx upstream error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithException(
          UpstreamErrorResponse("backend problem message", BAD_GATEWAY, BAD_GATEWAY)
        )

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"dependency service throws a runtime exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceGetAgentSizeWithException(new RuntimeException("boo boo"))

        val result = agentChecksController.getAgentSize(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "Call for user check" when {

    "more than one users exist in group" should {
      s"return $NO_CONTENT" in new TestScope {
        mockAgentChecksServiceUserCheckWithoutException(2)

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe NO_CONTENT
      }
    }

    "less than two users exist in group" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAgentChecksServiceUserCheckWithoutException(1)

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe FORBIDDEN
      }
    }

    s"dependency service throws an upstream error having status $NOT_FOUND" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceUserCheckWithException(UpstreamErrorResponse("not found message", NOT_FOUND, NOT_FOUND))

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    s"dependency service throws an upstream error having status $UNAUTHORIZED" should {
      s"return $UNAUTHORIZED" in new TestScope {
        mockAgentChecksServiceUserCheckWithException(
          UpstreamErrorResponse("unauthorized message", UNAUTHORIZED, UNAUTHORIZED)
        )

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }
    }

    s"dependency service throws a 5xx upstream error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceUserCheckWithException(
          UpstreamErrorResponse("backend problem message", BAD_GATEWAY, BAD_GATEWAY)
        )

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"dependency service throws a runtime exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceUserCheckWithException(new RuntimeException("boo boo"))

        val result = agentChecksController.userCheck(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "Call to check outstanding work items" when {

    "dependency service indicates outstanding work items exist" should {
      s"return $OK" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithoutException(true)

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe OK
      }
    }

    "dependency service indicates outstanding work items do not exist" should {
      s"return $NO_CONTENT" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithoutException(false)

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe NO_CONTENT
      }
    }

    s"dependency service throws an upstream error having status $NOT_FOUND" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithException(
          UpstreamErrorResponse("not found message", NOT_FOUND, NOT_FOUND)
        )

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    s"dependency service throws an upstream error having status $UNAUTHORIZED" should {
      s"return $UNAUTHORIZED" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithException(
          UpstreamErrorResponse("unauthorized message", UNAUTHORIZED, UNAUTHORIZED)
        )

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }
    }

    s"dependency service throws a 5xx upstream error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithException(
          UpstreamErrorResponse("backend problem message", BAD_GATEWAY, BAD_GATEWAY)
        )

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"dependency service throws a runtime exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceOutstandingWorkItemsExistWithException(new RuntimeException("boo boo"))

        val result = agentChecksController.outstandingWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "Call to check outstanding assignments work items" when {

    "dependency service indicates outstanding work items exist" should {
      s"return $OK" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithoutException(true)

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe OK
      }
    }

    "dependency service indicates outstanding work items do not exist" should {
      s"return $NO_CONTENT" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithoutException(false)

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe NO_CONTENT
      }
    }

    s"dependency service throws an upstream error having status $NOT_FOUND" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithException(
          UpstreamErrorResponse("not found message", NOT_FOUND, NOT_FOUND)
        )

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    s"dependency service throws an upstream error having status $UNAUTHORIZED" should {
      s"return $UNAUTHORIZED" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithException(
          UpstreamErrorResponse("unauthorized message", UNAUTHORIZED, UNAUTHORIZED)
        )

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }
    }

    s"dependency service throws a 5xx upstream error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithException(
          UpstreamErrorResponse("backend problem message", BAD_GATEWAY, BAD_GATEWAY)
        )

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"dependency service throws a runtime exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceOutstandingAssignmentsWorkItemsExistWithException(new RuntimeException("boo boo"))

        val result = agentChecksController.outstandingAssignmentsWorkItemsExist(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "Call to get agent team members" when {

    def extractTeamMemberSize(result: Future[Result]): Int = Json.parse(contentAsString(result)).as[JsArray].value.size

    "dependency service indicates no team members exist" should {
      s"return $OK with zero team members" in new TestScope {
        mockAgentChecksServiceGetTeamMembersWithoutException(Seq.empty)

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe OK
        extractTeamMemberSize(result) shouldBe 0
      }
    }

    "dependency service indicates team members exist" should {
      s"return $OK with non-zero team members" in new TestScope {
        val teamMembers: Seq[UserDetails] = Seq(UserDetails(userId = Some("userId")))
        mockAgentChecksServiceGetTeamMembersWithoutException(teamMembers)

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe OK
        extractTeamMemberSize(result) shouldBe teamMembers.size
      }
    }

    s"dependency service throws an upstream error having status $NOT_FOUND" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAgentChecksServiceGetTeamMembersWithException(
          UpstreamErrorResponse("not found message", NOT_FOUND, NOT_FOUND)
        )

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe NOT_FOUND
      }
    }

    s"dependency service throws an upstream error having status $UNAUTHORIZED" should {
      s"return $UNAUTHORIZED" in new TestScope {
        mockAgentChecksServiceGetTeamMembersWithException(
          UpstreamErrorResponse("unauthorized message", UNAUTHORIZED, UNAUTHORIZED)
        )

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }
    }

    s"dependency service throws a 5xx upstream error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceGetTeamMembersWithException(
          UpstreamErrorResponse("backend problem message", BAD_GATEWAY, BAD_GATEWAY)
        )

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"dependency service throws a runtime exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAgentChecksServiceGetTeamMembersWithException(new RuntimeException("boo boo"))

        val result = agentChecksController.getTeamMembers(arn)(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }

}
