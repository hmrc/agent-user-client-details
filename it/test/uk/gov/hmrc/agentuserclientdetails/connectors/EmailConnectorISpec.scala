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
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfig
import uk.gov.hmrc.agentuserclientdetails.model.EmailInformation
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailConnectorISpec
extends BaseIntegrationSpec
with MockFactory {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  def mockHttpPost(url: URL): CallHandler2[
    URL,
    HeaderCarrier,
    RequestBuilder
  ] =
    (mockHttpClient
      .post(_: URL)(_: HeaderCarrier))
      .expects(url, *)
      .returning(mockRequestBuilder)

  def mockRequestBuilderExecute[A](value: A): CallHandler2[
    HttpReads[A],
    ExecutionContext,
    Future[A]
  ] = {
    (mockRequestBuilder
      .withBody(_: JsValue)(
        using
        _: BodyWritable[JsValue],
        _: Tag[JsValue],
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute(using _: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful value)
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
        mockHttpPost(url"${appConfig.emailBaseUrl}/hmrc/email")
        mockRequestBuilderExecute(HttpResponse(200))

        underTest.sendEmail(emailInformation).futureValue shouldBe true
      }
    }

    s"email endpoint returns $BAD_REQUEST" should {
      "return false" in {
        mockHttpPost(url"${appConfig.emailBaseUrl}/hmrc/email")
        mockRequestBuilderExecute(HttpResponse(400))

        underTest.sendEmail(emailInformation).futureValue shouldBe false
      }
    }
  }

}
