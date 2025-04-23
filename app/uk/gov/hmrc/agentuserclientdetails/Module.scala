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

package uk.gov.hmrc.agentuserclientdetails

import javax.inject.Singleton
import com.google.inject.{AbstractModule, Provides}
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import uk.gov.hmrc.clusterworkthrottling.{DefaultServiceInstances, ServiceInstances}
import uk.gov.hmrc.mongo.MongoComponent

import java.time.{Clock, ZoneOffset}
import scala.concurrent.ExecutionContext

class Module extends AbstractModule with ScalaModule {

  override def configure(): Unit = bind[AgentUserClientDetailsMain].asEagerSingleton()

  @Provides
  @Singleton
  def serviceInstancesProvider(configuration: Configuration, mongo: MongoComponent)(implicit
    ec: ExecutionContext
  ): ServiceInstances =
    new DefaultServiceInstances(configuration, mongo)

  @Provides
  @Singleton
  def clock(): Clock = Clock.systemDefaultZone.withZone(ZoneOffset.UTC)

}
