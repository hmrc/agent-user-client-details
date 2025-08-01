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

package uk.gov.hmrc.agentuserclientdetails.support

import uk.gov.hmrc.agentmtdidentifiers.model.CgtRef
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentuserclientdetails.connectors.DesConnector
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case object FakeDesConnector
extends DesConnector {

  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CgtSubscription]] = Future.successful(
    Some(
      CgtSubscription(
        SubscriptionDetails(
          TypeOfPersonDetails(
            "Individual",
            Left(IndividualName("CGT", "Client"))
          )
        )
      )
    )
  )

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[VatCustomerDetails]] = Future.successful(Some(VatCustomerDetails(
    Some("VAT Client"),
    None,
    Some("VAT Client")
  )))

}

case class FailingDesConnector(status: Int)
extends DesConnector {

  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CgtSubscription]] = Future.failed(UpstreamErrorResponse("A fake exception", status))

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[VatCustomerDetails]] = Future.failed(UpstreamErrorResponse("A fake exception", status))

}

case object NotFoundDesConnector
extends DesConnector {

  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CgtSubscription]] = Future.successful(None)

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[VatCustomerDetails]] = Future.successful(None)

}
