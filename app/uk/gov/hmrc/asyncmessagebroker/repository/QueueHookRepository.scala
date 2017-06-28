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

import javax.inject.{Named, Inject, Singleton}

import com.google.inject.ImplementedBy
import org.joda.time.DateTime
import play.api.libs.json.{JsNumber, Format, Json}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.asyncmessagebroker.domain.Webhook
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class GroupStatusUpdate(status:ProcessingStatus, items:Seq[BSONObjectID])

case class WebhookPersist(id: BSONObjectID, timestamp: Long, status:ProcessingStatus, attempts:Int, hook:Webhook)

object WebhookPersist {
  val mongoFormats: Format[WebhookPersist] = ReactiveMongoFormats.mongoEntity({
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[WebhookPersist], Json.writes[WebhookPersist])
  })
}

@Singleton
class QueueHookMongoRepository @Inject()(mongo: DB, @Named("maxRetryAttempts") maxAttempts: Int)
  extends ReactiveRepository[WebhookPersist, BSONObjectID]("hookqueue", () => mongo, WebhookPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
  with AtomicUpdate[WebhookPersist]
  with WebhookPersistRepository
  with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[scala.Seq[Boolean]] = {

    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("hook.data.id" -> IndexType.Ascending), name = Some("HookDataIdUnique"), unique = true)),
        collection.indexesManager.ensure(
        Index(Seq("hook.data.roomId" -> IndexType.Ascending), name = Some("HookDataRoomIdNotUnique"), unique = false))
      )
    )
  }

  override def isInsertion(suppliedId: BSONObjectID, returned: WebhookPersist): Boolean =
    suppliedId.equals(returned.id)

  protected def findById(id:String) = BSONDocument("hook.data.id" -> id)

  private def modifierForInsert(webhook:Webhook): BSONDocument = {
    BSONDocument(
      "$setOnInsert" -> BSONDocument("status" -> ProcessingStatus.queued),
      "$setOnInsert" -> BSONDocument("attempts" -> 0),
      "$setOnInsert" -> BSONDocument("timestamp" -> DateTime.now().getMillis),
      "$setOnInsert" -> BSONDocument("hook" -> Json.toJson(webhook))
    )
  }

  def insert(webhook:Webhook): Future[DatabaseUpdate[WebhookPersist]] = {
    atomicUpsert(findById(webhook.id), modifierForInsert(webhook))
  }

  override def getQueued(maxBatchSize: Int): Future[Seq[WebhookPersist]] = {

    def queued = {
      collection.find(BSONDocument(
        "$and" -> BSONArray(
          BSONDocument("status" -> ProcessingStatus.queued),
          BSONDocument("attempts" -> BSONDocument("$lt" -> maxAttempts))
        )
      )).
        sort(Json.obj("timestamp" -> JsNumber(-1))).cursor[WebhookPersist](ReadPreference.primaryPreferred).
        collect[List](maxBatchSize)
    }

    processBatch(queued, ProcessingStatus.Queued)
  }

  def updateGroup(update:GroupStatusUpdate): Future[UpdateWriteResult] = {

    collection.update(
      BSONDocument("_id" -> BSONDocument("$in" -> update.items.foldLeft(BSONArray())((a, p) => a.add(p)))),
      BSONDocument(
        "$set" -> BSONDocument(
          "updated" -> BSONDateTime(DateTimeUtils.now.getMillis),
          "status" -> update.status.toString
        )
      ),
      upsert = false,
      multi = true
    )
  }

  def processBatch(batch: Future[List[WebhookPersist]], status:ProcessingStatus) : Future[Seq[WebhookPersist]] = {
    def setSent(batch: List[WebhookPersist]) = {

      val withAttempts = BSONDocument("$inc" -> BSONDocument("attempts" -> 1))

      collection.update(
        BSONDocument("_id" -> BSONDocument("$in" -> batch.foldLeft(BSONArray())((a, p) => a.add(p.id)))),
        BSONDocument(
          "$set" -> BSONDocument(
            "updated" -> BSONDateTime(DateTimeUtils.now.getMillis),
            "status" -> ProcessingStatus.processing
          )
        ) ++ withAttempts,
        upsert = false,
        multi = true
      )
    }

    def getBatchOrFailed(batch: List[WebhookPersist], updateWriteResult: UpdateWriteResult) = {
      if (updateWriteResult.ok) {
        Future.successful(batch.map(n => n.copy(attempts = n.attempts + 1)))
      } else {
        Future.failed(new ReactiveMongoException {
          override def message: String = "failed to fetch unsent notifications"
        })
      }
    }

    for (
      notifications <- batch;
      updateResult <- setSent(notifications);
      unsentNotifications <- getBatchOrFailed(notifications, updateResult)
    ) yield unsentNotifications
  }

}

@ImplementedBy(classOf[QueueHookMongoRepository])
trait WebhookPersistRepository extends Repository[WebhookPersist, BSONObjectID] {
  def insert(webhook:Webhook): Future[DatabaseUpdate[WebhookPersist]]

  def updateGroup(update:GroupStatusUpdate): Future[UpdateWriteResult]

  def getQueued(maxBatchSize: Int): Future[Seq[WebhookPersist]]
}
