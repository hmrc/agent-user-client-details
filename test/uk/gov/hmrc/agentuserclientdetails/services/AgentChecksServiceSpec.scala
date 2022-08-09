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

import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, Enrolment, Identifier, UserDetails}
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfigImpl
import uk.gov.hmrc.agentuserclientdetails.connectors.{EnrolmentStoreProxyConnector, UsersGroupsSearchConnector}
import uk.gov.hmrc.agentuserclientdetails.model.{Assign, AssignmentWorkItem, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.{AgentSize, AgentSizeRepository, RecordInserted, RecordUpdated, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.workitem.ProcessingStatus.processingStatuses
import uk.gov.hmrc.workitem._

import java.time.LocalDateTime
import scala.concurrent.duration.{DAYS, Duration}
import scala.concurrent.{ExecutionContext, Future}

class AgentChecksServiceSpec extends BaseSpec {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val refreshdurationConfigKey = "agentsize.refreshduration"

  val arn: Arn = Arn("KARN1234567")
  val groupId = "groupId"
  val userId = "userId"
  val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"

  val refreshDays = 12
  val refreshDuration: Duration = Duration(refreshDays, DAYS)

  trait TestScope {
    val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
    val appconfig = new AppConfigImpl(mockServicesConfig)
    val mockAgentSizeRepository: AgentSizeRepository = mock[AgentSizeRepository]
    val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
    val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]
    val mockWorkItemService: FriendlyNameWorkItemService = mock[FriendlyNameWorkItemService]
    val mockAssignmentsWorkItemService: AssignmentsWorkItemService = mock[AssignmentsWorkItemService]

    val agentChecksService: AgentChecksService = new AgentChecksService(
      appconfig,
      mockAgentSizeRepository,
      mockEnrolmentStoreProxyConnector,
      mockUsersGroupsSearchConnector,
      mockWorkItemService,
      mockAssignmentsWorkItemService
    )

    def buildAgentSize(refreshDateTime: LocalDateTime): AgentSize = AgentSize(arn, 50, refreshDateTime)

    def buildWorkItem[A](item: A, status: ProcessingStatus): WorkItem[A] = {
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
  }

  "Get AgentSize" when {

    val enrolment1: Enrolment =
      Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
    val enrolment2: Enrolment = Enrolment(
      "HMRC-PPT-ORG",
      "Activated",
      "Frank Wright",
      Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
    )
    val enrolment3: Enrolment =
      Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))
    val enrolment4: Enrolment =
      Enrolment("HMRC-CGT-PD", "NotYetActivated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))

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
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

            agentChecksService.getAgentSize(arn).futureValue shouldBe None
          }
        }

        "enrolment store proxy returns empty list of enrolments" should {
          "have client count 0" in new TestScope {
            mockAgentSizeRepositoryGet(None)(mockAgentSizeRepository)
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
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
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq(enrolment1, enrolment2, enrolment3, enrolment4))(
              mockEnrolmentStoreProxyConnector
            )
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
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

            agentChecksService.getAgentSize(arn).futureValue shouldBe None
          }
        }

        "enrolment store proxy returns empty list of enrolments" should {
          "have client count 0" in new TestScope {
            mockAgentSizeRepositoryGet(Some(buildAgentSize(lastRefreshedAt)))(mockAgentSizeRepository)
            mockServicesConfigGetDuration(mockServicesConfig)
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
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
            mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
            mockEnrolmentStoreProxyConnectorGetEnrolments(Seq(enrolment1, enrolment2, enrolment3, enrolment4))(
              mockEnrolmentStoreProxyConnector
            )
            mockAgentSizeRepositoryUpsert(Some(RecordUpdated))(mockAgentSizeRepository)

            val agentSize: AgentSize = agentChecksService.getAgentSize(arn).futureValue.get
            agentSize.arn shouldBe arn
            agentSize.clientCount shouldBe 3
          }
        }

      }
    }

  }

  "User Check" when {

    "enrolment store proxy returns no group for ARN" should {
      "return nil" in new TestScope {
        mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

        agentChecksService.userCheck(arn).futureValue shouldBe 0
      }
    }

    "enrolment store proxy returns a group for ARN" when {

      "user groups search connector returns empty list of user details" should {
        "return nil" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          mockUsersGroupsSearchConnectorGetGroupUsers(Seq.empty)(mockUsersGroupsSearchConnector)

          agentChecksService.userCheck(arn).futureValue shouldBe 0
        }
      }

      "user groups search connector returns non-empty list of user details" should {
        "return correct count" in new TestScope {
          val seqUserDetails: Seq[UserDetails] = Seq(
            UserDetails(userId = Some("userId1"), credentialRole = Some("Assistant")),
            UserDetails(userId = Some("userId2"), credentialRole = Some("Admin"))
          )

          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          mockUsersGroupsSearchConnectorGetGroupUsers(seqUserDetails)(mockUsersGroupsSearchConnector)

          agentChecksService.userCheck(arn).futureValue shouldBe seqUserDetails.size
        }
      }
    }

  }

  "Work Items exist" when {

    "enrolment store proxy returns no group for ARN" should {
      "return false" in new TestScope {
        mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

        agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe false
      }
    }

    "enrolment store proxy returns a group for ARN" when {
      val client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")

      val outstandingProcessingStatuses: Set[ProcessingStatus] = Set(ToDo, InProgress, Failed, Deferred)
      val nonOutstandingProcessingStatuses: Set[ProcessingStatus] = processingStatuses -- outstandingProcessingStatuses

      "workItemService returns an empty list of work items" should {
        "return false" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          mockWorkItemServiceQuery(Seq.empty)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe false
        }
      }

      s"workItemService returns a list of work items that does not contain any of $outstandingProcessingStatuses" should {
        "return false" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)

          val workItems: Seq[WorkItem[FriendlyNameWorkItem]] =
            nonOutstandingProcessingStatuses.toSeq.map(buildWorkItem(FriendlyNameWorkItem(groupId, client), _))
          mockWorkItemServiceQuery(workItems)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe false
        }
      }

      s"workItemService returns a list of work items that contains a $InProgress" should {
        "return true" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)

          val workItems: Seq[WorkItem[FriendlyNameWorkItem]] = (nonOutstandingProcessingStatuses + InProgress).toSeq
            .map(buildWorkItem(FriendlyNameWorkItem(groupId, client), _))
          mockWorkItemServiceQuery(workItems)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe true
        }
      }

      s"workItemService returns a list of work items that contains a $ToDo" should {
        "return true" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          val workItems: Seq[WorkItem[FriendlyNameWorkItem]] =
            (nonOutstandingProcessingStatuses + ToDo).toSeq.map(buildWorkItem(FriendlyNameWorkItem(groupId, client), _))
          mockWorkItemServiceQuery(workItems)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe true
        }
      }

      s"workItemService returns a list of work items that contains a $Deferred" should {
        "return true" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          val workItems: Seq[WorkItem[FriendlyNameWorkItem]] = (nonOutstandingProcessingStatuses + Deferred).toSeq.map(
            buildWorkItem(FriendlyNameWorkItem(groupId, client), _)
          )
          mockWorkItemServiceQuery(workItems)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe true
        }
      }

      s"workItemService returns a list of work items that contains a $Failed" should {
        "return true" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          val workItems: Seq[WorkItem[FriendlyNameWorkItem]] = (nonOutstandingProcessingStatuses + Failed).toSeq.map(
            buildWorkItem(FriendlyNameWorkItem(groupId, client), _)
          )
          mockWorkItemServiceQuery(workItems)(mockWorkItemService)

          agentChecksService.outstandingWorkItemsExist(arn).futureValue shouldBe true
        }
      }
    }

  }

  "Outstanding Assignments Work Items exist" when {

    val outstandingProcessingStatuses: Set[ProcessingStatus] = Set(ToDo, InProgress, Failed, Deferred)
    val nonOutstandingProcessingStatuses: Set[ProcessingStatus] = processingStatuses -- outstandingProcessingStatuses

    "workItemService returns an empty list of work items" should {
      "return false" in new TestScope {
        mockAssignmentsWorkItemServiceQuery(Seq.empty)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe false
      }
    }

    s"workItemService returns a list of work items that does not contain any of $outstandingProcessingStatuses" should {
      "return false" in new TestScope {
        val workItems: Seq[WorkItem[AssignmentWorkItem]] =
          nonOutstandingProcessingStatuses.toSeq.map(
            buildWorkItem(AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value), _)
          )
        mockAssignmentsWorkItemServiceQuery(workItems)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe false
      }
    }

    s"workItemService returns a list of work items that contains a $InProgress" should {
      "return true" in new TestScope {
        val workItems: Seq[WorkItem[AssignmentWorkItem]] =
          (nonOutstandingProcessingStatuses + InProgress).toSeq.map(
            buildWorkItem(AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value), _)
          )
        mockAssignmentsWorkItemServiceQuery(workItems)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe true
      }
    }

    s"workItemService returns a list of work items that contains a $ToDo" should {
      "return true" in new TestScope {
        val workItems: Seq[WorkItem[AssignmentWorkItem]] =
          (nonOutstandingProcessingStatuses + ToDo).toSeq.map(
            buildWorkItem(AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value), _)
          )
        mockAssignmentsWorkItemServiceQuery(workItems)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe true
      }
    }

    s"workItemService returns a list of work items that contains a $Deferred" should {
      "return true" in new TestScope {
        val workItems: Seq[WorkItem[AssignmentWorkItem]] =
          (nonOutstandingProcessingStatuses + Deferred).toSeq.map(
            buildWorkItem(AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value), _)
          )
        mockAssignmentsWorkItemServiceQuery(workItems)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe true
      }
    }

    s"workItemService returns a list of work items that contains a $Failed" should {
      "return true" in new TestScope {
        val workItems: Seq[WorkItem[AssignmentWorkItem]] =
          (nonOutstandingProcessingStatuses + Failed).toSeq.map(
            buildWorkItem(AssignmentWorkItem(Assign, userId, enrolmentKey, arn.value), _)
          )
        mockAssignmentsWorkItemServiceQuery(workItems)(mockAssignmentsWorkItemService)

        agentChecksService.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe true
      }
    }

  }

  "Get Team Members" when {

    "enrolment store proxy returns no group for ARN" should {
      "return no team members" in new TestScope {
        mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(None)(mockEnrolmentStoreProxyConnector)

        agentChecksService.getTeamMembers(arn).futureValue shouldBe empty
      }
    }

    "enrolment store proxy returns a group for ARN" when {

      "user groups search connector returns non-empty list of user details" should {
        "return team members" in new TestScope {
          mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(Some(groupId))(mockEnrolmentStoreProxyConnector)
          val seqUserDetails: Seq[UserDetails] = Seq(
            UserDetails(userId = Some("userId1"), credentialRole = Some("Assistant")),
            UserDetails(userId = Some("userId2"), credentialRole = Some("Admin"))
          )

          mockUsersGroupsSearchConnectorGetGroupUsers(seqUserDetails)(mockUsersGroupsSearchConnector)

          agentChecksService.getTeamMembers(arn).futureValue shouldBe seqUserDetails
        }
      }
    }
  }

  private def mockAgentSizeRepositoryGet(maybeAgentSize: Option[AgentSize])(
    mockAgentSizeRepository: AgentSizeRepository
  ) =
    (mockAgentSizeRepository.get _).expects(arn).returning(Future.successful(maybeAgentSize))

  private def mockEnrolmentStoreProxyConnectorGetPrincipalGroupId(
    maybeGroupId: Option[String]
  )(mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) =
    (mockEnrolmentStoreProxyConnector
      .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future.successful(maybeGroupId))

  private def mockServicesConfigGetDuration(mockServicesConfig: ServicesConfig) =
    (mockServicesConfig.getDuration _).expects(refreshdurationConfigKey).returning(refreshDuration)

  private def mockEnrolmentStoreProxyConnectorGetEnrolments(
    enrolments: Seq[Enrolment]
  )(mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) =
    (mockEnrolmentStoreProxyConnector
      .getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(groupId, *, *)
      .returning(Future.successful(enrolments))

  private def mockAgentSizeRepositoryUpsert(maybeUpsertType: Option[UpsertType])(
    mockAgentSizeRepository: AgentSizeRepository
  ) =
    (mockAgentSizeRepository.upsert _).expects(*).returning(Future.successful(maybeUpsertType))

  private def mockUsersGroupsSearchConnectorGetGroupUsers(
    seqUserDetail: Seq[UserDetails]
  )(mockUsersGroupsSearchConnector: UsersGroupsSearchConnector) =
    (mockUsersGroupsSearchConnector
      .getGroupUsers(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(groupId, *, *)
      .returning(Future.successful(seqUserDetail))

  private def mockWorkItemServiceQuery(
    workItems: Seq[WorkItem[FriendlyNameWorkItem]]
  )(mockWorkItemService: FriendlyNameWorkItemService) =
    (mockWorkItemService
      .query(_: String, _: Option[Seq[ProcessingStatus]], _: Int)(_: ExecutionContext))
      .expects(groupId, None, *, *)
      .returning(Future.successful(workItems))

  private def mockAssignmentsWorkItemServiceQuery(
    workItems: Seq[WorkItem[AssignmentWorkItem]]
  )(mockAssignmentsWorkItemService: AssignmentsWorkItemService) =
    (mockAssignmentsWorkItemService
      .queryBy(_: Arn, _: Int)(_: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future.successful(workItems))
}
