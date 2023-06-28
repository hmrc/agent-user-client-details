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

import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, CgtRef, MtdItId, Vrn}
import uk.gov.hmrc.agentuserclientdetails.connectors.{DesConnector, TradingDetails}
import uk.gov.hmrc.agentuserclientdetails.model.{AgencyDetails, AgentDetailsDesResponse, CgtSubscription, IndividualName, SubscriptionDetails, TypeOfPersonDetails, VatCustomerDetails}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

case object FakeDesConnector extends DesConnector {
  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CgtSubscription]] =
    Future.successful(
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
  def getTradingDetailsForMtdItId(
    mtdbsa: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] =
    mtdbsa match {
      case MtdItId("GK873907D") => Future.successful(Some(TradingDetails(Nino("GK873908D"), None)))
      case MtdItId("GK873908D") => Future.successful(Some(TradingDetails(Nino("GK873908D"), Some(""))))
      case _                    => Future.successful(Some(TradingDetails(Nino("GK873908D"), Some("IT Client"))))
    }

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] =
    Future.successful(Some(VatCustomerDetails(Some("VAT Client"), None, Some("VAT Client"))))

  override def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] = Future successful Some(
    AgentDetailsDesResponse(Some(AgencyDetails(Some("Delightful agency"), Some("id@domain.com"))))
  )
}

case class FailingDesConnector(status: Int) extends DesConnector {
  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CgtSubscription]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))

  override def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))

  override def getTradingDetailsForMtdItId(
    mtdbsa: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))
}

case object NotFoundDesConnector extends DesConnector {
  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CgtSubscription]] =
    Future.successful(None)

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] =
    Future.successful(None)

  override def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] = Future successful None

  override def getTradingDetailsForMtdItId(
    mtdbsa: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] = Future.successful(None)
}
