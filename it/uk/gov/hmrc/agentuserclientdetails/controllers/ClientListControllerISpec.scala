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

import com.typesafe.config.Config
import org.joda.time.DateTime
import org.scalamock.handlers.CallHandler3
import play.api.Configuration
import play.api.http.HttpEntity.NoEntity
import play.api.http.Status
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentuserclientdetails.connectors.{CitizenDetailsConnector, DesConnector, EnrolmentStoreProxyConnector, IfConnector}
import uk.gov.hmrc.agentuserclientdetails.model.{AgencyDetails, AgentDetailsDesResponse, FriendlyNameJobData, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.{FriendlyNameWorkItemRepository, JobMonitoringRepository}
import uk.gov.hmrc.agentuserclientdetails.services.{AgentCacheProvider, AssignedUsersService, ClientNameService, FriendlyNameWorkItemServiceImpl, JobMonitoringServiceImpl}
import uk.gov.hmrc.agentuserclientdetails.util.MongoProvider
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.workitem.{PermanentlyFailed, Succeeded, ToDo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClientListControllerISpec extends BaseIntegrationSpec with MongoSpecSupport {

  implicit val mongoProvider = MongoProvider(mongo)

  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val wir = FriendlyNameWorkItemRepository(config)
  lazy val wis = new FriendlyNameWorkItemServiceImpl(wir)

  lazy val assignedUsersService = app.injector.instanceOf[AssignedUsersService]
  lazy val jobMonitoringRepository = new JobMonitoringRepository(config)
  lazy val jobMonitoringService = new JobMonitoringServiceImpl(jobMonitoringRepository, appConfig)
  lazy val agentCacheProvider = app.injector.instanceOf[AgentCacheProvider]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val anotherTestGroupId = "8R6G-J5B5-0U1Q-N8R2"
  val testArn = Arn("BARN9706518")
  val unknownArn = Arn("SARN4216517")
  val badArn = Arn("XARN0000BAD")
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
  val client2: Client = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
  val client3: Client = Client("HMRC-CGT-PD~CgtRef~XMCGTP123456789", "George Candy")
  val client4: Client = Client("HMRC-MTD-VAT~VRN~101747642", "Ross Barker")
  val clientsWithFriendlyNames: Seq[Client] = Seq(client1, client2, client3, client4)
  val clientsWithoutAnyFriendlyNames = clientsWithFriendlyNames.map(_.copy(friendlyName = ""))
  val clientsWithoutSomeFriendlyNames =
    clientsWithFriendlyNames.take(2) ++ clientsWithoutAnyFriendlyNames.drop(2)

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropTestCollection(wir.collection.name)
    dropTestCollection(jobMonitoringRepository.collection.name)
  }

  trait TestScope {
    val citizenDetailsConnector = mock[CitizenDetailsConnector]
    val desConnector = stub[DesConnector]
    val ifConnector = mock[IfConnector]
    val ugs = mock[UsersGroupsSearchConnector]
    val esp = mock[EnrolmentStoreProxyConnector]
    val clientNameService = new ClientNameService(
      citizenDetailsConnector,
      desConnector,
      ifConnector,
      agentCacheProvider
    )
    val clc = new ClientListController(
      cc,
      wis,
      esp,
      ugs,
      assignedUsersService,
      jobMonitoringService,
      desConnector,
      appConfig
    )

    val testAgencyDetails = AgencyDetails(Some("Perfect Accounts Ltd"), Some("a@b.c"))

    def mockDesConnectorGetAgencyDetails(
      maybeAgentDetailsDesResponse: Option[AgentDetailsDesResponse]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[AgentDetailsDesResponse]]] = (desConnector
      .getAgencyDetails(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .when(*, *, *)
      .returns(Future successful maybeAgentDetailsDesResponse)
  }

  "GET /arn/:arn/client-list" should {
    "respond with 200 status and a list of enrolments if all of the retrieved enrolments have friendly names" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithFriendlyNames))

      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))

      val request = FakeRequest("GET", "")
      val result = clc.getClients(testArn)(request).futureValue
      result.header.status shouldBe 200

      // Do not create a job monitoring item if there was no work to be done.
      jobMonitoringService.getNextJobToCheck.futureValue shouldBe None
    }

    "respond with 400 status if given an ARN in invalid format" in new TestScope {
      val request = FakeRequest("GET", "")
      val result = clc.getClients(badArn)(request).futureValue
      result.header.status shouldBe Status.BAD_REQUEST
    }

    "respond with 404 status if given a valid but non-existent ARN" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(unknownArn, *, *)
        .returning(Future.successful(None))
      val request = FakeRequest("GET", "")
      val result = clc.getClients(unknownArn)(request).futureValue
      result.header.status shouldBe Status.NOT_FOUND
    }

    "respond with 404 status if the groupId associated with the arn is unknown" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(UpstreamErrorResponse("", 404)))
      val request = FakeRequest("GET", "")
      val result = clc.getClients(testArn)(request).futureValue
      result.header.status shouldBe 404
    }
    "respond with 202 status if any of the retrieved enrolments don't have a friendly name" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithoutSomeFriendlyNames))
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = clc.getClients(testArn)(request).futureValue
      result.header.status shouldBe 202

      // Create a job monitoring item if there was no work to be done, which should contain all the enrolment keys for which there was no name.
      val maybeJob = jobMonitoringService.getNextJobToCheck.futureValue
      maybeJob should not be empty
      maybeJob.get.item should matchPattern {
        case job: FriendlyNameJobData
            if job.enrolmentKeys.length == clientsWithoutSomeFriendlyNames.count(_.friendlyName.isEmpty) =>
      }
    }
    "respond with 200 status if any of the retrieved enrolments don't have a friendly name but they have been tried before and marked as permanently failed" in new TestScope {
      wis
        .pushNew(
          clientsWithoutSomeFriendlyNames
            .filter(_.friendlyName.isEmpty)
            .map(e => FriendlyNameWorkItem(testGroupId, e)),
          DateTime.now(),
          PermanentlyFailed
        )
        .futureValue

      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithoutSomeFriendlyNames))
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = clc.getClients(testArn)(request).futureValue
      result.header.status shouldBe 200

      // Do not create a job monitoring item if there was no work to be done.
      jobMonitoringService.getNextJobToCheck.futureValue shouldBe None
    }
  }

  "GET /arn/:arn/client-list-status" should {
    "respond with 200 status if all of the retrieved enrolments have friendly names" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithFriendlyNames))
      val request = FakeRequest("GET", "")
      val result = clc.getClientListStatus(testArn)(request).futureValue
      result.header.status shouldBe 200
      result.body shouldBe NoEntity
    }

    "respond with 202 status if some of the retrieved enrolments have friendly names" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithoutSomeFriendlyNames))
      val request = FakeRequest("GET", "")
      mockDesConnectorGetAgencyDetails(None)
      val result = clc.getClientListStatus(testArn)(request).futureValue
      result.header.status shouldBe 202
      result.body shouldBe NoEntity
    }
  }

  "GET /arn/:arn/clients-assigned-users" when {

    "ESP connector returns nothing for group delegated enrolments" should {
      "return 404" in new TestScope {
        (esp
          .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
          .expects(testArn, *, *)
          .returning(Future.successful(Some(testGroupId)))
        (esp
          .getGroupDelegatedEnrolments(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *)
          .returning(Future successful None)

        val request = FakeRequest("GET", "")
        val result = clc.getClientsWithAssignedUsers(testArn)(request)
        result.futureValue.header.status shouldBe 404
      }
    }

    "ESP connector returns some group delegated enrolments" when {

      "UGS does not return any matching user details" should {
        "return 200 with empty list of clients" in new TestScope {
          (esp
            .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
            .expects(testArn, *, *)
            .returning(Future.successful(Some(testGroupId)))

          val groupDelegatedEnrolments =
            GroupDelegatedEnrolments(Seq(AssignedClient("aService", Seq(Identifier("idKey", "idVal")), None, "me")))

          (esp
            .getGroupDelegatedEnrolments(_: String)(_: HeaderCarrier, _: ExecutionContext))
            .expects(*, *, *)
            .returning(
              Future successful Some(
                groupDelegatedEnrolments
              )
            )

          (ugs
            .getGroupUsers(_: String)(_: HeaderCarrier, _: ExecutionContext))
            .expects(testGroupId, *, *)
            .returning(Future successful Seq.empty)

          val request = FakeRequest("GET", "")
          val result = clc.getClientsWithAssignedUsers(testArn)(request)
          result.futureValue.header.status shouldBe 200
          contentAsJson(result).as[GroupDelegatedEnrolments] shouldBe GroupDelegatedEnrolments(Seq.empty)
        }
      }

      "UGS returns matching user details" should {
        "return 200 with non-empty list of clients" in new TestScope {
          (esp
            .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
            .expects(testArn, *, *)
            .returning(Future.successful(Some(testGroupId)))

          val groupDelegatedEnrolments =
            GroupDelegatedEnrolments(Seq(AssignedClient("aService", Seq(Identifier("idKey", "idVal")), None, "me")))

          (esp
            .getGroupDelegatedEnrolments(_: String)(_: HeaderCarrier, _: ExecutionContext))
            .expects(*, *, *)
            .returning(
              Future successful Some(
                groupDelegatedEnrolments
              )
            )

          (ugs
            .getGroupUsers(_: String)(_: HeaderCarrier, _: ExecutionContext))
            .expects(testGroupId, *, *)
            .returning(Future successful Seq(UserDetails(userId = Some("me"))))

          val request = FakeRequest("GET", "")
          val result = clc.getClientsWithAssignedUsers(testArn)(request)
          result.futureValue.header.status shouldBe 200
          contentAsJson(result).as[GroupDelegatedEnrolments] shouldBe groupDelegatedEnrolments
        }
      }
    }
  }

  "POST /groupid/:groupid/refresh-names" should {
    "delete all work items from the repo for the given groupId and recreate work items, ignoring any names already present in the enrolment store" in new TestScope {
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
      (esp
        .getClientsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(clientsWithFriendlyNames))
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, clientsWithFriendlyNames(0))),
          DateTime.now(),
          Succeeded
        )
        .futureValue
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, clientsWithFriendlyNames(1))),
          DateTime.now(),
          PermanentlyFailed
        )
        .futureValue
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(anotherTestGroupId, clientsWithFriendlyNames(3))),
          DateTime.now(),
          Succeeded
        )
        .futureValue
      val request = FakeRequest("POST", "")
      val result = clc.forceRefreshFriendlyNames(testArn)(request).futureValue
      result.header.status shouldBe Status.ACCEPTED
      // Check that none of the old work items are left and that now we have new to-do ones with no name filled in.
      val workItems = wis.query(testGroupId, None).futureValue
      workItems.length shouldBe clientsWithFriendlyNames.length
      all(workItems.map(_.status)) shouldBe ToDo
      all(workItems.map(_.item.client.friendlyName)) shouldBe empty
      // Test that work items for a different groupId haven't been affected
      val otherWorkItems = wis.query(anotherTestGroupId, None).futureValue
      otherWorkItems.length shouldBe 1
      otherWorkItems.head.status shouldBe Succeeded
    }
  }

  "/work-items/clean" should {
    "trigger cleanup of work items when requested" in new TestScope {
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), Succeeded)
        .futureValue
      val result = clc.cleanupWorkItems(request).futureValue
      result.header.status shouldBe 200
      wis.collectStats.futureValue.values.sum shouldBe 0
    }
  }

  "/work-items/stats" should {
    "collect repository stats when requested" in new TestScope {
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), ToDo)
        .futureValue
      val result = clc.getWorkItemStats(request)
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[Map[String, Int]].values.sum shouldBe 1
    }
  }

  "/groupid/:groupid/outstanding-work-items" should {
    "query repository by groupId" in new TestScope {
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), DateTime.now(), ToDo)
        .futureValue
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), DateTime.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(FriendlyNameWorkItem(anotherTestGroupId, client2)), DateTime.now(), ToDo)
        .futureValue
      val result = clc.getOutstandingWorkItemsForGroupId(testGroupId)(request)
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[Seq[Client]].toSet shouldBe Set(client1)
    }

  }
}
