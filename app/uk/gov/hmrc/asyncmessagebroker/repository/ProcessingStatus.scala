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

import play.api.libs.json._

trait ProcessingStatus

object ProcessingStatus {
  val queued = "queued"
  val processing = "processing"
  val failed = "failed"
  val complete = "complete"

  case object Queued extends ProcessingStatus {
    override def toString: String = queued
  }

  case object Processing extends ProcessingStatus {
    override def toString: String = processing
  }

  case object Failed extends ProcessingStatus {
    override def toString: String = failed
  }

  case object Complete extends ProcessingStatus {
    override def toString: String = complete
  }

  val reads: Reads[ProcessingStatus] = new Reads[ProcessingStatus] {
    override def reads(json: JsValue): JsResult[ProcessingStatus] = json match {
      case JsString(ProcessingStatus.queued) => JsSuccess(Queued)
      case JsString(ProcessingStatus.`processing`) => JsSuccess(Processing)
      case JsString(ProcessingStatus.failed) => JsSuccess(Failed)
      case JsString(ProcessingStatus.complete) => JsSuccess(Complete)
      case _ => JsError(s"Failed to resolve $json")
    }
  }

  val writes: Writes[ProcessingStatus] = new Writes[ProcessingStatus] {
    override def writes(status: ProcessingStatus): JsString = status match {
      case Queued => JsString(queued)
      case Processing => JsString(processing)
      case Failed => JsString(failed)
      case Complete => JsString(complete)
    }
  }

  implicit val formats = Format(ProcessingStatus.reads, ProcessingStatus.writes)
}
