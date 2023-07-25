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

package uk.gov.hmrc.agentuserclientdetails.connectors

import com.google.inject.AbstractModule
import play.api.http.Status.{CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreProxyConnectorISpec extends BaseIntegrationSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val httpClient: HttpClient = stub[HttpClient]

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(httpClient)
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
    }
  }

  val mtdVatEnrolment: Enrolment =
    Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val pptEnrolment: Enrolment =
    Enrolment("HMRC-PPT-ORG", "Activated", "Frank Wright", Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345")))
  val legacyVatEnrolment: Enrolment =
    Enrolment("HMCE-VATDEC-ORG", "Activated", "George Candy", Seq(Identifier("VATRegNo", "101747641")))
  val pirEnrolment: Enrolment = Enrolment("HMRC-NI", "Activated", "George Cando", Seq(Identifier("NINO", "QC373791C")))
  val cbcEnrolment: Enrolment = Enrolment(
    "HMRC-CBC-ORG",
    "Activated",
    "Fran Toms",
    Seq(Identifier("UTR", "0123456789"), Identifier("cbcId", "XACBC0000012345"))
  )

  trait TestScope {
    lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    lazy val esp: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]
  }

  "EnrolmentStoreProxy" should {

    s"handle ES0 call returning $NO_CONTENT" in new TestScope {
      val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val enrolmentType = "principal"

      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users?type=$enrolmentType",
        mockResponse
      )(httpClient)

      esp.getUsersAssignedToEnrolment(enrolmentKey, enrolmentType).futureValue shouldBe Seq.empty
    }

    "handle ES0 call returning 'principal' user ids" in new TestScope {
      val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val enrolmentType = "principal"

      val pricipalUserId1 = "ABCEDEFGI1234567"
      val pricipalUserId2 = "ABCEDEFGI1234568"
      val principalUserIds: String = s"""{
                                        |    "principalUserIds": [
                                        |       "$pricipalUserId1",
                                        |       "$pricipalUserId2"
                                        |    ]
                                        |}""".stripMargin

      val mockResponse: HttpResponse = HttpResponse(OK, principalUserIds)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users?type=$enrolmentType",
        mockResponse
      )(httpClient)

      esp.getUsersAssignedToEnrolment(enrolmentKey, enrolmentType).futureValue shouldBe Seq(
        pricipalUserId1,
        pricipalUserId2
      )
    }

    "handle ES0 call returning 'delegated' user ids" in new TestScope {
      val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val enrolmentType = "delegated"

      val delegatedUserId1 = "ABCEDEFGI1234565"
      val delegatedUserId2 = "ABCEDEFGI1234566"
      val delegatedUserIds: String = s"""{
                                        |    "delegatedUserIds": [
                                        |       "$delegatedUserId1",
                                        |       "$delegatedUserId2"
                                        |    ]
                                        |}""".stripMargin

      val mockResponse: HttpResponse = HttpResponse(OK, delegatedUserIds)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users?type=$enrolmentType",
        mockResponse
      )(httpClient)

      esp.getUsersAssignedToEnrolment(enrolmentKey, enrolmentType).futureValue shouldBe Seq(
        delegatedUserId1,
        delegatedUserId2
      )
    }

    "handle ES0 call returning 'principal' and 'delegated' user ids" in new TestScope {
      val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val enrolmentType = "all"

      val pricipalUserId1 = "ABCEDEFGI1234567"
      val pricipalUserId2 = "ABCEDEFGI1234568"
      val delegatedUserId1 = "ABCEDEFGI1234565"
      val delegatedUserId2 = "ABCEDEFGI1234566"
      val delegatedUserIds: String = s"""{
                                        |    "principalUserIds": [
                                        |       "$pricipalUserId1",
                                        |       "$pricipalUserId2"
                                        |    ],
                                        |    "delegatedUserIds": [
                                        |       "$delegatedUserId1",
                                        |       "$delegatedUserId2"
                                        |    ]
                                        |}""".stripMargin

      val mockResponse: HttpResponse = HttpResponse(OK, delegatedUserIds)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users?type=$enrolmentType",
        mockResponse
      )(httpClient)

      esp.getUsersAssignedToEnrolment(enrolmentKey, enrolmentType).futureValue shouldBe Seq(
        pricipalUserId1,
        pricipalUserId2,
        delegatedUserId1,
        delegatedUserId2
      )
    }

    "handle ES0 call for unknown enrolment type" in new TestScope {
      val enrolmentKey = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
      val enrolmentType = "unknown"

      esp.getUsersAssignedToEnrolment(enrolmentKey, enrolmentType).futureValue shouldBe Seq.empty
    }

    "complete ES1 call successfully" in new TestScope {
      val arn = "TARN0000001"
      val groupId = "2K6H-N1C1-7M7V-O4A3"
      val principalGroupIds = s"""{"principalGroupIds": ["$groupId"]}"""
      val mockResponse: HttpResponse = HttpResponse(OK, principalGroupIds)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal",
        mockResponse
      )(httpClient)
      esp.getPrincipalGroupIdFor(Arn(arn)).futureValue shouldBe Some(groupId)
    }
    s"handle $NO_CONTENT in ES1 call" in new TestScope {
      val arn = "TARN0000001"
      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal",
        mockResponse
      )(httpClient)
      esp.getPrincipalGroupIdFor(Arn(arn)).futureValue shouldBe None
    }

    "complete ES3 call successfully, discounting unsupported enrolments" in new TestScope {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      def mockResponse(startRecord: Int, totalRecords: Int, enrolments: Seq[Enrolment]): HttpResponse =
        HttpResponse(
          OK,
          Json
            .obj(
              "startRecord"  -> startRecord,
              "totalRecords" -> totalRecords,
              "enrolments"   -> Json.toJson(enrolments)
            )
            .toString
        )
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments?type=delegated&start-record=1&max-records=${appConfig.es3MaxRecordsFetchCount}",
        mockResponse(1, 5, Seq(mtdVatEnrolment, pptEnrolment, legacyVatEnrolment, cbcEnrolment, pirEnrolment))
      )(httpClient)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments?type=delegated&start-record=${1 + appConfig.es3MaxRecordsFetchCount}&max-records=${appConfig.es3MaxRecordsFetchCount}",
        mockResponse(1 + appConfig.es3MaxRecordsFetchCount, 0, Seq.empty)
      )(httpClient)
      esp.getEnrolmentsForGroupId(testGroupId).futureValue.toSet shouldBe Set(
        mtdVatEnrolment,
        pptEnrolment,
        cbcEnrolment
      )
    }

    "complete ES19 call successfully" in new TestScope {
      val testGroupId = "2K6H-N1C1-7M7V-O4A3"
      val mockResponse: HttpResponse =
        HttpResponse(OK, Json.obj("enrolments" -> Json.toJson(Seq(mtdVatEnrolment, pptEnrolment))).toString)
      val enrolmentKey: String = EnrolmentKey.fromEnrolment(mtdVatEnrolment)
      mockHttpPut(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$testGroupId/enrolments/$enrolmentKey/friendly_name",
        mockResponse
      )(httpClient)
      esp
        .updateEnrolmentFriendlyName(testGroupId, enrolmentKey, "Friendly Name")
        .futureValue shouldBe Future.unit.futureValue
    }
    "complete ES11 call successfully" in new TestScope {
      val testUserId = "ABCEDEFGI1234568"
      val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"
      val mockResponse: HttpResponse = HttpResponse(CREATED, "")
      mockHttpPostEmpty(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/users/$testUserId/enrolments/$testEnrolmentKey",
        mockResponse
      )(httpClient)
      esp.assignEnrolment(testUserId, testEnrolmentKey).futureValue shouldBe Future.unit.futureValue
    }
    "complete ES12 call successfully" in new TestScope {
      val testUserId = "ABCEDEFGI1234568"
      val testEnrolmentKey = "HMRC-MTD-VAT~VRN~12345678"
      val mockResponse: HttpResponse = HttpResponse(NO_CONTENT, "")
      mockHttpDelete(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/users/$testUserId/enrolments/$testEnrolmentKey",
        mockResponse
      )(httpClient)
      esp.unassignEnrolment(testUserId, testEnrolmentKey).futureValue shouldBe Future.unit.futureValue
    }

    s"handle ES21 call returning non-$OK" in new TestScope {
      val groupId = "2K6H-N1C1-7M7V-O4A3"

      val mockResponse: HttpResponse = HttpResponse(NOT_FOUND, "")
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$groupId/delegated",
        mockResponse
      )(httpClient)

      esp.getGroupDelegatedEnrolments(groupId).futureValue shouldBe None
    }

    s"handle ES21 call returning $OK" in new TestScope {
      val groupId = "2K6H-N1C1-7M7V-O4A3"

      val groupDelegatedEnrolments: String = s"""{
                                                |    "clients": []
                                                |}""".stripMargin

      val mockResponse: HttpResponse = HttpResponse(OK, groupDelegatedEnrolments)
      mockHttpGet(
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/groups/$groupId/delegated",
        mockResponse
      )(httpClient)

      esp.getGroupDelegatedEnrolments(groupId).futureValue shouldBe Some(GroupDelegatedEnrolments(Seq.empty))
    }

    "ES2 collate paginated results correctly" in new TestScope {
      val userId = "myUser"
      val pageSize: Int = appConfig.es3MaxRecordsFetchCount
      val totalNrEnrolments: Int =
        (pageSize * 2.5).toInt // use two and a half pages of results for the sake of the test
      val enrolments: Seq[Enrolment] = Seq.tabulate(totalNrEnrolments)(i =>
        Enrolment("HMRC-MTD-VAT", "Activated", s"Client$i", Seq(Identifier("VRN", "%09d".format(i))))
      )
      def urlForPage(page: Int): String =
        s"${appConfig.enrolmentStoreProxyUrl}/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&start-record=${(page - 1) * pageSize + 1}&max-records=$pageSize"
      def resultsForPage(page: Int): PaginatedEnrolments = PaginatedEnrolments(
        (page - 1) * pageSize + 1,
        totalNrEnrolments,
        enrolments.slice((page - 1) * pageSize, page * pageSize)
      )

      mockHttpGet(urlForPage(1), HttpResponse(OK, Json.toJson(resultsForPage(1)).toString))(httpClient) // page 1
      mockHttpGet(urlForPage(2), HttpResponse(OK, Json.toJson(resultsForPage(2)).toString))(httpClient) // page 2
      mockHttpGet(urlForPage(3), HttpResponse(OK, Json.toJson(resultsForPage(3)).toString))(httpClient) // page 3
      mockHttpGet(urlForPage(4), HttpResponse(NO_CONTENT, ""))(httpClient) // page 4 (empty)

      esp.getEnrolmentsAssignedToUser(userId).futureValue.toSet shouldBe enrolments.toSet
    }
  }

  def mockHttpGet[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])(
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpPostEmpty[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .POSTEmpty[A](_: String, _: Seq[(String, String)])(_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .when(url, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpPut[I, A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .PUT[I, A](_: String, _: I, _: Seq[(String, String)])(
        _: Writes[I],
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *, *, *)
      .returns(Future.successful(response))

  def mockHttpDelete[A](url: String, response: A)(mockHttpClient: HttpClient): Unit =
    (mockHttpClient
      .DELETE[A](_: String, _: Seq[(String, String)])(
        _: HttpReads[A],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .when(url, *, *, *, *)
      .returns(Future.successful(response))
}
