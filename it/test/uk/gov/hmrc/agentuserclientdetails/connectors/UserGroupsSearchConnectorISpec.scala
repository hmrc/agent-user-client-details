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

import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.BAD_GATEWAY
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.agents.accessgroups.UserDetails
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserGroupsSearchConnectorISpec
extends BaseIntegrationSpec
with MockFactory {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val groupId = "2K6H-N1C1-7M7V-O4A3"

  lazy val urlUserGroupSearch = url"${appConfig.userGroupsSearchUrl}/users-groups-search/groups/$groupId/users"
  lazy val ugsConnector: UsersGroupsSearchConnector = new UsersGroupsSearchConnector(mockHttpClient, metrics)

  def mockHttpGet(url: URL): CallHandler2[
    URL,
    HeaderCarrier,
    RequestBuilder
  ] =
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(url, *)
      .returning(mockRequestBuilder)

  def mockRequestBuilderExecute[A](value: A): CallHandler2[
    HttpReads[A],
    ExecutionContext,
    Future[A]
  ] =
    (mockRequestBuilder
      .execute(using _: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful value)

  "getGroupUsers" when {

    "UGS endpoint returns 2xx response which has user details" should {
      "return the sequence of user details" in {
        val seqUserDetails: Seq[UserDetails] = Seq(
          UserDetails(userId = Some("userId1"), credentialRole = Some("Assistant")),
          UserDetails(userId = Some("userId2"), credentialRole = Some("Admin"))
        )
        val mockResponse: HttpResponse = HttpResponse(OK, Json.toJson(seqUserDetails).toString)
        mockHttpGet(urlUserGroupSearch)
        mockRequestBuilderExecute(mockResponse)

        ugsConnector.getGroupUsers(groupId).futureValue shouldBe seqUserDetails
      }
    }

    s"UGS endpoint returns $NOT_FOUND response" should {
      "return empty sequence" in {
        val mockResponse: HttpResponse = HttpResponse(NOT_FOUND, "")
        mockHttpGet(urlUserGroupSearch)
        mockRequestBuilderExecute(mockResponse)

        ugsConnector.getGroupUsers(groupId).futureValue shouldBe empty
      }
    }

    s"UGS endpoint returns server side error response" should {
      "return empty sequence" in {
        val mockResponse: HttpResponse = HttpResponse(BAD_GATEWAY, "")
        mockHttpGet(urlUserGroupSearch)
        mockRequestBuilderExecute(mockResponse)

        whenReady(ugsConnector.getGroupUsers(groupId).failed) { ex =>
          ex shouldBe a[UpstreamErrorResponse]
        }
      }
    }
  }

}
