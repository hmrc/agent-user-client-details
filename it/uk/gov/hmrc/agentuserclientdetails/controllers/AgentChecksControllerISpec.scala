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

import com.google.inject.AbstractModule
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, Identifier}
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSize, AgentSizeRepository, AgentSizeRepositoryImpl}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.{ExecutionContext, Future}

class AgentChecksControllerISpec extends BaseIntegrationSpec with DefaultPlayMongoRepositorySupport[AgentSize] {

  val arn = "TARN0000001"
  val groupId = "groupId"

  val enrolment1: Enrolment = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2: Enrolment = Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
  val enrolment3: Enrolment = Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))
  val enrolment4: Enrolment = Enrolment("HMRC-CGT-PD", "NotYetActivated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit = {
      bind(classOf[AgentSizeRepository]).toInstance(repository.asInstanceOf[AgentSizeRepository])
      bind(classOf[EnrolmentStoreProxyConnector]).toInstance(mockEnrolmentStoreProxyConnector)
    }
  }

  trait TestScope {
    lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
    lazy val baseUrl = s"http://localhost:$port"

    lazy val urlGetAgentSize = s"$baseUrl/agent-user-client-details/arn/$arn/agent-size"
  }

  "Agent Size" when {

    "ES proxy connector returns group and delegated enrolments of ARN" should {

      s"return $OK with correct count of enrolments having Activated state" in new TestScope {
        mockPrincipalGroupIdResponseWithoutException(Some(groupId))
        mockEnrolmentsForGroupIdResponseWithoutException(Seq(enrolment1, enrolment2, enrolment3, enrolment4))

        val response: WSResponse = wsClient.url(urlGetAgentSize).get().futureValue

        response.status shouldBe OK
        extractAgentSizeFrom(response.body) shouldBe 3
      }
    }

    "ES proxy connector returns no group of ARN" should {

      s"return $NOT_FOUND" in new TestScope {
        mockPrincipalGroupIdResponseWithoutException(None)

        wsClient.url(urlGetAgentSize).get().futureValue
          .status shouldBe NOT_FOUND
      }
    }

    s"ES proxy connector throws upstream error $NOT_FOUND while getting group of ARN" should {

      s"return $NOT_FOUND" in new TestScope {
        mockPrincipalGroupIdResponseWithException(UpstreamErrorResponse("some problem", 404, 404))

        wsClient.url(urlGetAgentSize).get().futureValue
          .status shouldBe NOT_FOUND
      }
    }

    s"ES proxy connector throws upstream error $UNAUTHORIZED while getting group of ARN" should {

      s"return $UNAUTHORIZED" in new TestScope {
        mockPrincipalGroupIdResponseWithException(UpstreamErrorResponse("some problem", 401, 401))

        wsClient.url(urlGetAgentSize).get().futureValue
          .status shouldBe UNAUTHORIZED
      }
    }

    "ES proxy connector returns group but empty list of delegated enrolments of ARN" should {

      s"return $OK with no enrolments having Activated state" in new TestScope {
        mockPrincipalGroupIdResponseWithoutException(Some(groupId))
        mockEnrolmentsForGroupIdResponseWithoutException(Seq.empty)

        val response: WSResponse = wsClient.url(urlGetAgentSize).get().futureValue

        response.status shouldBe OK
        extractAgentSizeFrom(response.body) shouldBe 0
      }
    }

    "ES proxy connector throws upstream error 5xx while getting list of delegated enrolments of ARN" should {

      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockPrincipalGroupIdResponseWithoutException(Some(groupId))
        mockEnrolmentsForGroupIdResponseWithException(UpstreamErrorResponse("some problem", 502, 502))

        wsClient.url(urlGetAgentSize).get().futureValue
          .status shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  private def extractAgentSizeFrom(body: String) = (Json.parse(body) \ "agent-size").get.as[Int]

  override protected def repository: PlayMongoRepository[AgentSize] = new AgentSizeRepositoryImpl(mongoComponent)

  private def mockPrincipalGroupIdResponseWithoutException(maybeGroupId: Option[String]) =
    (mockEnrolmentStoreProxyConnector.getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(Arn(arn), *, *)
      .returning(Future.successful(maybeGroupId))

  private def mockPrincipalGroupIdResponseWithException(exception: Exception) =
    (mockEnrolmentStoreProxyConnector.getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(Arn(arn), *, *)
      .throwing(exception)

  private def mockEnrolmentsForGroupIdResponseWithoutException(enrolments: Seq[Enrolment]) =
    (mockEnrolmentStoreProxyConnector.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
    .expects(groupId, *, *)
    .returning(Future.successful(enrolments))

  private def mockEnrolmentsForGroupIdResponseWithException(exception: Exception) =
    (mockEnrolmentStoreProxyConnector.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(groupId, *, *)
      .throwing(exception)

}
