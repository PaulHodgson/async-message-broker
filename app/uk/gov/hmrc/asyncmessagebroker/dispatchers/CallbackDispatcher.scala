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

package uk.gov.hmrc.asyncmessagebroker.dispatchers

import java.util.concurrent.TimeUnit.HOURS
import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.asyncmessagebroker.actor.{CallbackWorker, Master}
import uk.gov.hmrc.asyncmessagebroker.actor.WorkPullingPattern._
import uk.gov.hmrc.asyncmessagebroker.service.{AsyncMessage, MessageService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[CallbackDispatcher])
trait CallbackDispatcherApi {
  def processCallbacks(): Future[Unit]

  def isRunning: Future[Boolean]
}

@Singleton
class CallbackDispatcher @Inject()(@Named("callbackDispatcherCount") callbackDispatcherCount: Int, @Named("messageUpdateMode") messageUpdateMode:Boolean, messageService: MessageService, system: ActorSystem, metrics: Metrics) extends CallbackDispatcherApi {
  implicit val timeout = Timeout(1, HOURS)

  lazy val gangMaster: ActorRef = system.actorOf(Props[Master[Batch[AsyncMessage]]])

  val name = "callback-dispatcher"

  (1 until callbackDispatcherCount).foreach { _ =>
    gangMaster ! RegisterWorker(system.actorOf(CallbackWorker.props(gangMaster, messageService, metrics, messageUpdateMode)))
  }

  override def processCallbacks(): Future[Unit] = {

    def refresh() = {
      messageService.getProfiles.map { profiles =>
        profiles.map { profile =>
          messageService.getMessagesForRoomId(profile.roomId).map(resp =>  process(resp))
        }
      }
    }

    def hook() = messageService.getQueuedHookMesssages.map(resp =>  process(resp))

    def process(items:Seq[AsyncMessage]) = {
      val work = if (items.nonEmpty)
        List(items)
      else
        List.empty
      gangMaster ? Epic[Batch[AsyncMessage]](work)
    }

    val future = if (messageUpdateMode) {
      refresh()
    } else {
      hook()
    }
    future.map(s => Unit)
  }

  override def isRunning: Future[Boolean] = Future.successful(false)
}
