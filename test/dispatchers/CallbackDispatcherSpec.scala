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

package dispatchers

import com.kenshoo.play.metrics.Metrics
import reactivemongo.bson.BSONObjectID
import support.MockAnswer
import uk.gov.hmrc.asyncmessagebroker.dispatchers.CallbackDispatcher
import uk.gov.hmrc.asyncmessagebroker.domain.SparkMessage
import uk.gov.hmrc.asyncmessagebroker.repository.{GroupStatusUpdate, SparkMessagePersist, ProfilePersist}
import uk.gov.hmrc.asyncmessagebroker.service.{AsyncMessage, MessageService}
import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.mockito.Mockito._
import org.mockito.{Mockito, ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CallbackDispatcherSpec extends UnitSpec with ScalaFutures with MockitoSugar with OneAppPerSuite with MockAnswer {
  val mockMetrics = mock[Metrics]
  val mockMessageService = mock[MessageService]

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {

    val id1 = BSONObjectID.generate
    val id2 = BSONObjectID.generate

    val callback1Success = AsyncMessage("1", "a", Some(id1))
    val callback2Success = AsyncMessage("2", "b", Some(id2))
    val someCallbacks = List(callback1Success, callback2Success)
    val messagePersist = SparkMessagePersist(BSONObjectID.generate, 1, "1", "2", "3", SparkMessage("a", None, None))

    val profiles = Seq(ProfilePersist(BSONObjectID.generate, 1, "123", "abc", "name", "gg&gg.com"))
    val asyncMessage = Seq(callback1Success, callback2Success)

    val emptyCallbacks = List.empty

    val processMessageCaptor = ArgumentCaptor.forClass(classOf[AsyncMessage])
    val updateCaptor = ArgumentCaptor.forClass(classOf[Seq[GroupStatusUpdate]])

    val dispatcherHook = new CallbackDispatcher(4, false, mockMessageService, system, mockMetrics)
    val dispatcherRefresh = new CallbackDispatcher(4, true, mockMessageService, system, mockMetrics)
  }

  "scheduling the queue dispatcher" should {
    "process queued messages in hook mode" in new Setup {

      when(mockMessageService.getQueuedHookMesssages).thenAnswer(defineResult(Future.successful(someCallbacks)))
      when(mockMessageService.processMessage(ArgumentMatchers.any[AsyncMessage]())).thenAnswer(defineResult(Future.successful(Some(messagePersist))))

      await(dispatcherHook.processCallbacks())

      Eventually.eventually {
        verify(mockMessageService, times(2)).processMessage(processMessageCaptor.capture())
        val firstClientInvocation:AsyncMessage = processMessageCaptor.getAllValues.get(0)
        val secondClientInvocation:AsyncMessage = processMessageCaptor.getAllValues.get(1)
        firstClientInvocation shouldBe callback1Success
        secondClientInvocation shouldBe callback2Success
      }
    }

    "do nothing when there are no messages in hook mode" in new Setup {
      reset(mockMessageService)

      when(mockMessageService.getQueuedHookMesssages).thenAnswer(defineResult(Future.successful(emptyCallbacks)))

      await(dispatcherHook.processCallbacks())

      Eventually.eventually(
        Mockito.verify(mockMessageService, times(0)).processMessage(ArgumentMatchers.any[AsyncMessage]())
      )
    }
  }

  "scheduling the queue dispatcher in refresh mode " should {
    "process queued messages in refresh mode" in new Setup {
      reset(mockMessageService)

      when(mockMessageService.getProfiles).thenAnswer(defineResult(Future.successful(profiles)))
      when(mockMessageService.getMessagesForRoomId(ArgumentMatchers.any[String]())).thenAnswer(defineResult(Future.successful(asyncMessage)))
      when(mockMessageService.processMessage(ArgumentMatchers.any[AsyncMessage]())).thenAnswer(defineResult(Future.successful(Some(messagePersist))))

      await(dispatcherRefresh.processCallbacks())

      Eventually.eventually {

        verify(mockMessageService, times(2)).processMessage(processMessageCaptor.capture())
        val firstClientInvocation:AsyncMessage = processMessageCaptor.getAllValues.get(0)
        val secondClientInvocation:AsyncMessage = processMessageCaptor.getAllValues.get(1)
        firstClientInvocation shouldBe callback1Success
        secondClientInvocation shouldBe callback2Success

      }
    }

    "do nothing when there are no messages in refresh mode" in new Setup {
      reset(mockMessageService)

      when(mockMessageService.getProfiles).thenAnswer(defineResult(Future.successful(emptyCallbacks)))

      await(dispatcherRefresh.processCallbacks())

      Eventually.eventually(
        Mockito.verify(mockMessageService, times(0)).processMessage(ArgumentMatchers.any[AsyncMessage]())
      )
    }
  }
}
