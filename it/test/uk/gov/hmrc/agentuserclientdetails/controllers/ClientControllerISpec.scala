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

package uk.gov.hmrc.agentuserclientdetails.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import com.google.inject.AbstractModule
import com.typesafe.config.Config
import org.scalamock.handlers.{CallHandler3, CallHandler4}
import play.api.Configuration
import play.api.http.HttpEntity.NoEntity
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.auth.AuthAction
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.connectors._
import uk.gov.hmrc.agentuserclientdetails.model.{AgencyDetails, AgentDetailsDesResponse, FriendlyNameJobData, FriendlyNameWorkItem}
import uk.gov.hmrc.agentuserclientdetails.repositories.{FriendlyNameWorkItemRepository, JobMonitoringRepository}
import uk.gov.hmrc.agentuserclientdetails.services._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClientControllerISpec extends BaseIntegrationSpec with MongoSupport with AuthorisationMockSupport {

  lazy val cc = app.injector.instanceOf[ControllerComponents]
  lazy val config = app.injector.instanceOf[Config]
  lazy val configuration = app.injector.instanceOf[Configuration]
  lazy val appConfig = app.injector.instanceOf[AppConfig]
  implicit lazy val materializer = app.injector.instanceOf[Materializer]
  implicit lazy val actorSystem = app.injector.instanceOf[ActorSystem]

  lazy val wir = FriendlyNameWorkItemRepository(config, mongoComponent)
  lazy val wis = new FriendlyNameWorkItemServiceImpl(wir, appConfig)

  implicit lazy val mockAuthConnector = mock[AuthConnector]
  implicit lazy val authAction: AuthAction = app.injector.instanceOf[AuthAction]

  lazy val jobMonitoringRepository = new JobMonitoringRepository(mongoComponent, config)
  lazy val jobMonitoringService = new JobMonitoringServiceImpl(jobMonitoringRepository, appConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testGroupId = "2K6H-N1C1-7M7V-O4A3"
  val anotherTestGroupId = "8R6G-J5B5-0U1Q-N8R2"
  val testArn = Arn("BARN9706518")
  val unknownArn = Arn("SARN4216517")
  val badArn = Arn("XARN0000BAD")
  val badGroupId = "XINV-ALID-GROU-PIDX"
  val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
  val client2: Client = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
  val client3: Client = Client("HMRC-CGT-PD~CGTPDRef~XMCGTP123456789", "George Candy")
  val client4: Client = Client("HMRC-MTD-VAT~VRN~101747642", "Ross Barker")
  val client5: Client = Client("HMRC-CBC-ORG~UTR~0123456789~cbcId~XACBC0123456789", "Kenny Roger") // cbc
  val client6: Client = Client("HMRC-CBC-NONUK-ORG~cbcId~XACBC01234567892", "Micheal Jackson") // nonCbc
  val clientsWithFriendlyNames: Seq[Client] = Seq(client1, client2, client3, client4, client5, client6)
  val clientsWithoutAnyFriendlyNames = clientsWithFriendlyNames.map(_.copy(friendlyName = ""))
  val clientsWithoutSomeFriendlyNames =
    clientsWithFriendlyNames.take(2) ++ clientsWithoutAnyFriendlyNames.drop(2)

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wir.collection.drop().toFuture().futureValue
    jobMonitoringRepository.collection.drop().toFuture().futureValue
  }

  trait TestScope {
    val citizenDetailsConnector = mock[CitizenDetailsConnector]
    val desConnector = stub[DesConnector]
    val agentAssuranceConnector = stub[AgentAssuranceConnector]
    val ifConnector = mock[IfConnector]
    val ugs = mock[UsersGroupsSearchConnector]
    val esp = mock[EnrolmentStoreProxyConnector]
    val es3CacheService = mock[ES3CacheService]
    val clientNameService = new ClientNameService(
      citizenDetailsConnector,
      desConnector,
      ifConnector
    )
    val controller = new ClientController(
      cc,
      wis,
      esp,
      es3CacheService,
      jobMonitoringService,
      agentAssuranceConnector,
      appConfig
    )

    val testAgencyDetails = AgencyDetails(Some("Perfect Accounts Ltd"), Some("a@b.c"))

    def mockGetPrincipalForGroupIdSuccess() =
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.successful(Some(testGroupId)))
    def mockDesConnectorGetAgencyDetails(
      maybeAgentDetailsDesResponse: Option[AgentDetailsDesResponse]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[AgentDetailsDesResponse]]] = (desConnector
      .getAgencyDetails(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .when(*, *, *)
      .returns(Future successful maybeAgentDetailsDesResponse)

    def mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(
      clients: Seq[Client]
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Seq[Client]]] = (es3CacheService
      .getClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(Future.successful(clients))

    def mockES3CacheServiceGetCachedClientsForGroupIdWithException(
      errorResponse: UpstreamErrorResponse
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Seq[Client]]] = (es3CacheService
      .getClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(Future.failed(errorResponse))

    def mockEspGetUsersAssignedToEnrolment(
      enrolmentKey: String,
      userIds: Seq[String]
    ): CallHandler4[String, String, HeaderCarrier, ExecutionContext, Future[Seq[String]]] =
      (esp
        .getUsersAssignedToEnrolment(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(enrolmentKey, "delegated", *, *)
        .returning(Future successful userIds)

    def mockES3CacheServiceCacheRefreshForGroupIdWithoutException(
      result: Option[Unit]
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Option[Unit]]] =
      (es3CacheService
        .refresh(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful result)
  }

  "GET /arn/:arn/client/:id" should {

    "respond with 200 status and client when matching client found" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))

      val request = FakeRequest("GET", "")
      val result = controller.getClient(testArn, client2.enrolmentKey)(request)
      status(result) shouldBe 200
      val actualClient = Json.fromJson[Client](contentAsJson(result)).get
      actualClient shouldBe client2

    }

    "respond with 404 status if not found" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))

      val request = FakeRequest("GET", "")
      val result = controller.getClient(testArn, "whatever")(request).futureValue
      result.header.status shouldBe Status.NOT_FOUND
    }
  }

  "GET /arn/:arn/client-list" should {
    "respond with 200 status and a list of enrolments if all of the retrieved enrolments have friendly names" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))

      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn)(request).futureValue
      result.header.status shouldBe 200

      // Do not create a job monitoring item if there was no work to be done.
      jobMonitoringService.getNextJobToCheck.futureValue shouldBe None
    }

    "Allow Assistant credential role " in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponseAssistant)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))

      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn)(request).futureValue
      result.header.status shouldBe 200

      // Do not create a job monitoring item if there was no work to be done.
      jobMonitoringService.getNextJobToCheck.futureValue shouldBe None
    }

    "respond with 400 status if given an ARN in invalid format" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("GET", "")
      val result = controller.getClients(badArn)(request).futureValue
      result.header.status shouldBe Status.BAD_REQUEST
    }

    "respond with 404 status if given a valid but non-existent ARN" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(unknownArn, *, *)
        .returning(Future.successful(None))
      val request = FakeRequest("GET", "")
      val result = controller.getClients(unknownArn)(request).futureValue
      result.header.status shouldBe Status.NOT_FOUND
    }

    "respond with 404 status if the groupId associated with the arn is unknown" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithException(UpstreamErrorResponse("", 404))

      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn)(request).futureValue
      result.header.status shouldBe 404
    }

    "respond with 202 status if any of the retrieved enrolments don't have a friendly name" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithoutSomeFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn)(request).futureValue
      result.header.status shouldBe 202

      // Create a job monitoring item if there was any work to be done, which should contain all the enrolment keys for which there was no name.
      // The option to send email should be OFF as we did not explicitly ask for it in the request
      val maybeJob = jobMonitoringService.getNextJobToCheck.futureValue
      maybeJob should not be empty
      maybeJob.get.item should matchPattern {
        case job: FriendlyNameJobData
            if job.enrolmentKeys.length == clientsWithoutSomeFriendlyNames.count(
              _.friendlyName.isEmpty
            ) && !job.sendEmailOnCompletion =>
      }
    }

    "if creating a job monitoring item, turn on the flag to send an email notification if specified in the request" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithoutSomeFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn, sendEmail = Some(true))(request).futureValue
      result.header.status shouldBe 202

      // Create a job monitoring item with the language preference for the email set to welsh
      val maybeJob = jobMonitoringService.getNextJobToCheck.futureValue
      maybeJob should not be empty
      maybeJob.get.item should matchPattern {
        case job: FriendlyNameJobData if job.sendEmailOnCompletion && job.emailLanguagePreference.forall(_ == "en") =>
      }
    }

    "if creating a job monitoring item, set the email language to welsh if specified in the request" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithoutSomeFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn, sendEmail = Some(true), lang = Some("cy"))(request).futureValue
      result.header.status shouldBe 202

      // Create a job monitoring item with the language preference for the email set to welsh
      val maybeJob = jobMonitoringService.getNextJobToCheck.futureValue
      maybeJob should not be empty
      maybeJob.get.item should matchPattern {
        case job: FriendlyNameJobData if job.sendEmailOnCompletion && job.emailLanguagePreference.contains("cy") =>
      }
    }

    "respond with 200 status if any of the retrieved enrolments don't have a friendly name but they have been tried before and marked as permanently failed" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      wis
        .pushNew(
          clientsWithoutSomeFriendlyNames
            .filter(_.friendlyName.isEmpty)
            .map(e => FriendlyNameWorkItem(testGroupId, e)),
          Instant.now(),
          PermanentlyFailed
        )
        .futureValue

      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithoutSomeFriendlyNames)
      mockDesConnectorGetAgencyDetails(Some(AgentDetailsDesResponse(Some(testAgencyDetails))))
      val request = FakeRequest("GET", "")
      val result = controller.getClients(testArn)(request).futureValue
      result.header.status shouldBe 200

      // Do not create a job monitoring item if there was no work to be done.
      jobMonitoringService.getNextJobToCheck.futureValue shouldBe None
    }
  }

  "GET /arn/:arn/client-list-status" should {
    "respond with 200 status if all of the retrieved enrolments have friendly names" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      val request = FakeRequest("GET", "")
      val result = controller.getClientListStatus(testArn)(request).futureValue
      result.header.status shouldBe 200
      result.body shouldBe NoEntity
    }

    "respond with 202 status if some of the retrieved enrolments have friendly names" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithoutSomeFriendlyNames)
      val request = FakeRequest("GET", "")
      mockDesConnectorGetAgencyDetails(None)
      val result = controller.getClientListStatus(testArn)(request).futureValue
      result.header.status shouldBe 202
      result.body shouldBe NoEntity
    }
  }

  "POST /groupid/:groupid/refresh-names" should {
    "delete all work items from the repo for the given groupId and recreate work items, ignoring any names already present in the enrolment store" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(clientsWithFriendlyNames)
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, clientsWithFriendlyNames(0))),
          Instant.now(),
          Succeeded
        )
        .futureValue
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(testGroupId, clientsWithFriendlyNames(1))),
          Instant.now(),
          PermanentlyFailed
        )
        .futureValue
      wis
        .pushNew(
          Seq(FriendlyNameWorkItem(anotherTestGroupId, clientsWithFriendlyNames(3))),
          Instant.now(),
          Succeeded
        )
        .futureValue
      val request = FakeRequest("POST", "")
      val result = controller.forceRefreshFriendlyNames(testArn)(request).futureValue
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
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), Succeeded)
        .futureValue
      val result = controller.cleanupWorkItems(request).futureValue
      result.header.status shouldBe 200
    }
  }

  "/work-items/stats" should {
    "collect repository stats when requested" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), ToDo)
        .futureValue
      val result = controller.getWorkItemStats(request)
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[Map[String, Int]].values.sum shouldBe 1
    }
  }

  "/groupid/:groupid/outstanding-work-items" should {
    "query repository by groupId" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val request = FakeRequest("GET", "")
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client1)), Instant.now(), ToDo)
        .futureValue
      wis
        .pushNew(Seq(FriendlyNameWorkItem(testGroupId, client3)), Instant.now(), Succeeded)
        .futureValue
      wis
        .pushNew(Seq(FriendlyNameWorkItem(anotherTestGroupId, client2)), Instant.now(), ToDo)
        .futureValue
      val result = controller.getOutstandingWorkItemsForGroupId(testGroupId)(request)
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[Seq[Client]].toSet shouldBe Set(client1)
    }

  }

  "GET tax service client count on /arn/:arn/tax-service-client-count" should {

    "respond with 200 status if es3 cache service returns data" in new TestScope {
      private val countVatClients = 10
      private val countCgtClients = 5
      private val countPptClients = 15
      private val countMtditClients = 11
      private val countTaxableTrustClients = 3
      private val countNonTaxableTrustClients = 2
      private val countCbcEnrolments = 2
      private val countCbcNonUkEnrolments = 1

      // given
      val vatEnrolments =
        (1 to countVatClients).map(_ => Enrolment("HMRC-MTD-VAT", "", "", Seq(Identifier("VRN", "101747641"))))
      val cgtEnrolments =
        (1 to countCgtClients).map(_ =>
          Enrolment("HMRC-CGT-PD", "", "", Seq(Identifier("CGTPDRef", "XMCGTP123456789")))
        )
      val pptEnrolments = (1 to countPptClients).map(_ =>
        Enrolment("HMRC-PPT-ORG", "", "", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
      )
      val mtdEnrolments =
        (1 to countMtditClients).map(_ =>
          Enrolment("HMRC-MTD-IT", "", "", Seq(Identifier("MTDITID", "GUKL52542245108")))
        )
      val ttEnrolments = (1 to countTaxableTrustClients).map(_ =>
        Enrolment("HMRC-TERS-ORG", "", "", Seq(Identifier("SAUTR", "1234567890")))
      )
      val nttEnrolments = (1 to countNonTaxableTrustClients).map(_ =>
        Enrolment("HMRC-TERSNT-ORG", "", "", Seq(Identifier("URN", "XXTRUST10010010")))
      )
      val cbcEnrolments = (1 to countCbcEnrolments).map(i =>
        Enrolment("HMRC-CBC-ORG", "", "", Seq(Identifier("cbcId", f"XACBC00000$i%05d")))
      )
      val cbcNonUkEnrolments = (1 to countCbcNonUkEnrolments).map(i =>
        Enrolment("HMRC-CBC-NONUK-ORG", "", "", Seq(Identifier("cbcId", f"XACBC90000$i%05d")))
      )

      val enrolments: Seq[Enrolment] =
        vatEnrolments ++ cgtEnrolments ++ pptEnrolments ++ mtdEnrolments ++ ttEnrolments ++ nttEnrolments ++ cbcEnrolments ++ cbcNonUkEnrolments

      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()

      mockES3CacheServiceGetCachedClientsForGroupIdWithoutException(enrolments.map(Client.fromEnrolment))

      val request = FakeRequest("GET", "")

      // when
      val result = controller.getTaxServiceClientCount(testArn)(request)

      // then
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[Map[String, Int]] shouldBe Map(
        "HMRC-MTD-VAT"       -> countVatClients,
        "HMRC-CGT-PD"        -> countCgtClients,
        "HMRC-PPT-ORG"       -> countPptClients,
        "HMRC-MTD-IT"        -> countMtditClients,
        "HMRC-TERS-ORG"      -> countTaxableTrustClients, // trusts not combined until in agent-permissions BE
        "HMRC-TERSNT-ORG"    -> countNonTaxableTrustClients,
        "HMRC-CBC-ORG"       -> countCbcEnrolments,
        "HMRC-CBC-NONUK-ORG" -> countCbcNonUkEnrolments
      )
    }
  }

  "GET paginated clients on /arn/:arn/clients" should {

    "returns 200 Ok with tax reference search matching all" in new TestScope {
      // given
      val clients = (1 to 20).map(i => Client("HMRC-MTD-VAT~VRN~101747642", s"Ross Barker $i"))

      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      (es3CacheService
        .getClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testGroupId, *, *)
        .returning(Future.successful(clients))
      val request = FakeRequest("GET", "")

      // when
      val result = controller.getPaginatedClients(testArn, 1, 15, search = Option("10174"))(request)

      // then
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[PaginatedList[Client]] shouldBe PaginatedList(
        pageContent = clients.take(15),
        paginationMetaData = PaginationMetaData(false, true, 20, 2, 15, 1, 15)
      )
    }

    "return 200 Ok when only a few match by enrolment key tax reference" in new TestScope {
      // given
      val clients = (1 to 20).map(i => Client(s"HMRC-MTD-VAT~VRN~${i}174764", s"Ross Barker $i"))

      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      (es3CacheService
        .getClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testGroupId, *, *)
        .returning(Future.successful(clients))
      val request = FakeRequest("GET", "")

      private val searchTerm = "1174" // <- should match clients 1 and 11
      // when
      val result = controller.getPaginatedClients(testArn, 1, 15, search = Option(searchTerm))(request)

      // then
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[PaginatedList[Client]] shouldBe PaginatedList(
        pageContent = Seq(clients(0), clients(10)),
        paginationMetaData = PaginationMetaData(true, true, 2, 1, 15, 1, 2)
      )
    }

    "return 200 Ok when only a few match the name" in new TestScope {
      // given
      val clients = Seq(
        Client(s"HMRC-MTD-VAT~VRN~456", "Steve smith"),
        Client(s"HMRC-MTD-VAT~VRN~123", "bob builder"),
        Client(s"HMRC-MTD-VAT~VRN~789", "bob smith"),
        Client(s"HMRC-MTD-VAT~VRN~1020", "John builder")
      )

      mockAuthResponseWithoutException(buildAuthorisedResponse)
      mockGetPrincipalForGroupIdSuccess()
      (es3CacheService
        .getClients(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testGroupId, *, *)
        .returning(Future.successful(clients))
      val request = FakeRequest("GET", "")

      private val searchTerm = "bob" // <- should match clients 1 and 11
      // when
      val result = controller.getPaginatedClients(testArn, 1, 15, search = Option(searchTerm))(request)

      // then
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[PaginatedList[Client]] shouldBe PaginatedList(
        pageContent = Seq(clients(1), clients(2)),
        paginationMetaData = PaginationMetaData(true, true, 2, 1, 15, 1, 2)
      )
    }
  }

  "GET /cache-refresh" should {
    "return 204 No Content if a cache exists" in new TestScope {

      mockSimpleAuthResponse()
      mockGetPrincipalForGroupIdSuccess()

      mockES3CacheServiceCacheRefreshForGroupIdWithoutException(Some(()))

      val request = FakeRequest("PUT", "")
      val result = controller.cacheRefresh(testArn)(request)
      result.futureValue.header.status shouldBe 204
    }

    "return 404 Not Found if a cache doesn't exist" in new TestScope {

      mockSimpleAuthResponse()
      mockGetPrincipalForGroupIdSuccess()

      mockES3CacheServiceCacheRefreshForGroupIdWithoutException(None)

      val request = FakeRequest("PUT", "")
      val result = controller.cacheRefresh(testArn)(request)
      result.futureValue.header.status shouldBe 404
    }

    "return 500 when esp throws an error" in new TestScope {

      mockSimpleAuthResponse()
      (esp
        .getPrincipalGroupIdFor(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(testArn, *, *)
        .returning(Future.failed(UpstreamErrorResponse("bad", 503)))

      val request = FakeRequest("PUT", "")
      val result = controller.cacheRefresh(testArn)(request)
      result.futureValue.header.status shouldBe 500
    }
  }

  "GET /arn/:arn/agency-details" should {

    "return 200 with agency details if found" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      val agencyDetails = AgencyDetails(Some("Agency Name"), Some("agency@email.com"))
      (desConnector
        .getAgencyDetails(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(Some(AgentDetailsDesResponse(Some(agencyDetails)))))

      val result = controller.getAgencyDetails(testArn)(FakeRequest("GET", ""))
      result.futureValue.header.status shouldBe 200
      contentAsJson(result).as[AgencyDetails] shouldBe agencyDetails
    }

    "return 404 when agency details not found" in new TestScope {
      mockAuthResponseWithoutException(buildAuthorisedResponse)
      (desConnector
        .getAgencyDetails(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(testArn, *, *)
        .returns(Future.successful(None))

      val result = controller.getAgencyDetails(testArn)(FakeRequest("GET", ""))
      result.futureValue.header.status shouldBe 404
    }
  }
}
