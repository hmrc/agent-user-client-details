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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Reads._
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.{AgentDetailsDesResponse, CgtSubscription, VatCustomerDetails}
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class TradingDetails(nino: Nino, tradingName: Option[String])

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {
  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CgtSubscription]]

  def getTradingDetailsForMtdItId(
    mtdbsa: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]]

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]

  def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]]
}

@Singleton
class DesConnectorImpl @Inject() (
  appConfig: AppConfig,
  agentCacheProvider: AgentCacheProvider,
  httpClient: HttpClient,
  metrics: Metrics,
  desIfHeaders: DesIfHeaders
) extends HttpAPIMonitor with DesConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl: String = appConfig.desBaseUrl

  def getCgtSubscription(
    cgtRef: CgtRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CgtSubscription]] = {

    val url = s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}"

    getWithDesIfHeaders("getCgtSubscription", url).map { response =>
      response.status match {
        case OK        => Some(response.json.as[CgtSubscription])
        case NOT_FOUND => None
        case other =>
          throw UpstreamErrorResponse(s"unexpected error during 'getCgtSubscription': ${response.body}", other, other)
      }
    }
  }

  def getTradingDetailsForMtdItId(
    mtdbsa: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] = {
    val url =
      s"$baseUrl/registration/business-details/mtdbsa/${UriEncoding.encodePathSegment(mtdbsa.value, "UTF-8")}"
    agentCacheProvider.tradingDetailsCache(mtdbsa.value) {
      getWithDesIfHeaders("GetTradingNameByMtdItId", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            Some(
              TradingDetails(
                (response.json \ "nino").as[Nino],
                (response.json \ "businessData").toOption.map(_(0) \ "tradingName").flatMap(_.asOpt[String])
              )
            )
          case NOT_FOUND => None
          case other =>
            throw UpstreamErrorResponse(
              s"unexpected error during 'getTradingNameForMtdItId', statusCode=$other",
              other,
              other
            )
        }
      }
    }
  }

  def getVatCustomerDetails(
    vrn: Vrn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] = {
    val url = s"$baseUrl/vat/customer/vrn/${UriEncoding.encodePathSegment(vrn.value, "UTF-8")}/information"
    agentCacheProvider.vatCustomerDetailsCache(vrn.value) {
      getWithDesIfHeaders("GetVatOrganisationNameByVrn", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            (response.json \ "approvedInformation" \ "customerDetails").asOpt[VatCustomerDetails]
          case NOT_FOUND => None
          case other =>
            throw UpstreamErrorResponse(
              s"unexpected error during 'getVatCustomerDetails', statusCode=$other",
              other,
              other
            )
        }
      }
    }
  }

  override def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] =
    agentCacheProvider
      .agencyDetailsCache(arn.value) {
        val url = s"$baseUrl/registration/personal-details/arn/${UriEncoding.encodePathSegment(arn.value, "UTF-8")}"
        getWithDesIfHeaders("getAgencyDetails", url).map { response =>
          response.status match {
            case status if is2xx(status) => response.json.asOpt[AgentDetailsDesResponse]
            case status if is4xx(status) => None
            case _ if response.body.contains("AGENT_TERMINATED") =>
              logger.warn(s"Discovered a Termination for agent: $arn")
              None
            case other =>
              throw UpstreamErrorResponse(
                s"unexpected response during 'getAgencyDetails', status: $other, response: ${response.body}",
                other,
                other
              )
          }
        }
      }

  private def getWithDesIfHeaders(apiName: String, url: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = desIfHeaders.outboundHeaders(viaIF = false, Some(apiName)))(
        implicitly[HttpReads[HttpResponse]],
        hc,
        ec
      )
    }
}
