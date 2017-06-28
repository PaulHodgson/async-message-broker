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

package uk.gov.hmrc.asyncmessagebroker.repository

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.api.{DB, ReadPreference}
import uk.gov.hmrc.asyncmessagebroker.domain.SparkMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class SparkMessagePersist(id: BSONObjectID, timestamp: Long, clientId:String, localId:String, roomId:String, action: SparkMessage)
case class Room(id: BSONObjectID, timestamp: Long, clientId:String, roomId:String)

object SparkMessagePersist {

  val mongoFormats: Format[SparkMessagePersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    implicit val device = SparkMessage.formats
    Format(Json.reads[SparkMessagePersist], Json.writes[SparkMessagePersist])
  })
}

@Singleton
class MessageMongoRepository @Inject()(mongo: DB)
  extends ReactiveRepository[SparkMessagePersist, BSONObjectID]("messages", () => mongo, SparkMessagePersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
  with AtomicUpdate[SparkMessagePersist]
  with MessageRepository
  with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[scala.Seq[Boolean]] = {
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("clientId" -> IndexType.Ascending), name = Some("clientIdNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("roomId" -> IndexType.Ascending), name = Some("roomIdNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("action.messageId" -> IndexType.Ascending), name = Some("actionMessageIdNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(suppliedId: BSONObjectID, returned: SparkMessagePersist): Boolean =
    suppliedId.equals(returned.id)

  protected def findMessageById(localId:String, registration:SparkMessage) = {
    registration.messageId.fold(BSONDocument("localId" -> localId)) { id => BSONDocument("action.messageId" -> id) }
  }

  private def modifierForInsertOrUpdate(registration: SparkMessage, clientId:String, localId:String, roomId:String): BSONDocument = {

    val message = BSONDocument(
      "$setOnInsert" -> BSONDocument("clientId" -> clientId),
      "$setOnInsert" -> BSONDocument("localId" -> localId),
      "$setOnInsert" -> BSONDocument("roomId" -> roomId),
      "$setOnInsert" -> BSONDocument("timestamp" ->  DateTime.now().getMillis)
    )

    val messageId: BSONDocument = registration.messageId.fold(BSONDocument.empty){ id => BSONDocument("$set" -> BSONDocument("action.messageId" -> id))}
    val emailInsert = registration.email.fold(BSONDocument.empty){ found => BSONDocument("$set" -> BSONDocument("action.email" -> registration.email))}

    val update = BSONDocument("$set" -> BSONDocument("action.text" -> registration.text))
    // TODO...Add unique time when record is updated!!!
    // Initial timestamp on insert is used to order messages.
    //BSONDocument("$set" -> BSONDocument("timestamp" -> DateTime.now().getMillis))

    message ++ messageId ++ emailInsert ++ update
  }

  def insert(clientId:String, localId:String, roomId:String, message: SparkMessage): Future[DatabaseUpdate[SparkMessagePersist]] = {
    atomicUpsert(findMessageById(localId, message), modifierForInsertOrUpdate(message, clientId, localId, roomId))
  }

  def findMessages(clientId:String, roomId:String, timestamp:Option[Long], lastId:Option[String]): Future[Seq[SparkMessagePersist]] = {
    val messageTypeQuery = BSONDocument(s"clientId" -> clientId) ++ BSONDocument(s"roomId" -> roomId) ++ BSONDocument("action.email" -> BSONDocument("$exists" -> true))
    val timestampQuery = timestamp.fold(BSONDocument.empty)(time => BSONDocument("timestamp" -> BSONDocument("$gte" -> time)))
    val excludeRecord = lastId.fold(BSONDocument.empty)(found => BSONDocument("action.messageId" -> BSONDocument("$ne" -> found)))

    collection.find(messageTypeQuery ++ timestampQuery ++ excludeRecord)
      .sort(Json.obj("timestamp" -> 1))
      .cursor[SparkMessagePersist](ReadPreference.primaryPreferred)
      .collect[Seq]()
  }
}

@ImplementedBy(classOf[MessageMongoRepository])
trait MessageRepository extends Repository[SparkMessagePersist, BSONObjectID] {

  def insert(clientId:String, localId:String, roomId:String, message: SparkMessage): Future[DatabaseUpdate[SparkMessagePersist]]

  def findMessages(clientId:String, roomId:String, timestamp:Option[Long], lastId:Option[String]) : Future[Seq[SparkMessagePersist]]
}
