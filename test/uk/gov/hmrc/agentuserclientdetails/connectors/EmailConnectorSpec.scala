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

import com.codahale.metrics.{MetricRegistry, NoopMetricRegistry}
import org.scalamock.handlers.CallHandler0
import play.api.Configuration
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Writes
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.{AppConfig, AppConfigImpl}
import uk.gov.hmrc.agentuserclientdetails.model.EmailInformation
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.{ExecutionContext, Future}

class EmailConnectorSpec extends BaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "Sending email" when {

    s"email endpoint returns $OK" should {
      "return true" in new TestScope {
        mockMetricsDefaultRegistry
        mockServicesConfigBaseUrl
        mockHttpPost(
          s"${appConfig.emailBaseUrl}/hmrc/email",
          HttpResponse(OK, "")
        )

        underTest.sendEmail(emailInformation).futureValue shouldBe true
      }
    }

    s"email endpoint returns $BAD_REQUEST" should {
      "return false" in new TestScope {
        mockMetricsDefaultRegistry
        mockServicesConfigBaseUrl
        mockHttpPost(
          s"${appConfig.emailBaseUrl}/hmrc/email",
          HttpResponse(BAD_REQUEST, "")
        )

        underTest.sendEmail(emailInformation).futureValue shouldBe false
      }
    }
  }

  trait TestScope {
    val emailInformation: EmailInformation =
      EmailInformation(to = Seq.empty, templateId = "templateId", parameters = Map.empty)
    val noopMetricRegistry = new NoopMetricRegistry
    val mockMetrics: Metrics = mock[Metrics]
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
    val mockConfiguration: Configuration = mock[Configuration]
    val appConfig: AppConfig = new AppConfigImpl(mockServicesConfig, mockConfiguration)

    lazy val underTest: EmailConnector = new EmailConnectorImpl(appConfig, mockHttpClient, mockMetrics)

    def mockMetricsDefaultRegistry: CallHandler0[MetricRegistry] =
      (() => mockMetrics.defaultRegistry)
        .expects()
        .returning(noopMetricRegistry)

    def mockServicesConfigBaseUrl =
      (mockServicesConfig.baseUrl _)
        .expects(*)
        .returning("http://someBaseUrl")

    def mockHttpPost[I, A](url: String, response: A): Unit =
      (mockHttpClient
        .POST[I, A](_: String, _: I, _: Seq[(String, String)])(
          _: Writes[I],
          _: HttpReads[A],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(url, *, *, *, *, *, *)
        .returning(Future.successful(response))
  }

}
