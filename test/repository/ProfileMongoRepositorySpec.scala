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
import uk.gov.hmrc.asyncmessagebroker.repository.{ProfilePersist, ProfileRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class ProfileMongoRepositorySpec extends UnitSpec with ScalaFutures with WithTestApplication with BeforeAndAfterEach {

  lazy val profileRepository = fakeApplication.injector.instanceOf[ProfileRepository]

  override def beforeEach() {
    implicit val patienceConfig = PatienceConfig(scaled(Span(500, Millis)), scaled(Span(15, Millis)))
    await(profileRepository.removeAll())
  }

  "Saving profiles " should {

    "allow only 1 client be associated to 1 roomId" in {

      await(profileRepository.insert("1", "2", "3", "4"))
      await(profileRepository.insert("1", "2", "3", "4"))
      val allRecords = await(profileRepository.findAll())
      allRecords.size shouldBe 1
      val record: ProfilePersist = allRecords(0)
      record.clientId shouldBe "1"
      record.roomId shouldBe "2"
      record.roomName shouldBe "3"
      record.email shouldBe "4"
    }

    "find a record by room name" in {
      await(profileRepository.insert("1", "2", "3", "4"))
      await(profileRepository.insert("a", "b", "c", "d"))
      val record = await(profileRepository.findProfileByRoomName("3")).get
      record.clientId shouldBe "1"
      record.roomId shouldBe "2"
      record.roomName shouldBe "3"
      record.email shouldBe "4"
    }
  }
}
