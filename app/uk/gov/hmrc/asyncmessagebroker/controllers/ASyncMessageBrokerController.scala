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

package uk.gov.hmrc.asyncmessagebroker.controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}
import com.google.inject.name.Named
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.asyncmessagebroker.domain.{SparkMessage, Response, RoomResponse}
import uk.gov.hmrc.asyncmessagebroker.repository.{SparkMessagePersist, ProfileMongoRepository, SparkMessageRepository}
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.play.microservice.controller.BaseController
import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.language.postfixOps
import com.ciscospark.Message
import scala.concurrent.ExecutionContext.Implicits.global


@Singleton
class ASyncMessageBrokerController @Inject()(@Named("botBearerToken") botBearerToken: String, @Named("botEmailIdentity") botEmail: String, messageRepository: SparkMessageRepository, profileMongoRpository:ProfileMongoRepository) extends BaseController {

  def chatview(csaEmail:String, roomName:String, pageId:String) = Action.async { implicit request =>

    def chat(roomId: String, clientId: String, email:String, roomName:String): Result = Ok(views.html.chatdemo(debug = true, email, clientId, roomId, pageId, roomName))

    profileMongoRpository.findProfileByRoomName(roomName).flatMap {
      case Some(found) =>
        Future.successful(chat(found.roomId, found.clientId, found.email, found.roomName))

      case _ =>
        val clientId = UUID.randomUUID().toString
        val roomId = com.ciscospark.SparkClient.createRoom(botBearerToken, roomName)
        profileMongoRpository.insert(clientId, roomId, roomName, botEmail).map { res =>
          val membership = com.ciscospark.SparkClient.createMembership(botBearerToken, roomId, csaEmail)
          chat(roomId, clientId, botEmail, roomName)
        }
    }
  }

  def createRoom(clientId:String, roomName:String) = Action.async {
    val roomId=com.ciscospark.SparkClient.createRoom(botBearerToken, roomName)

    profileMongoRpository.insert(clientId, roomId, roomName, botEmail).map { res =>
      Ok(Json.toJson(RoomResponse(res.updateType.savedValue.clientId, res.updateType.savedValue.roomId)))
    }
  }

  def getRoomMessages(clientId:String, roomId:String) = Action.async { implicit request =>
    messageRepository.findMessages(clientId, roomId).map{ messages =>
      val resp: List[Response] = messages.map{item => Response(item.action.text,item.action.email.getOrElse("undefined"))}
      Ok(Json.toJson(resp))
    }
  }

  def sendMessage(clientId:String, roomId:String, message:String) = Action.async {
    val localId = UUID.randomUUID().toString
    val resp: Future[DatabaseUpdate[SparkMessagePersist]] = messageRepository.insert(clientId, localId, roomId, SparkMessage(message, None, None))

    resp.flatMap { res =>
      val mess = com.ciscospark.SparkClient.sendMessage(botBearerToken, message, roomId)
      messageRepository.insert(clientId, localId, roomId, SparkMessage(message, Some(mess.getId), Some(mess.getPersonEmail)))
        .map{resA => Ok("{}")
      }
    }
  }

  def sync() = Action.async {
    profileMongoRpository.findProfiles().map { profiles =>
      val outcome: List[RoomResponse] = profiles.map { profile =>

        val messages: scala.Seq[Message] = JavaConverters.asScalaBufferConverter(com.ciscospark.SparkClient.getMessageRooms(botBearerToken, profile.roomId)).asScala.toSeq
        val filteredCSAMessages = messages.filterNot(p => p.getPersonEmail.equals(profile.email))

        // Do not wait for async task to complete.
        filteredCSAMessages.map { message =>
          messageRepository.insert(profile.clientId, UUID.randomUUID().toString, profile.roomId, SparkMessage(message.getText, Some(message.getId), Some(message.getPersonEmail))).map(res => res.updateType.savedValue)
        }
        RoomResponse(profile.clientId, profile.roomId)
      }

      Ok(Json.toJson(outcome))
    }

  }

  def membership(roomId:String, email:String) = Action.async {
    val membership = com.ciscospark.SparkClient.createMembership(botBearerToken, roomId, email)
    Future.successful(Ok(s"""{"id":"${membership.getId}""""))
  }

}


