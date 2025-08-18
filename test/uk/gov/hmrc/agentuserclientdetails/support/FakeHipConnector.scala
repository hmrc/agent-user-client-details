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

package uk.gov.hmrc.agentuserclientdetails.support

import uk.gov.hmrc.agentuserclientdetails.model.clientidtypes.MtdItId
import uk.gov.hmrc.agentuserclientdetails.connectors.HipConnector
import uk.gov.hmrc.agentuserclientdetails.connectors.TradingDetails
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

object FakeHipConnector
extends HipConnector {
  def getTradingDetailsForMtdItId(mtdId: MtdItId)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] =
    FakeIfConnector.getTradingDetailsForMtdItId(mtdId)(hc, null)
}

case class FailingHipConnector(status: Int)
extends HipConnector {

  private val delegate = new FailingIfConnector(status)
  def getTradingDetailsForMtdItId(
    mtdId: MtdItId
  )(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] = delegate.getTradingDetailsForMtdItId(mtdId)(hc, null)

}

object NotFoundHipConnector
extends HipConnector {
  def getTradingDetailsForMtdItId(mtdId: MtdItId)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] =
    NotFoundIfConnector.getTradingDetailsForMtdItId(mtdId)(hc, null)
}
