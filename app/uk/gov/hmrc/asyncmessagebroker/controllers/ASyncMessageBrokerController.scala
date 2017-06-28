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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.asyncmessagebroker.domain.Webhook
import uk.gov.hmrc.asyncmessagebroker.service.MessageService
import uk.gov.hmrc.play.microservice.controller.BaseController
import scala.concurrent.Future
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class ASyncMessageBrokerController @Inject()(messageService:MessageService) extends BaseController {

  def chatview(csaEmail:String, roomName:String, pageId:String) = Action.async { implicit request =>

    def chat(roomId: String, clientId: String, email:String, roomName:String): Result =
      Ok(views.html.chatdemo(debug = true, email, clientId, roomId, pageId, roomName))

    messageService.createRoom(csaEmail, roomName).map(res => chat(res.roomId, res.clientId, res.botEmail, res.roomName))
  }

  def getRoomMessages(clientId:String, roomId:String, timestamp:Option[Long], lastId:Option[String]) = Action.async { implicit request =>
    messageService.getLocalRoomMessages(clientId, roomId, timestamp, lastId).map(res => Ok(Json.toJson(res)))
  }

  def sendMessage(clientId:String, roomId:String, message:String) = Action.async {
    messageService.sendMessage(clientId, roomId, message).map(_ => Ok("{}"))
  }

  def membership(roomId:String, email:String) = Action.async {
    val membership = messageService.createMembership(roomId, email)
    Future.successful(Ok(s"""{"id":"${membership.getId}""""))
  }

  def webhook = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[Webhook].fold(
      errors => {
        Logger.warn("Service failed for webhook: " + errors + " body" + request.body)
        Future.successful(BadRequest)
      },
      current => {
        messageService.insertHook(current).map(res => {
          Ok("")
        })
      }
    )
  }

}
