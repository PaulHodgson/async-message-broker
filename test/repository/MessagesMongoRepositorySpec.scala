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

package repository

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import support.WithTestApplication
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.asyncmessagebroker.domain.SparkMessage
import uk.gov.hmrc.asyncmessagebroker.repository.{SparkMessagePersist, MessageRepository}
import scala.concurrent.ExecutionContext.Implicits.global

class MessagesMongoRepositorySpec extends UnitSpec with ScalaFutures with WithTestApplication with BeforeAndAfterEach {

  lazy val sparkMessageRepository = fakeApplication.injector.instanceOf[MessageRepository]

    override def beforeEach() {
      implicit val patienceConfig = PatienceConfig(scaled(Span(500, Millis)), scaled(Span(15, Millis)))
      await(sparkMessageRepository.removeAll())
    }

  "Saving messages " should {

    "only insert 1 record for a unique message Id, subsequent attempt to insert same message Id will force update" in {

      await(sparkMessageRepository.insert("1", "2", "3", SparkMessage("Message ABC", Some("1"), None)))
      await(sparkMessageRepository.insert("1", "2", "3", SparkMessage("Message updated", Some("1"), None)))

      val allRecords = await(sparkMessageRepository.findAll())

      allRecords.size shouldBe 1
      allRecords(0).clientId shouldBe "1"
      allRecords(0).localId shouldBe "2"
      allRecords(0).roomId shouldBe "3"
      allRecords(0).action.text shouldBe "Message updated"
    }

    "find the record from clientId and roomId" in {
      await(sparkMessageRepository.insert("1", "2", "3", SparkMessage("Message ABC", Some("1"), Some("somewhere@something.com"))))
      val found: Seq[SparkMessagePersist] = await(sparkMessageRepository.findMessages("1", "3", None, None))

      found(0).clientId shouldBe "1"
      found(0).localId shouldBe "2"
      found(0).roomId shouldBe "3"
      found(0).action.text shouldBe "Message ABC"
    }

    "return only new records and not all messages" in {
      await(sparkMessageRepository.insert("1", "1", "3", SparkMessage("Message 1", Some("1"), Some("somewhere@something.com"))))
      await(sparkMessageRepository.insert("1", "2", "3", SparkMessage("Message 2", Some("2"), Some("somewhere@something.com"))))
      await(sparkMessageRepository.insert("1", "3", "3", SparkMessage("Message 3", Some("3"), Some("somewhere@something.com"))))

      val found: Seq[SparkMessagePersist] = await(sparkMessageRepository.findMessages("1", "3", None, None))
      val timestamp = found(2).timestamp
      val id: String = found(2).action.messageId.get

      await(sparkMessageRepository.insert("1", "4", "3", SparkMessage("Message 4", Some("4"), Some("somewhere@something.com"))))
      await(sparkMessageRepository.insert("1", "5", "3", SparkMessage("Message 5", Some("5"), Some("somewhere@something.com"))))

      val foundLatest: Seq[SparkMessagePersist] = await(sparkMessageRepository.findMessages("1", "3", Some(timestamp), Some(id)))

      foundLatest.size shouldBe 2

      foundLatest(0).clientId shouldBe "1"
      foundLatest(0).localId shouldBe "4"
      foundLatest(0).roomId shouldBe "3"
      foundLatest(0).action.text shouldBe "Message 4"
      foundLatest(1).clientId shouldBe "1"
      foundLatest(1).localId shouldBe "5"
      foundLatest(1).roomId shouldBe "3"
      foundLatest(1).action.text shouldBe "Message 5"
    }

    "not find the record from clientId and roomId" in {
      await(sparkMessageRepository.insert("1", "2", "3", SparkMessage("Message ABC", Some("1"), None)))
      val found: Seq[SparkMessagePersist] = await(sparkMessageRepository.findMessages("3", "2", None, None))

      found shouldBe List.empty
    }

  }

}
