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

package uk.gov.hmrc.asyncmessagebroker.domain

import play.api.libs.json._


case class SparkMessage(text:String, messageId:Option[String], email:Option[String])
object SparkMessage {
  implicit val formats: Format[SparkMessage] = Json.format[SparkMessage]
}

case class ClientRequest(text:String)
object ClientRequest {
  implicit val format = Json.format[ClientRequest]
}

case class RoomResponse(clientId:String, roomId:String)
object RoomResponse {
  implicit val format = Json.format[RoomResponse]
}

case class Response(text:String, email:String, timestamp:Long, id:String)
object Response {
  implicit val format = Json.format[Response]
}

case class Data(id:String, roomId:String, personId:String, personEmail:String, created:String)
object Data {
  implicit val format = Json.format[Data]
}

case class Webhook(id:String, name:String, targetUrl:String, resource:String, event:String, orgId:String,
                   createdBy:String, appId:String, ownedBy:String, status:String, created:String, actorId:String,
                   data:Data)

object Webhook {
  implicit val format = Json.format[Webhook]
}
