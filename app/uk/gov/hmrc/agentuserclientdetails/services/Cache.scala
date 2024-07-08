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

import com.github.blemale.scaffeine.Scaffeine
import play.api.{Configuration, Environment, Logger, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentuserclientdetails.connectors.TradingDetails
import uk.gov.hmrc.agentuserclientdetails.model.{AgentDetailsDesResponse, CgtSubscription, PptSubscription, VatCustomerDetails}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait CacheMetrics {

  val metrics: Metrics

  def record[T](name: String): Unit = {
    metrics.defaultRegistry.getMeters.getOrDefault(name, metrics.defaultRegistry.meter(name)).mark()
    Logger(getClass).debug(s"metrics-event::meter::$name::recorded")
  }

}

trait Cache[T] {
  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T]
  def invalidate(key: String)(implicit ec: ExecutionContext): Unit
}

class DoNotCache[T] extends Cache[T] {
  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T] = body
  def invalidate(key: String)(implicit ec: ExecutionContext): Unit = ()
}

class LocalCaffeineCache[T](name: String, size: Int, expires: Duration)(implicit
  val metrics: Metrics,
  val ec: ExecutionContext
) extends CacheMetrics with Cache[T] with Logging {

  private val underlying: com.github.blemale.scaffeine.Cache[String, T] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(FiniteDuration(expires.toMillis, MILLISECONDS))
      .maximumSize(size)
      .build[String, T]()

  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    underlying.getIfPresent(key) match {
      case Some(v) =>
        record("Count-" + name + "-from-cache")
        Future.successful(v)
      case None =>
        body.andThen { case Success(v) =>
          logger.debug(s"Missing $name cache hit, storing new value.")
          record("Count-" + name + "-from-source")
          underlying.put(key, v)
        }
    }

  def invalidate(key: String)(implicit ec: ExecutionContext): Unit =
    underlying.invalidate(key)
}

@Singleton
class AgentCacheProvider @Inject() (val environment: Environment, configuration: Configuration)(implicit
  metrics: Metrics,
  ec: ExecutionContext
) {

  val runModeConfiguration: Configuration = configuration
  def mode = environment.mode

  val cacheSize = configuration.underlying.getInt("agent.cache.size")
  val cacheExpires = Duration.create(configuration.underlying.getString("agent.cache.expires"))
  val cacheEnabled = configuration.underlying.getBoolean("agent.cache.enabled")

  val trustResponseCache: Cache[Option[String]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[String]]("trustResponse", cacheSize, cacheExpires)
    else new DoNotCache[Option[String]]

  val cgtSubscriptionCache: Cache[Option[CgtSubscription]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[CgtSubscription]]("cgtSubscription", cacheSize, cacheExpires)
    else new DoNotCache[Option[CgtSubscription]]

  val pptSubscriptionCache: Cache[Option[PptSubscription]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[PptSubscription]]("pptSubscription", cacheSize, cacheExpires)
    else new DoNotCache[Option[PptSubscription]]

  val clientNinoCache: Cache[Option[Nino]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[Nino]]("ClientNinoFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[Nino]]

  val clientMtdItIdCache: Cache[Option[MtdItId]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[MtdItId]]("ClientMtdItIdFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[MtdItId]]

  val businessNameCache: Cache[Option[String]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[String]]("BusinessNameFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[String]]

  val tradingDetailsCache: Cache[Option[TradingDetails]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[TradingDetails]]("TradingDetailsFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[TradingDetails]]

  val vatCustomerDetailsCache: Cache[Option[VatCustomerDetails]] =
    if (cacheEnabled)
      new LocalCaffeineCache[Option[VatCustomerDetails]]("VatCustomerDetailsFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[VatCustomerDetails]]

  val agencyDetailsCache: Cache[Option[AgentDetailsDesResponse]] =
    if (cacheEnabled)
      new LocalCaffeineCache[Option[AgentDetailsDesResponse]]("AgencyDetailsFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[AgentDetailsDesResponse]]

  val es1Cache: Cache[HttpResponse] =
    if (cacheEnabled)
      new LocalCaffeineCache[HttpResponse]("ES1Enrolments", cacheSize, cacheExpires)
    else new DoNotCache[HttpResponse]

}
