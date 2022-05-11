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

import java.net.URL
import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.Enrolment
import uk.gov.hmrc.agentuserclientdetails.services.AgentCacheProvider
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReads.is2xx
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

case class ES19Request(friendlyName: String)
object ES19Request {
  implicit val format: Format[ES19Request] = Json.format[ES19Request]
}

@ImplementedBy(classOf[EnrolmentStoreProxyConnectorImpl])
trait EnrolmentStoreProxyConnector {

  //ES3 - Query Enrolments allocated to a Group
  def getEnrolmentsForGroupId(groupId: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Seq[Enrolment]]

  //ES19 - Update an enrolment's friendly name
  def updateEnrolmentFriendlyName(groupId: String, enrolmentKey: String, friendlyName: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit]
}


@Singleton
class EnrolmentStoreProxyConnectorImpl @Inject()(http: HttpClient, agentCacheProvider: AgentCacheProvider, metrics: Metrics)(implicit appConfig: AppConfig)
    extends EnrolmentStoreProxyConnector with HttpAPIMonitor with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val espBaseUrl = new URL(appConfig.enrolmentStoreProxyUrl)

  private val es3cache = agentCacheProvider.es3Cache

  //ES3 - Query Enrolments allocated to a Group
  def getEnrolmentsForGroupId(groupId: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Seq[Enrolment]] = {
    val url =
      new URL(
        espBaseUrl,
        s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?type=delegated")
    monitor(s"ConsumedAPI-ES-getEnrolmentsForGroupId-$groupId-GET") {
      es3cache(groupId) {
        http.GET[HttpResponse](url.toString)
      }.map { response =>
        response.status match {
          case Status.OK => (response.json \ "enrolments").as[Seq[Enrolment]]
          case Status.NO_CONTENT => Seq.empty
          case other =>
            throw UpstreamErrorResponse(response.body, other, other)
        }
      }
    }
  }

  //ES19 - Update an enrolment's friendly name
  def updateEnrolmentFriendlyName(groupId: String, enrolmentKey: String, friendlyName: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {
    val url = new URL(
      espBaseUrl,
      s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments/$enrolmentKey/friendly_name")
    monitor(s"ConsumedAPI-ES-updateEnrolmentFriendlyName-PUT") {
      http.PUT[ES19Request, HttpResponse](url.toString, ES19Request(friendlyName)).map { response =>
        response.status match {
          case status if is2xx(status) =>
            if (status != Status.NO_CONTENT) logger.warn(s"updateEnrolmentFriendlyName: Expected 204 status, got other success status ($status)")
            es3cache.invalidate(groupId)
          case other =>
            throw UpstreamErrorResponse(response.body, other, other)
        }
      }
    }
  }
}

