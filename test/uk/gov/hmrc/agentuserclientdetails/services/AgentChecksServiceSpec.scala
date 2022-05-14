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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfigImpl
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.model.{Enrolment, Identifier}
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSize, AgentSizeRepository, RecordInserted, RecordUpdated, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import scala.concurrent.duration.{DAYS, Duration}
import scala.concurrent.{ExecutionContext, Future}

class AgentChecksServiceSpec extends BaseSpec {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val refreshdurationConfigKey = "agentsize.refreshduration"

  val arn: Arn = Arn("KARN1234567")
  val groupId = "groupId"

  val refreshDays = 12
  val refreshDuration: Duration = Duration(refreshDays, DAYS)

  "Get AgentSize" when {

    val enrolment1: Enrolment = Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
    val enrolment2: Enrolment = Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
    val enrolment3: Enrolment = Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))
    val enrolment4: Enrolment = Enrolment("HMRC-CGT-PD", "NotYetActivated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))

    trait TestScope {

      val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
      val appconfig = new AppConfigImpl(mockServicesConfig)
      val mockAgentSizeRepository: AgentSizeRepository = mock[AgentSizeRepository]
      val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]

      val agentChecksService: AgentChecksService = new AgentChecksService(appconfig, mockAgentSizeRepository, mockEnrolmentStoreProxyConnector)

      def buildAgentSize(refreshDateTime: LocalDateTime): AgentSize = AgentSize(arn, 50, refreshDateTime)
    }

    "an AgentSize record that was last refreshed within the refresh duration exists" should {

      "return that existing AgentSize record" in new TestScope {
        val lastRefreshedAt: LocalDateTime = LocalDateTime.now().minusDays(refreshDays - 2)
        val agentSize: AgentSize = buildAgentSize(lastRefreshedAt)

        mockAgentSizeRepositoryGet(Some(agentSize))(mockAgentSizeRepository)
        mockServicesConfigGetDuration(mockServicesConfig)

        agentChecksService.getAgentSize(arn).futureValue shouldBe Some(agentSize)
      }
    }

    "an AgentSize record that was last refreshed within the refresh duration does not exist" when {

      "AgentSize record does not exist at all" when {

        "enrolment store proxy returns no group for ARN" should {
          "return None" in new TestScope {
            mockAgentSizeRepositoryGet(None)(mockAgentSizeRepository)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

            agentChecksService.getAgentSize(arn).futureValue shouldBe None
          }
        }

        "enrolment store proxy returns empty list of enrolments" should {
          "have client count 0" in new TestScope {
            mockAgentSizeRepositoryGet(None)(mockAgentSizeRepository)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq.empty)(mockEnrolmentStoreProxyConnector)
            mockAgentSizeRepositoryUpsert(Some(RecordInserted))(mockAgentSizeRepository)

            val agentSize: AgentSize = agentChecksService.getAgentSize(arn).futureValue.get
            agentSize.arn shouldBe arn
            agentSize.clientCount shouldBe 0
          }
        }

        "enrolment store proxy returns non-empty list of enrolments" should {
          "return correct count of enrolments having Activated state" in new TestScope {
            mockAgentSizeRepositoryGet(None)(mockAgentSizeRepository)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq(enrolment1, enrolment2, enrolment3, enrolment4))(mockEnrolmentStoreProxyConnector)
            mockAgentSizeRepositoryUpsert(Some(RecordInserted))(mockAgentSizeRepository)

            val agentSize: AgentSize = agentChecksService.getAgentSize(arn).futureValue.get
            agentSize.arn shouldBe arn
            agentSize.clientCount shouldBe 3
          }
        }

      }

      "AgentSize record exists but outside the refresh duration" when {

        val lastRefreshedAt: LocalDateTime = LocalDateTime.now().minusDays(refreshDays + 2)

        "enrolment store proxy returns no group for ARN" should {
          "return None" in new TestScope {
            mockAgentSizeRepositoryGet(Some(buildAgentSize(lastRefreshedAt)))(mockAgentSizeRepository)
            mockServicesConfigGetDuration(mockServicesConfig)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

            agentChecksService.getAgentSize(arn).futureValue shouldBe None
          }
        }

        "enrolment store proxy returns empty list of enrolments" should {
          "have client count 0" in new TestScope {
            mockAgentSizeRepositoryGet(Some(buildAgentSize(lastRefreshedAt)))(mockAgentSizeRepository)
            mockServicesConfigGetDuration(mockServicesConfig)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq.empty)(mockEnrolmentStoreProxyConnector)
            mockAgentSizeRepositoryUpsert(Some(RecordUpdated))(mockAgentSizeRepository)

            val agentSize: AgentSize = agentChecksService.getAgentSize(arn).futureValue.get
            agentSize.arn shouldBe arn
            agentSize.clientCount shouldBe 0
          }
        }

        "enrolment store proxy returns non-empty list of enrolments" should {
          "return correct count of enrolments having Activated state" in new TestScope {
            mockAgentSizeRepositoryGet(Some(buildAgentSize(lastRefreshedAt)))(mockAgentSizeRepository)
            mockServicesConfigGetDuration(mockServicesConfig)
            enrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq(enrolment1, enrolment2, enrolment3, enrolment4))(mockEnrolmentStoreProxyConnector)
            mockAgentSizeRepositoryUpsert(Some(RecordUpdated))(mockAgentSizeRepository)

            val agentSize: AgentSize = agentChecksService.getAgentSize(arn).futureValue.get
            agentSize.arn shouldBe arn
            agentSize.clientCount shouldBe 3
          }
        }

      }
    }

  }

  private def mockAgentSizeRepositoryGet(maybeAgentSize: Option[AgentSize])(mockAgentSizeRepository: AgentSizeRepository) =
    (mockAgentSizeRepository.get _).expects(arn).returning(Future.successful(maybeAgentSize))

  private def enrolmentStoreProxyConnectorGetPrincipalGroupId(maybeGroupId: Option[String])(mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) =
    (mockEnrolmentStoreProxyConnector.getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext)).expects(arn, *, *)
      .returning(Future.successful(maybeGroupId))

  private def mockServicesConfigGetDuration(mockServicesConfig: ServicesConfig) =
    (mockServicesConfig.getDuration _).expects(refreshdurationConfigKey).returning(refreshDuration)

  private def mockEnrolmentStoreProxyConnectorGetEnrolments(enrolments: Seq[Enrolment])(mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) =
    (mockEnrolmentStoreProxyConnector.getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext)).expects(groupId, *, *)
      .returning(Future.successful(enrolments))

  private def mockAgentSizeRepositoryUpsert(maybeUpsertType: Option[UpsertType])(mockAgentSizeRepository: AgentSizeRepository) =
    (mockAgentSizeRepository.upsert _).expects(*).returning(Future.successful(maybeUpsertType))

}
