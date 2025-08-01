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

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AuthorisationSupport
extends BaseSpec
with MockFactory {

  val arnStr = "KARN0762398"
  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(
    EnrolmentIdentifier(agentReferenceNumberIdentifier, arnStr)
  )
  val agentEnrolment = "HMRC-AS-AGENT"

  val emptyName: Name = Name(None, None)

  val enrolments: Set[Enrolment] = Set(Enrolment(
    agentEnrolment,
    agentEnrolmentIdentifiers,
    "Activated"
  ))

  def buildAuthorisedResponse: GrantAccess =
    Enrolments(enrolments) and
      Some(User)

  def buildUnauthorisedResponseHavingEmptyEnrolments: GrantAccess =
    Enrolments(Set.empty) and
      Some(User)

  def buildUnauthorisedResponseHavingIncorrectCredentialRole: GrantAccess =
    Enrolments(enrolments) and
      None

  def buildAuthorisedResponseHavingAssistantCredentialRole: GrantAccess =
    Enrolments(enrolments) and
      Some(Assistant)

  type GrantAccess = Enrolments ~ Option[CredentialRole]

  def mockAuthResponseWithoutException(response: GrantAccess)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def mockAuthResponseWithException(exceptionToReturn: Exception)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed exceptionToReturn)

  def mockSimpleAuthResponseWithoutException()(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future.successful(()))

  def mockSimpleAuthResponseWithException(exceptionToReturn: Exception)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed exceptionToReturn)

}
