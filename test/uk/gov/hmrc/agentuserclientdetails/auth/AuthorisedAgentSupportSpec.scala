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

package uk.gov.hmrc.agentuserclientdetails.auth

import org.scalamock.handlers.CallHandler3
import play.api.mvc.Results.{Forbidden, Ok}
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.agentuserclientdetails.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthorisedAgentSupportSpec extends BaseSpec {

  "Auth Action not returning authorised agent" should {
    s"return $Forbidden" in new TestScope {
      mockAuthActionGetAuthorisedAgent(None)

      authorisedAgentSupport.withAuthorisedAgent()(body).futureValue shouldBe Forbidden
    }
  }

  "Auth Action returning authorised agent" should {
    s"return $Ok" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

      authorisedAgentSupport.withAuthorisedAgent()(body).futureValue shouldBe Ok
    }
  }

  trait TestScope {
    val body: AuthorisedAgent => Future[Result] = _ => Future successful Ok

    implicit val mockAuthAction: AuthAction = mock[AuthAction]
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
    val authorisedAgentSupport: AuthorisedAgentSupport = new AuthorisedAgentSupport {}

    val arn: Arn = Arn("KARN0762398")
    val user: AgentUser = AgentUser("userId", "userName")

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler3[Boolean, ExecutionContext, Request[?], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent(_: Boolean)(_: ExecutionContext, _: Request[?]))
        .expects(*, *, *)
        .returning(Future.successful(maybeAuthorisedAgent))
  }

}
