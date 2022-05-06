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

package uk.gov.hmrc.agentuserclientdetails.support

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.PptRef
import uk.gov.hmrc.agentuserclientdetails.connectors.IfConnector
import uk.gov.hmrc.agentuserclientdetails.model._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class FakeIfConnector extends IfConnector {
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] =
    Future.successful(TrustResponse(Right(TrustName("Trust Client"))))
  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] =
    Future.successful(Some(
      Json.parse(
        """{
          |  "legalEntityDetails": {
          |    "customerDetails": {
          |      "customerType": "Organisation",
          |      "organisationDetails": {
          |        "organisationName": "PPT Client"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin)
   ))
  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] =
    Future.successful(Some(PptSubscription("PPT Client")))
}

class FailingIfConnector(status: Int) extends IfConnector {
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))
  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))
  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] =
    Future.failed(UpstreamErrorResponse("A fake exception", status))
}

class NotFoundIfConnector extends IfConnector {
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] =
    Future.successful(TrustResponse(Left(InvalidTrust("404", "Not found"))))
  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] =
    Future.successful(None)
    def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] =
    Future.successful(None)
}