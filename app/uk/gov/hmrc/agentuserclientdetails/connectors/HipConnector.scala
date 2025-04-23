/*
 * Copyright 2025 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import play.api.Logging
import play.api.http.{HeaderNames, Status}
import play.api.http.Status.NOT_FOUND
import play.utils.UriEncoding
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.util.HttpAPIMonitor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import java.time.{Clock, Instant}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HipConnectorImpl])
trait HipConnector {
  def getTradingDetailsForMtdItId(mtdItId: MtdItId)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]]
}

@Singleton
class HipConnectorImpl @Inject() (
  appConfig: AppConfig,
  httpClient: HttpClient,
  val metrics: Metrics,
  clock: Clock
)(implicit val ec: ExecutionContext)
    extends HipConnector with HttpAPIMonitor with Logging {
  httpMonitor: HttpAPIMonitor =>

  /** API number: API#5266 Itsa Taxpayer Business Details
    * https://admin.tax.service.gov.uk/integration-hub/apis/details/e54e8843-c146-4551-a499-c93ecac4c6fd
    */
  def getTradingDetailsForMtdItId(mtdItId: MtdItId)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] = {

    val url: URL = uri"${appConfig.hipBaseUrl}/etmp/RESTAdapter/itsa/taxpayer/business-details"
      .withParam("mtdReference", Some(mtdItId.value))
      .toJavaUri
      .toURL

    val correlationId: String = makeCorrelationId()
    val headers = Seq(
      (HeaderNames.AUTHORIZATION, s"Basic ${appConfig.hipAuthToken}"),
      ("correlationId", correlationId),
      ("X-Message-Type", "TaxpayerDisplay"),
      ("X-Originating-System", "MDTP"),
      (
        "X-Receipt-Date",
        DateTimeFormatter.ISO_INSTANT.format( // yyy-MM-ddTHH:mm:ssZ
          Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        )
      ),
      ("X-Regime-Type", "ITSA"),
      ("X-Transmitting-System", "HIP")
    )

    monitor(s"ConsumedAPI-HIP-ITSA_Taxpayer_Business_Details-GET") {
      httpClient
        .GET[HttpResponse](
          url = url,
          headers = headers
        )
        .map { response =>
          response.status match {
            case 200 =>
              Some(
                TradingDetails(
                  (response.json \ "success" \ "taxPayerDisplayResponse" \ "nino").as[Nino],
                  (response.json \ "success" \ "taxPayerDisplayResponse" \ "businessData").toOption
                    .map(_(0) \ "tradingName")
                    .flatMap(_.asOpt[String])
                )
              )
            case Status.UNPROCESSABLE_ENTITY
                if List(
                  ErrorCodes.`Subscription data not found`,
                  ErrorCodes.`ID not found`
                )
                  .contains((response.json \ "errors" \ "code").as[String]) =>
              None
            case otherStatus =>
              throw new RuntimeException(
                s"Unexpected response from HIP API: [correlationId: $correlationId] [status: $otherStatus] [responseBody: ${response.body}]"
              )
          }
        }
    }
  }

  protected def makeCorrelationId(): String = UUID.randomUUID().toString

  object ErrorCodes {
    val `Subscription data not found`: String = "006"
    val `ID not found`: String = "008"
  }
}
