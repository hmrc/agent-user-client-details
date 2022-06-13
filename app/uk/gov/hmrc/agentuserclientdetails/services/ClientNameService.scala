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

package uk.gov.hmrc.agentuserclientdetails.services

import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentmtdidentifiers.model.{CgtRef, MtdItId, PptRef, Vrn}
import uk.gov.hmrc.agentuserclientdetails.connectors.{CitizenDetailsConnector, DesConnector, IfConnector}
import uk.gov.hmrc.agentuserclientdetails.model.VatCustomerDetails
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class ClientNameNotFound() extends Exception

class ClientNameService @Inject() (
  citizenDetailsConnector: CitizenDetailsConnector,
  desConnector: DesConnector,
  ifConnector: IfConnector,
  agentCacheProvider: AgentCacheProvider
) {

  private def trustCache = agentCacheProvider.trustResponseCache
  private def cgtCache = agentCacheProvider.cgtSubscriptionCache

  def getClientNameByService(clientId: String, service: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[String]] =
    service match {
      case HMRCMTDIT     => getItsaTradingName(MtdItId(clientId))
      case "HMRC-PT"     => getCitizenName(Nino(clientId))
      case HMRCMTDVAT    => getVatName(Vrn(clientId))
      case HMRCTERSORG   => getTrustName(clientId)
      case HMRCTERSNTORG => getTrustName(clientId)
      case HMRCCGTPD     => getCgtName(CgtRef(clientId))
      case HMRCPPTORG    => getPptCustomerName(PptRef(clientId))
      case _             => Future.failed(ClientNameService.InvalidServiceIdException(service))
    }

  def getItsaTradingName(mtdItId: MtdItId)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector.getTradingNameForMtdItId(mtdItId)

  def getCitizenName(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    citizenDetailsConnector.getCitizenDetails(nino).map(_.flatMap(_.name))

  def getVatName(vrn: Vrn)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector
      .getVatCustomerDetails(vrn)
      .map { maybeCustomerDetails =>
        val customerDetails = maybeCustomerDetails.getOrElse(VatCustomerDetails(None, None, None))
        customerDetails.tradingName
          .orElse(customerDetails.organisationName)
          .orElse(customerDetails.individual.map(_.name))
      }

  def getTrustName(
    trustTaxIdentifier: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    trustCache(trustTaxIdentifier) {
      ifConnector.getTrustName(trustTaxIdentifier)
    }

  def getCgtName(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    cgtCache(cgtRef.value) {
      desConnector.getCgtSubscription(cgtRef)
    }.map(_.map { cgtSubscription =>
      cgtSubscription.subscriptionDetails.typeOfPersonDetails.name match {
        case Right(trusteeName)   => trusteeName.name
        case Left(individualName) => s"${individualName.firstName} ${individualName.lastName}"
      }
    })

  def getPptCustomerName(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    ifConnector.getPptSubscription(pptRef).map(_.map(_.customerName))
}

object ClientNameService {
  case class InvalidServiceIdException(serviceId: String) extends RuntimeException
}
