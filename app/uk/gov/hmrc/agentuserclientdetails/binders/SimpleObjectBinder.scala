/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentuserclientdetails.binders

import play.api.mvc.PathBindable

import scala.reflect.ClassTag
import scala.util.control.NonFatal

class SimpleObjectBinder[T](
  bind: String => T,
  unbind: T => String
)(implicit m: ClassTag[T])
extends PathBindable[T] {

  override def bind(
    key: String,
    value: String
  ): Either[String, T] =
    try Right(bind(value))
    catch {
      case NonFatal(_) => Left(s"Cannot parse parameter '$key' with value '$value' as '${m.runtimeClass.getSimpleName}'")
    }

  def unbind(
    key: String,
    value: T
  ): String = unbind(value)

}
