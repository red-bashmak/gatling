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

package io.gatling.http.action.sse

import io.gatling.commons.validation._
import io.gatling.core.session.Session
import io.gatling.http.action.sse.fsm.SseFsm

trait SseAction {
  final def fetchFsm(sseName: String, session: Session): Validation[SseFsm] =
    session.attributes.get(sseName) match {
      case Some(sseFsm) => sseFsm.asInstanceOf[SseFsm].success
      case _            => "Couldn't fetch open sse".failure
    }
}
