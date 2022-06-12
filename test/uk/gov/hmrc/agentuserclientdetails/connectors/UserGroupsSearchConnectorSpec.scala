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

package uk.gov.hmrc.agentuserclientdetails.connectors

import play.api.http.Status.{BAD_GATEWAY, NOT_FOUND, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.UserDetails
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.support.TestAppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.graphite.DisabledMetrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserGroupsSearchConnectorSpec extends BaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val appConfig: AppConfig = new TestAppConfig
  val mockHttpClient: HttpClient = mock[HttpClient]

  val groupId = "2K6H-N1C1-7M7V-O4A3"

  trait TestScope {
    lazy val urlUserGroupSearch = s"${appConfig.userGroupsSearchUrl}/users-groups-search/groups/$groupId/users"
    lazy val ugsConnector: UsersGroupsSearchConnector = new UsersGroupsSearchConnectorImpl(mockHttpClient, new DisabledMetrics)
  }

  "getGroupUsers" when {

    "UGS endpoint returns 2xx response which has user details" should {
      "return the sequence of user details" in new TestScope {
        val seqUserDetails: Seq[UserDetails] = Seq(
          UserDetails(userId = Some("userId1"), credentialRole = Some("Assistant")),
          UserDetails(userId = Some("userId2"), credentialRole = Some("Admin"))
        )
        val mockResponse: HttpResponse = HttpResponse(OK, Json.toJson(seqUserDetails).toString)
        mockHttpGet(urlUserGroupSearch, mockResponse)(mockHttpClient)

        ugsConnector.getGroupUsers(groupId).futureValue shouldBe seqUserDetails
      }
    }

    s"UGS endpoint returns $NOT_FOUND response" should {
      "return empty sequence" in new TestScope {
        val mockResponse: HttpResponse = HttpResponse(NOT_FOUND, "")
        mockHttpGet(urlUserGroupSearch, mockResponse)(mockHttpClient)

        ugsConnector.getGroupUsers(groupId).futureValue shouldBe empty
      }
    }

    s"UGS endpoint returns server side error response" should {
      "return empty sequence" in new TestScope {
        val mockResponse: HttpResponse = HttpResponse(BAD_GATEWAY, "")
        mockHttpGet(urlUserGroupSearch, mockResponse)(mockHttpClient)

        whenReady(ugsConnector.getGroupUsers(groupId).failed) { ex =>
          ex shouldBe a [UpstreamErrorResponse]
        }
      }
    }
  }

  def mockHttpGet[A](url: String, response: A)(mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])(_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *, *, *)
      .returning(Future.successful(response))
  }

}
