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
import play.api.http.Status
import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.util.HttpAPIMonitor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class Citizen(firstName: Option[String], lastName: Option[String]) {
  lazy val name: Option[String] = {
    val n = Seq(firstName, lastName).collect { case Some(x) => x }.mkString(" ")
    if (n.isEmpty) None else Some(n)
  }
}

object Citizen {
  implicit val reads: Reads[Citizen] = {
    val current = JsPath \ "name" \ "current"
    for {
      fn <- (current \ "firstName").readNullable[String]
      ln <- (current \ "lastName").readNullable[String]
    } yield Citizen(fn, ln)
  }
}

@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {
  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]]
}

@Singleton
class CitizenDetailsConnectorImpl @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends HttpAPIMonitor with CitizenDetailsConnector with Logging {

  private val baseUrl = appConfig.citizenDetailsBaseUrl

  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]] =
    monitor(s"ConsumedAPI-CitizenDetails-GET") {
      http
        .get(url"$baseUrl/citizen-details/nino/${nino.value}")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case Status.OK        => Try(response.json.asOpt[Citizen]).getOrElse(None)
            case Status.NOT_FOUND => None
            case other =>
              throw UpstreamErrorResponse(s"unexpected error during 'getCitizenDetails', statusCode=$other", other)
          }
        }
    }
}
