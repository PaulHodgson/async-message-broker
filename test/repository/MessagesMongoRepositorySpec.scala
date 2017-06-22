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
import uk.gov.hmrc.asyncmessagebroker.repository.{SparkMessageRepository}
import scala.concurrent.ExecutionContext.Implicits.global

class MessagesMongoRepositorySpec extends UnitSpec with ScalaFutures with WithTestApplication with BeforeAndAfterEach {

  lazy val sparkMessageRepository = fakeApplication.injector.instanceOf[SparkMessageRepository]

    override def beforeEach() {
      implicit val patienceConfig = PatienceConfig(scaled(Span(500, Millis)), scaled(Span(15, Millis)))
      await(sparkMessageRepository.removeAll())
    }

  "Saving messages " should {

    "only insert 1 record for a message Id, subsequent attempt to insert same record will force update" in { //in new Setup {

      await(sparkMessageRepository.insert("1","2","3",SparkMessage("Message ABC", Some("1"), None)))
      await(sparkMessageRepository.insert("1","2","3",SparkMessage("Message2", Some("1"), None)))
      val allRecords = await(sparkMessageRepository.findAll())

      allRecords.size shouldBe 1
    }
  }
  

}
