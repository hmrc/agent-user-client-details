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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Reads._
import play.api.libs.json.Writes
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.{BusinessDetails, CgtError, CgtSubscription, CgtSubscriptionResponse, DesError, DesRegistrationRequest, Individual, NinoDesResponse, VatCustomerDetails, VatDetails}
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.agentuserclientdetails.util.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]]

  def getVatDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatDetails]]

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]]

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]
}

@Singleton
class DesConnectorImpl @Inject()(
                                  appConfig: AppConfig,
                                  agentCacheProvider: AgentCacheProvider,
                                  httpClient: HttpClient,
                                  metrics: Metrics,
                                  desIfHeaders: DesIfHeaders)
  extends HttpAPIMonitor with DesConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl: String = appConfig.desBaseUrl

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] = {
    val url = s"$baseUrl/registration/business-details/nino/${encodePathSegment(nino.value)}"
    getWithDesIfHeaders("getRegistrationBusinessDetailsByNino", url).map { response =>
      response.status match {
        case status if is2xx(status) => response.json.asOpt[BusinessDetails]
        case status if is4xx(status) =>
          logger.warn(s"4xx response for getBusinessDetails ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(s"unexpected error during 'getBusinessDetails', statusCode=$other", other, other)
      }
    }
  }

  def getVatDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatDetails]] = {
    val url = s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information"
    getWithDesIfHeaders("GetVatCustomerInformation", url).map { response =>
      response.status match {
        case OK => response.json.asOpt[VatDetails]
        case NOT_FOUND =>
          logger.info(s"${response.status} response for getVatDetails ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(response.body, other, other)
      }
    }
  }

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse] = {

    val url = s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}"

    getWithDesIfHeaders("getCgtSubscription", url).map { response =>
      val result = response.status match {
        case 200 =>
          Right(response.json.as[CgtSubscription])
        case _ =>
          (response.json \ "failures").asOpt[Seq[DesError]] match {
            case Some(e) => Left(CgtError(response.status, e))
            case None    => Left(CgtError(response.status, Seq(response.json.as[DesError])))
          }
      }

      CgtSubscriptionResponse(result)
    }
  }

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumedAPI-DES-GetAgentRegistration-POST") {
      val url = s"$baseUrl/registration/individual/utr/${UriEncoding.encodePathSegment(utr.value, "UTF-8")}"

      httpClient
        .POST[DesRegistrationRequest, HttpResponse](
          url,
          body = DesRegistrationRequest(isAnAgent = false),
          headers = desIfHeaders.outboundHeaders(viaIF = false))(
          implicitly[Writes[DesRegistrationRequest]],
          implicitly[HttpReads[HttpResponse]],
          hc,
          ec
        )
        .map { response =>
          response.status match {
            case status if is2xx(status) =>
              val isIndividual = (response.json \ "isAnIndividual").as[Boolean]
              if (isIndividual) {
                (response.json \ "individual").asOpt[Individual].flatMap(_.name)
              } else {
                (response.json \ "organisation" \ "organisationName").asOpt[String]
              }

            case other =>
              throw UpstreamErrorResponse(s"unexpected error during 'getBusinessName', statusCode=$other", other, other)
          }
        }
    }

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]] = {
    val url = s"$baseUrl/registration/business-details/mtdbsa/${UriEncoding.encodePathSegment(mtdbsa.value, "UTF-8")}"
    agentCacheProvider.clientNinoCache(mtdbsa.value) {
      getWithDesIfHeaders("GetRegistrationBusinessDetailsByMtdbsa", url).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.asOpt[NinoDesResponse].map(_.nino)
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getNinoFor ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getNinoFor', statusCode=$other", other, other)
        }
      }
    }
  }

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    val url =
      s"$baseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.tradingNameCache(nino.value) {
      getWithDesIfHeaders("GetTradingNameByNino", url).map { response =>
        response.status match {
          case status if is2xx(status) => (response.json \ "businessData").toOption.map(_(0) \ "tradingName").flatMap(_.asOpt[String])
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getTradingNameForNino ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getTradingNameForNino', statusCode=$other", other, other)
        }
      }
    }
  }

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] = {
    val url = s"$baseUrl/vat/customer/vrn/${UriEncoding.encodePathSegment(vrn.value, "UTF-8")}/information"
    agentCacheProvider.vatCustomerDetailsCache(vrn.value) {
      getWithDesIfHeaders("GetVatOrganisationNameByVrn", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            (response.json \ "approvedInformation" \ "customerDetails").asOpt[VatCustomerDetails]
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getVatCustomerDetails ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getVatCustomerDetails', statusCode=$other", other, other)
        }
      }
    }
  }

  private def getWithDesIfHeaders(apiName: String, url: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = desIfHeaders.outboundHeaders(viaIF = true, Some(apiName)))(implicitly[HttpReads[HttpResponse]], hc, ec)
    }
}
