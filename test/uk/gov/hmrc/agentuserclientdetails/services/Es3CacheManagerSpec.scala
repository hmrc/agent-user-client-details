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

package uk.gov.hmrc.agentuserclientdetails.services

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, Enrolment, Identifier}
import uk.gov.hmrc.agentuserclientdetails.BaseSpec
import uk.gov.hmrc.agentuserclientdetails.config.AppConfigImpl
import uk.gov.hmrc.agentuserclientdetails.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentuserclientdetails.repositories.{Es3Cache, Es3CacheRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class Es3CacheManagerSpec extends BaseSpec {

  "Fetching cached clients" when {

    "client enrolments do not exist in cache" should {
      "fetch client enrolments by making call to ES3 and save them" in new TestScope {
        val enrolments = Seq(Enrolment("HMRC-MTD-IT", "", "", Seq(Identifier("MTDITID", "X12345678909876"))))

        mockEs3CacheRepositoryFetch(None)
        mockEnrolmentStoreProxyConnectorGetEnrolmentsForGroupId(enrolments)
        mockEs3CacheRepositorySave(enrolments)
        mockEs3CacheRepositoryFetch(Some(Es3Cache(groupId, enrolments)))

        es3CacheManager.getCachedClients(groupId).futureValue shouldBe Seq(
          Client("HMRC-MTD-IT~MTDITID~X12345678909876", "")
        )
      }
    }

    "client enrolments exist in cache" should {
      "return clients from the cache" in new TestScope {
        val enrolments = Seq(Enrolment("HMRC-MTD-IT", "", "", Seq(Identifier("MTDITID", "X12345678909876"))))

        mockEs3CacheRepositoryFetch(Some(Es3Cache(groupId, enrolments)))

        es3CacheManager.getCachedClients(groupId).futureValue shouldBe Seq(
          Client("HMRC-MTD-IT~MTDITID~X12345678909876", "")
        )
      }
    }
  }

  trait TestScope {
    val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
    val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
    val mockEs3CacheRepository: Es3CacheRepository = mock[Es3CacheRepository]
    val appconfig = new AppConfigImpl(mockServicesConfig)

    val es3CacheManager = new Es3CacheManagerImpl(mockEnrolmentStoreProxyConnector, mockEs3CacheRepository, appconfig)

    val groupId = "0R4C-G0G1-4M9Y-T7P0"

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def mockEs3CacheRepositoryFetch(maybeEs3Cache: Option[Es3Cache]): CallHandler1[String, Future[Option[Es3Cache]]] =
      (mockEs3CacheRepository
        .fetch(_: String))
        .expects(groupId)
        .returning(Future successful maybeEs3Cache)

    def mockEs3CacheRepositorySave(enrolments: Seq[Enrolment]): CallHandler2[String, Seq[Enrolment], Future[String]] =
      (mockEs3CacheRepository
        .save(_: String, _: Seq[Enrolment]))
        .expects(groupId, enrolments)
        .returning(Future successful groupId)

    def mockEnrolmentStoreProxyConnectorGetEnrolmentsForGroupId(
      enrolments: Seq[Enrolment]
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Seq[Enrolment]]] =
      (mockEnrolmentStoreProxyConnector
        .getEnrolmentsForGroupId(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, *, *)
        .returning(Future successful enrolments)

  }
}
