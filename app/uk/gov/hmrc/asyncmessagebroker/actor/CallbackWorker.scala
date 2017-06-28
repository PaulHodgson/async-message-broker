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

package uk.gov.hmrc.asyncmessagebroker.actor

import akka.actor.{ActorRef, Props}
import com.kenshoo.play.metrics.Metrics
import play.Logger
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.asyncmessagebroker.actor.WorkPullingPattern.Batch
import uk.gov.hmrc.asyncmessagebroker.repository.{GroupStatusUpdate, ProcessingStatus}
import uk.gov.hmrc.asyncmessagebroker.service.{MessageServiceApi, AsyncMessage, MessageService}

import scala.concurrent.Future

class CallbackWorker(master: ActorRef, messageService: MessageServiceApi, metrics: Metrics, messageUpdateMode:Boolean) extends Worker[Batch[AsyncMessage]](master) {

  def debug(message:String) = Logger.debug(s"CallbackWorker: $message")

  def error(message:String) = Logger.error(s"CallbackWorker: $message")

  case class UpdateStatus(status:ProcessingStatus, id:BSONObjectID)

  override def doWork(work: Batch[AsyncMessage]): Future[Unit] = {

    val future: Seq[Future[UpdateStatus]] = work.map { item =>
      messageService.processMessage(item).map(_ => UpdateStatus(ProcessingStatus.Complete, item.id.get)).recover {
        case ex:Exception =>
          UpdateStatus(ProcessingStatus.Failed, item.id.get)
      }
    }

    def matchStatus(status:ProcessingStatus): PartialFunction[UpdateStatus, UpdateStatus] = { case d if d.status == status => d }

    Future.sequence(future).map {
      result =>
        if (!messageUpdateMode) {
          val complete = result.collect(matchStatus(ProcessingStatus.Complete)).map(_.id)
          val failed = result.collect(matchStatus(ProcessingStatus.Failed)).map(_.id)
          val update: Seq[GroupStatusUpdate] = Seq(GroupStatusUpdate(ProcessingStatus.Complete, complete), GroupStatusUpdate(ProcessingStatus.Failed, failed))
          // Do not wait for future.
          messageService.updateHookMessages(update).recover {
            case ex:Exception => error(s"Failed to update hooks $update!")
          }
          Unit
        } else Unit
// TODO: Add sweeper to cleanup records!
    }
  }
}

object CallbackWorker {

  def props(master: ActorRef, messageService: MessageService, metrics: Metrics, messageUpdateMode:Boolean): Props = {
    Props(new CallbackWorker(master, messageService, metrics, messageUpdateMode))
  }
}
