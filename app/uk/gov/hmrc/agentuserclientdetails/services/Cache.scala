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

package uk.gov.hmrc.agentuserclientdetails.services

import com.codahale.metrics.MetricRegistry
import com.github.blemale.scaffeine.Scaffeine
import com.kenshoo.play.metrics.Metrics

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Logger, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentuserclientdetails.model.{CgtSubscription, PptSubscription, VatCustomerDetails}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait KenshooCacheMetrics {

  val kenshooRegistry: MetricRegistry

  def record[T](name: String): Unit = {
    kenshooRegistry.getMeters.getOrDefault(name, kenshooRegistry.meter(name)).mark()
    Logger(getClass).debug(s"kenshoo-event::meter::$name::recorded")
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

class LocalCaffeineCache[T](name: String, size: Int, expires: Duration)(implicit metrics: Metrics)
  extends KenshooCacheMetrics with Cache[T] with Logging {

  val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

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
        body.andThen {
          case Success(v) =>
            logger.debug(s"Missing $name cache hit, storing new value.")
            record("Count-" + name + "-from-source")
            underlying.put(key, v)
        }
    }

  def invalidate(key: String)(implicit ec: ExecutionContext): Unit =
    underlying.invalidate(key)
}

@Singleton
class AgentCacheProvider @Inject()(val environment: Environment, configuration: Configuration)(
  implicit metrics: Metrics) {

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

  val tradingNameCache: Cache[Option[String]] =
    if (cacheEnabled) new LocalCaffeineCache[Option[String]]("TradingNameFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[String]]

  val vatCustomerDetailsCache: Cache[Option[VatCustomerDetails]] =
    if (cacheEnabled)
      new LocalCaffeineCache[Option[VatCustomerDetails]]("VatCustomerDetailsFromDES", cacheSize, cacheExpires)
    else new DoNotCache[Option[VatCustomerDetails]]

  val es3Cache: Cache[HttpResponse] =
    if (cacheEnabled)
      new LocalCaffeineCache[HttpResponse]("ES3Enrolments", cacheSize, cacheExpires)
    else new DoNotCache[HttpResponse]

}
