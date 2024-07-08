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

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.Reads._
import play.utils.UriEncoding
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.PptSubscription
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.agentuserclientdetails.util.HttpAPIMonitor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IfConnectorImpl])
trait IfConnector {
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]
  def getPptSubscription(
    pptRef: PptRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]]

  def getTradingDetailsForMtdItId(
    mtdId: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]]

}

@Singleton
class IfConnectorImpl @Inject() (
  appConfig: AppConfig,
  agentCacheProvider: AgentCacheProvider,
  httpClient: HttpClient,
  val metrics: Metrics,
  desIfHeaders: DesIfHeaders
)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with IfConnector with HttpErrorFunctions with Logging {

  private val baseUrl: String = appConfig.ifPlatformBaseUrl

  // IF API#1495 Agent Known Fact Check (Trusts)
  def getTrustName(
    trustTaxIdentifier: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {

    val url = getTrustNameUrl(trustTaxIdentifier)

    getWithDesIfHeaders("getTrustName", url).map { response =>
      response.status match {
        case status if is2xx(status) =>
          Some((response.json \ "trustDetails" \ "trustName").as[String])
        case NOT_FOUND => None
        case other =>
          throw UpstreamErrorResponse(
            s"unexpected status during retrieving TrustName, error=${response.body}",
            other,
            other
          )
      }
    }
  }

  // IF API#1712 PPT Subscription Display
  def getPptSubscription(
    pptRef: PptRef
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] = {
    val url = s"$baseUrl/plastic-packaging-tax/subscriptions/PPT/${pptRef.value}/display"
    agentCacheProvider.pptSubscriptionCache(pptRef.value) {
      getWithDesIfHeaders("GetPptSubscriptionDisplay", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            Some(response.json.as[PptSubscription](PptSubscription.reads))
          case NOT_FOUND => None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error from getPptSubscriptionDisplay: ${response.body}", other)
        }
      }
    }
  }

  /* IF API#1171 Get Business Details (for ITSA customers) */
  def getTradingDetailsForMtdItId(
    mtdId: MtdItId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] = {
    val url =
      s"$baseUrl/registration/business-details/mtdId/${UriEncoding.encodePathSegment(mtdId.value, "UTF-8")}"
    agentCacheProvider.tradingDetailsCache(mtdId.value) {
      getWithDesIfHeaders("GetTradingNameByMtdItId", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            Some(
              TradingDetails(
                (response.json \ "taxPayerDisplayResponse" \ "nino").as[Nino],
                (response.json \ "taxPayerDisplayResponse" \ "businessData").toOption
                  .map(_(0) \ "tradingName")
                  .flatMap(_.asOpt[String])
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

  private def getWithDesIfHeaders(apiName: String, url: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] = {

    val headersConfig = desIfHeaders.headersConfig(viaIF = true, url, apiName)

    monitor(s"ConsumedAPI-IF-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = headersConfig.explicitHeaders)(
        implicitly[HttpReads[HttpResponse]],
        headersConfig.hc,
        ec
      )
    }
  }

  private val utrPattern = "^\\d{10}$"

  private def getTrustNameUrl(trustTaxIdentifier: String): String =
    if (trustTaxIdentifier.matches(utrPattern))
      s"$baseUrl/trusts/agent-known-fact-check/UTR/$trustTaxIdentifier"
    else s"$baseUrl/trusts/agent-known-fact-check/URN/$trustTaxIdentifier"
}
