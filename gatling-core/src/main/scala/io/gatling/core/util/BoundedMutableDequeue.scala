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

package io.gatling.core.util

import scala.collection.mutable

private[gatling] final class BoundedMutableDequeue[T](maxSize: Int) {
  private val queue = mutable.ArrayDeque.empty[T]

  def addOne(value: T): Unit =
    if (maxSize != 0) {
      queue.addOne(value)
      if (queue.lengthIs > maxSize) {
        queue.remove(0)
      }
    }

  def removeAll(): List[T] =
    if (maxSize == 0) {
      Nil
    } else {
      queue.removeAll().toList
    }

  def clear(): Unit =
    if (maxSize != 0) {
      queue.clear()
    }
}
