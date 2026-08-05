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

import com.codahale.metrics.NoopMetricRegistry
import izumi.reflect.Tag
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.EmailInformation
import uk.gov.hmrc.agentuserclientdetails.stubs.HttpClientStub
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailConnectorISpec
extends BaseIntegrationSpec
with HttpClientStub
with MockFactory {

  given HeaderCarrier = HeaderCarrier()
  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def requestBuilderReturning(response: HttpResponse): RequestBuilder = {
    val requestBuilder = Mockito.mock(classOf[RequestBuilder])
    Mockito
      .when(
        requestBuilder.withBody(ArgumentMatchers.any[JsValue]())(
          using
          ArgumentMatchers.any[BodyWritable[JsValue]](),
          ArgumentMatchers.any[Tag[JsValue]](),
          ArgumentMatchers.any[ExecutionContext]()
        )
      )
      .thenReturn(requestBuilder)
    Mockito
      .when(
        requestBuilder.execute(using
          ArgumentMatchers.any[HttpReads[HttpResponse]](),
          ArgumentMatchers.any[ExecutionContext]()
        )
      )
      .thenReturn(Future.successful(response))
    requestBuilder
  }

  val emailInformation: EmailInformation = EmailInformation(
    to = Seq.empty,
    templateId = "templateId",
    parameters = Map.empty
  )
  val noopMetricRegistry = new NoopMetricRegistry
  lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val underTest: EmailConnector =
    new EmailConnectorImpl(
      appConfig,
      mockHttpClient,
      metrics
    )

  "Sending email" when {

    s"email endpoint returns $OK" should {

      "return true" in {
        mockHttpPostReturning(
          url"${appConfig.emailBaseUrl}/hmrc/email",
          requestBuilderReturning(HttpResponse(200))
        )

        underTest.sendEmail(emailInformation).futureValue shouldBe true
      }
    }

    s"email endpoint returns $BAD_REQUEST" should {
      "return false" in {
        mockHttpPostReturning(
          url"${appConfig.emailBaseUrl}/hmrc/email",
          requestBuilderReturning(HttpResponse(400))
        )

        underTest.sendEmail(emailInformation).futureValue shouldBe false
      }
    }
  }

}
