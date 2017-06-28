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

package uk.gov.hmrc.asyncmessagebroker.service

import java.util.UUID
import javax.inject.{Named, Inject, Singleton}
import com.ciscospark.{Membership, SparkClient}
import com.google.inject.ImplementedBy
import play.api.Logger
import uk.gov.hmrc.asyncmessagebroker.domain.{Webhook, Response, SparkMessage}
import uk.gov.hmrc.asyncmessagebroker.repository._
import uk.gov.hmrc.mongo.DatabaseUpdate
import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.bson.BSONObjectID

case class AsyncMessage(messageId:String, roomId:String, id:Option[BSONObjectID]=None)

@ImplementedBy(classOf[MessageService])
trait MessageServiceApi {
  def getQueuedHookMesssages: Future[Seq[AsyncMessage]]

  def updateHookMessages(items: Seq[GroupStatusUpdate]): Future[Unit]

  def getProfiles: Future[Seq[ProfilePersist]]

  def processMessage(asyncMessage: AsyncMessage): Future[Option[SparkMessagePersist]]

  def createRoom(csaEmail: String, roomName: String): Future[CreatedRoom]

  def getLocalRoomMessages(clientId: String, roomId: String, timestamp: Option[Long], lastId: Option[String]): Future[Seq[Response]]

  def sendMessage(clientId: String, roomId: String, message: String): Future[DatabaseUpdate[SparkMessagePersist]]

  def createMembership(roomId: String, email: String): Membership

  def insertHook(webhook: Webhook): Future[DatabaseUpdate[WebhookPersist]]
}

@Singleton
class MessageService @Inject() (messageRepository:MessageRepository,
                                profileReposity:ProfileRepository,
                                queueRepository:WebhookPersistRepository,
                                @Named("botBearerToken") botBearerToken: String,
                                @Named("botEmailIdentity") botEmail: String) extends MessageServiceApi {

  def getQueuedHookMesssages: Future[Seq[AsyncMessage]] = {
    queueRepository.getQueued(10).map(data =>
      data.map(item => AsyncMessage(item.hook.data.id, item.hook.data.roomId, Some(item.id))))
  }

  def updateHookMessages(items:Seq[GroupStatusUpdate]): Future[Unit] = {
    Future.sequence(items.map( update =>
      queueRepository.updateGroup(update)
    )).map( _ => Unit)
  }

  def getProfiles: Future[Seq[ProfilePersist]] = profileReposity.findProfiles()
  
  def getMessagesForRoomId(roomId:String): Future[Seq[AsyncMessage]] = {
    val items = JavaConverters.asScalaBufferConverter(com.ciscospark.SparkClient.getMessageRooms(botBearerToken, roomId)).asScala.toSeq
    val messages: Seq[AsyncMessage] = items.map(a => AsyncMessage(a.getId, roomId, Some(BSONObjectID.generate)))
    Logger.debug(s"Messages returned are " + messages)

    Future.successful(messages)
  }



  def processMessage(asyncMessage: AsyncMessage): Future[Option[SparkMessagePersist]] = {
    def notFound : Option[SparkMessagePersist] = None

    profileReposity.findProfileByRoomId(asyncMessage.roomId).flatMap {
      case Some(profile) =>
        val message = SparkClient.getMessageFromId(botBearerToken, asyncMessage.messageId, null)
        messageRepository.insert(profile.clientId, UUID.randomUUID().toString, profile.roomId, SparkMessage(message.getText, Some(asyncMessage.messageId), Some(message.getPersonEmail))).flatMap{
          res =>
            Future.successful(Some(res.updateType.savedValue))
        }

      case _ =>
        Future.failed(new Exception(s"The room ${asyncMessage.roomId} is not associated with a current profile."))
    }
  }





  def createRoom(csaEmail:String, roomName:String): Future[CreatedRoom] = {
    profileReposity.findProfileByRoomName(roomName).flatMap {
      case Some(found) =>
        Future.successful(CreatedRoom(found.roomId, found.clientId, found.email, found.roomName))

      case _ =>
        val clientId = UUID.randomUUID().toString
        val roomId = com.ciscospark.SparkClient.createRoom(botBearerToken, roomName)
        profileReposity.insert(clientId, roomId, roomName, botEmail).map { res =>
          val membership = com.ciscospark.SparkClient.createMembership(botBearerToken, roomId, csaEmail)
          CreatedRoom(roomId, clientId, botEmail, roomName)
        }
    }
  }

  def getLocalRoomMessages(clientId:String, roomId:String, timestamp:Option[Long], lastId:Option[String]): Future[Seq[Response]] = {
    messageRepository.findMessages(clientId, roomId, timestamp, lastId).map { messages =>
      messages.map { item =>
        Response(item.action.text, item.action.email.getOrElse("undefined"), item.timestamp, item.action.messageId.getOrElse(throw new Exception("Failed to resolve ID!")))
      }
    }
  }

  def sendMessage(clientId:String, roomId:String, message:String): Future[DatabaseUpdate[SparkMessagePersist]] = {
    val localId = UUID.randomUUID().toString
    val resp: Future[DatabaseUpdate[SparkMessagePersist]] = messageRepository.insert(clientId, localId, roomId, SparkMessage(message, None, None))

    resp.flatMap { res =>
      val mess = com.ciscospark.SparkClient.sendMessage(botBearerToken, message, roomId)
      messageRepository.insert(clientId, localId, roomId, SparkMessage(message, Some(mess.getId), Some(mess.getPersonEmail)))
      }
    }

  def createMembership(roomId:String, email:String): Membership = {
    com.ciscospark.SparkClient.createMembership(botBearerToken, roomId, email)
  }

  def insertHook(webhook:Webhook): Future[DatabaseUpdate[WebhookPersist]] = {
    queueRepository.insert(webhook)
  }
}

case class CreatedRoom(roomId:String, clientId:String, botEmail:String, roomName:String)