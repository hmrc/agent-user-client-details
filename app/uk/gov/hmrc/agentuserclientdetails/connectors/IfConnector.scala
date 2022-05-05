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
import play.api.libs.json.Reads._
import play.api.libs.json.JsValue
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.{InvalidTrust, PptSubscription, TrustName, TrustResponse}
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[IfConnectorImpl])
trait IfConnector {

  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse]

  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]]

  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]]
}

@Singleton
class IfConnectorImpl @Inject()(
                                  appConfig: AppConfig,
                                  agentCacheProvider: AgentCacheProvider,
                                  httpClient: HttpClient,
                                  metrics: Metrics,
                                  desIfHeaders: DesIfHeaders)
  extends HttpAPIMonitor with IfConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  //IF API#1495 Agent Known Fact Check (Trusts)
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] = {

    val url = getTrustNameUrl(trustTaxIdentifier)

    getWithDesIfHeaders("getTrustName", url).map { response =>
      response.status match {
        case status if is2xx(status) =>
          TrustResponse(Right(TrustName((response.json \ "trustDetails" \ "trustName").as[String])))
        case status if is4xx(status) =>
          val invalidTrust = Try(((response.json \ "code").as[String], (response.json \ "reason").as[String])).toEither
            .fold(ex => {
              logger.error(s"${response.body}, ${ex.getMessage}")
              InvalidTrust(status.toString, ex.getMessage)
            }, t => InvalidTrust(t._1, t._2))
          TrustResponse(Left(invalidTrust))
        case other =>
          throw UpstreamErrorResponse(s"unexpected status during retrieving TrustName, error=${response.body}", other, other)
      }
    }
  }

  //IF API#1712 PPT Subscription Display
  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    val url = s"${appConfig.ifPlatformBaseUrl}/plastic-packaging-tax/subscriptions/PPT/${pptRef.value}/display"
    agentCacheProvider.pptSubscriptionCache(pptRef.value) {
      getWithDesIfHeaders("GetPptSubscriptionDisplay", url).map { response =>
        response.status match {
          case status if is2xx(status) =>
            Some(response.json)
          case status if is4xx(status) =>
            logger.warn(s"$status response from getPptSubscriptionDisplay ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error from getPptSubscriptionDisplay, status = $other", other)
        }
      }
    }
  }

  //IF API#1712 PPT Subscription Display
  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] =
    getPptSubscriptionRawJson(pptRef).map(_.flatMap(_.asOpt[PptSubscription](PptSubscription.reads)))

  private def getWithDesIfHeaders(apiName: String, url: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = desIfHeaders.outboundHeaders(viaIF = true, Some(apiName)))(implicitly[HttpReads[HttpResponse]], hc, ec)
    }

  private val utrPattern = "^\\d{10}$"

  private def getTrustNameUrl(trustTaxIdentifier: String): String =
    if (trustTaxIdentifier.matches(utrPattern))
      s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/UTR/$trustTaxIdentifier"
    else s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/$trustTaxIdentifier"
}
