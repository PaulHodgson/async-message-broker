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

package support

import org.scalatest.Suite
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results

import scala.concurrent.ExecutionContext

trait ControllerSpec
  extends PlaySpec
    with ResettingMockitoSugar
    with AkkaMaterializerSpec
    with Results{ this: Suite =>


  implicit val ctx: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
}