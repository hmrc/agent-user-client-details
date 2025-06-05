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
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.EmailInformation
import uk.gov.hmrc.agentuserclientdetails.util.HttpAPIMonitor
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector {
  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]
}

class EmailConnectorImpl @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends EmailConnector with HttpAPIMonitor with HttpErrorFunctions with Logging {

  private val baseUrl: String = appConfig.emailBaseUrl

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    monitor(s"Send-Email-${emailInformation.templateId}") {
      http
        .post(url"$baseUrl/hmrc/email")
        .withBody(Json.toJson(emailInformation))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case status if is2xx(status) => true
            case other =>
              logger.warn(s"Unexpected status from email service, status: $other, body: ${response.body}")
              false
          }
        }
    }
}
