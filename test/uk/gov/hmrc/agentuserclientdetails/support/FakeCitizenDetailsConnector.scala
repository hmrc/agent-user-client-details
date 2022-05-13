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

package uk.gov.hmrc.agentuserclientdetails.support

import uk.gov.hmrc.agentuserclientdetails.connectors.{Citizen, CitizenDetailsConnector}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

case object FakeCitizenDetailsConnector extends CitizenDetailsConnector {
  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]] =
    Future.successful(Some(Citizen(Some("Tom"), Some("Client"))))
}

case class FailingCitizenDetailsConnector(status: Int) extends CitizenDetailsConnector {
  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))
}

  case object NotFoundCitizenDetailsConnector extends CitizenDetailsConnector {
  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]] =
    Future.successful(None)
}
