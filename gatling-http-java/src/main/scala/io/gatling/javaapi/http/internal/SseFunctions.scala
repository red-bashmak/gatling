/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.javaapi.http.internal

import java.{ util => ju }
import java.util.{ function => juf }

import scala.jdk.CollectionConverters._

import io.gatling.commons.validation._
import io.gatling.core.session.{ Session => ScalaSession }
import io.gatling.http.action.sse.SseInboundMessage
import io.gatling.javaapi.core.Session

object SseFunctions {
  def javaProcessUnmatchedMessagesBiFunctionToExpression(
      f: juf.BiFunction[ju.List[SseInboundMessage], Session, Session]
  ): (List[SseInboundMessage], ScalaSession) => Validation[ScalaSession] =
    (messages, session) => safely()(f.apply(messages.asJava, new Session(session)).asScala().success)
}
