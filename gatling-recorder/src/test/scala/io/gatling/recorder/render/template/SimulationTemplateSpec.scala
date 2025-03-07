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

package io.gatling.recorder.render.template

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class SimulationTemplateSpec extends AnyFlatSpecLike with Matchers {
  "renderNonBaseUrls template" should "generate empty string if no variables" in {
    SimulationTemplate.renderNonBaseUrls(Nil, RenderingFormat.Scala) shouldBe empty
  }

  it should "list variables" in {
    val raw = SimulationTemplate.renderNonBaseUrls(Seq(UrlVal("name1", "url1"), UrlVal("name2", "url2")), RenderingFormat.Scala)
    raw.linesIterator.map(_.trim).filter(_.nonEmpty).toList shouldBe List(
      s"""private val name1 = ${"url1".protect(RenderingFormat.Scala)}""",
      s"""private val name2 = ${"url2".protect(RenderingFormat.Scala)}"""
    )
  }
}
