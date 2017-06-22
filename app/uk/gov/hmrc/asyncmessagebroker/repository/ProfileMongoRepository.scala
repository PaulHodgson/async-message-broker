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
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class ProfilePersist(id: BSONObjectID, timestamp: Long, clientId:String, roomId:String, email:String)

object ProfilePersist {
  val mongoFormats: Format[ProfilePersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[ProfilePersist], Json.writes[ProfilePersist])
  })
}

@Singleton
class ProfileMongoRepository @Inject()(mongo: DB)
  extends ReactiveRepository[ProfilePersist, BSONObjectID]("profile", () => mongo, ProfilePersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
  with AtomicUpdate[ProfilePersist]
  with ProfileRepository
  with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[scala.Seq[Boolean]] = {

    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("clientId" -> IndexType.Ascending), name = Some("clientIdNotUnique"), unique = false)),
        collection.indexesManager.ensure(
          Index(Seq("roomId" -> IndexType.Ascending), name = Some("roomIdUnique"), unique = true)),
        collection.indexesManager.ensure(
          Index(Seq("email" -> IndexType.Ascending), name = Some("emailNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(suppliedId: BSONObjectID, returned: ProfilePersist): Boolean =
    suppliedId.equals(returned.id)

  protected def findByClientIdAndRoomId(clientId:String, roomId:String) =
    BSONDocument("clientId" -> clientId) ++ BSONDocument("roomId" -> roomId)

  private def modifierForInsert(clientId:String, roomId:String, email:String): BSONDocument = {
    BSONDocument(
      "$setOnInsert" -> BSONDocument("clientId" -> clientId),
      "$setOnInsert" -> BSONDocument("roomId" -> roomId),
      "$setOnInsert" -> BSONDocument("email" -> email),
      "$setOnInsert" -> BSONDocument("timestamp" -> DateTime.now().getMillis)
    )
  }

  def insert(clientId:String, roomId:String, email:String): Future[DatabaseUpdate[ProfilePersist]] = {
    atomicUpsert(findByClientIdAndRoomId(clientId, roomId), modifierForInsert(clientId, roomId, email))
  }

  def findProfiles(): Future[List[ProfilePersist]] = {
    val messageTypeQuery = Json.obj("timestamp" -> Json.obj("$gt" -> 1))

    collection.find(messageTypeQuery)
      .sort(Json.obj("timestamp" -> 1))
      .cursor[ProfilePersist](ReadPreference.primaryPreferred)
      .collect[List]()
  }
}

@ImplementedBy(classOf[ProfileMongoRepository])
trait ProfileRepository extends Repository[ProfilePersist, BSONObjectID] {
  def insert(clientId:String, roomId:String, email:String): Future[DatabaseUpdate[ProfilePersist]]

  def findProfiles() : Future[List[ProfilePersist]]
}
