/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.asyncmessagebroker

import javax.inject.{Inject, Provider}

import com.google.inject.AbstractModule
import com.google.inject.name.Names._
import org.asynchttpclient.config.AsyncHttpClientConfigHelper.Config
import play.api.Mode.Mode
import play.api.{Logger, Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.asyncmessagebroker.config.{ScheduledConfig, BrokerConfig}

import scala.concurrent.duration.FiniteDuration

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig with BrokerConfig with ScheduledConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {

    bind(classOf[DB]).toProvider(classOf[MongoDbProvider])
    bind(classOf[String]).annotatedWith(named("botBearerToken")).toInstance(fromConfig(configuration, "bot", "bearerToken" ))
    bind(classOf[String]).annotatedWith(named("botEmailIdentity")).toInstance(fromConfig(configuration, "bot", "email" ))
    bind(classOf[Int]).annotatedWith(named("maxRetryAttempts")).toInstance(configuration.getInt("maxRetryAttempts").getOrElse(3))
    bind(classOf[Boolean]).annotatedWith(named("messageUpdateMode")).toInstance(configuration.getBoolean("messageUpdateMode").getOrElse(false))

    bind(classOf[Int]).annotatedWith(named("callbackDispatcherCount")).toInstance(maximumSenders(configuration, "callbackDispatcher"))
    bind(classOf[FiniteDuration]).annotatedWith(named("callbackInitialDelaySeconds")).toInstance(durationFromConfig(configuration, "callbackJobApi", "initialDelay" ))
    bind(classOf[FiniteDuration]).annotatedWith(named("callbackIntervalSeconds")).toInstance(durationFromConfig(configuration, "callbackJobApi", "interval" ))


  }
}

class MongoDbProvider @Inject() (reactiveMongoComponent: ReactiveMongoComponent) extends Provider[DB] {
  Logger.warn(s"Mongo URI ${reactiveMongoComponent.mongoConnector.mongoConnectionUri}")
  def get = reactiveMongoComponent.mongoConnector.db()
}