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
import com.kenshoo.play.metrics.Metrics
import org.scalamock.handlers.CallHandler0
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Writes
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.EmailInformation
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class EmailConnectorSpec extends BaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "Sending email" when {

    s"email endpoint returns $OK" should {
      "return true" in new TestScope {
        mockMetricsDefaultRegistry
        mockAppConfigEmailBaseUrl
        mockHttpPost(
          s"${mockAppConfig.emailBaseUrl}/hmrc/email",
          HttpResponse(OK, "")
        )

        emailConnector.sendEmail(emailInformation).futureValue shouldBe true
      }
    }

    s"email endpoint returns $BAD_REQUEST" should {
      "return false" in new TestScope {
        mockMetricsDefaultRegistry
        mockAppConfigEmailBaseUrl
        mockHttpPost(
          s"${mockAppConfig.emailBaseUrl}/hmrc/email",
          HttpResponse(BAD_REQUEST, "")
        )

        emailConnector.sendEmail(emailInformation).futureValue shouldBe false
      }
    }
  }

  trait TestScope {
    val emailInformation: EmailInformation =
      EmailInformation(to = Seq.empty, templateId = "templateId", parameters = Map.empty)
    val noopMetricRegistry = new NoopMetricRegistry

    val mockMetrics: Metrics = mock[Metrics]
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockHttpClient: HttpClient = mock[HttpClient]

    lazy val emailConnector: EmailConnector = new EmailConnectorImpl(mockAppConfig, mockHttpClient, mockMetrics)

    def mockMetricsDefaultRegistry: CallHandler0[MetricRegistry] =
      (() => mockMetrics.defaultRegistry)
        .expects()
        .returning(noopMetricRegistry)

    def mockAppConfigEmailBaseUrl: CallHandler0[String] =
      (() => mockAppConfig.emailBaseUrl)
        .expects()
        .returning("http://someBaseUrl")
        .noMoreThanTwice()

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
