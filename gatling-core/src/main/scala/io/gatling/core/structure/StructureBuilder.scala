/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
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

package io.gatling.core.structure

import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.controller.inject.InjectionProfileFactory

/**
 * This trait defines most of the scenario related DSL
 */
sealed trait StructureBuilder[B <: StructureBuilder[B]]
    extends Execs[B]
    with Pauses[B]
    with Feeds[B]
    with Loops[B]
    with ConditionalStatements[B]
    with Errors[B]
    with Groups[B]

/**
 * This class defines chain related methods
 *
 * @param actionBuilders the builders that represent the chain of actions of a scenario/chain
 */
final case class ChainBuilder(actionBuilders: List[ActionBuilder]) extends StructureBuilder[ChainBuilder] with BuildAction {

  override protected def chain(newActionBuilders: Seq[ActionBuilder]): ChainBuilder =
    ChainBuilder(newActionBuilders.toList ::: actionBuilders)
}

/**
 * The scenario builder is used in the DSL to define the scenario
 *
 * @param name the name of the scenario
 * @param actionBuilders the list of all the actions that compose the scenario
 */
final case class ScenarioBuilder(name: String, actionBuilders: List[ActionBuilder]) extends StructureBuilder[ScenarioBuilder] with BuildAction {

  override protected def chain(newActionBuilders: Seq[ActionBuilder]): ScenarioBuilder =
    copy(actionBuilders = newActionBuilders.toList ::: actionBuilders)

  def inject[T: InjectionProfileFactory](is: T, moreIss: T*): PopulationBuilder = inject[T](Seq(is) ++ moreIss)

  def inject[T: InjectionProfileFactory](iss: Iterable[T]): PopulationBuilder = {
    require(iss.nonEmpty, "Calling inject with empty injection steps")
    PopulationBuilder(
      scenarioBuilder = this,
      injectionProfile = implicitly[InjectionProfileFactory[T]].profile(iss),
      scenarioProtocols = Map.empty,
      scenarioThrottleSteps = Nil,
      pauseType = None,
      children = Nil
    )
  }
}

private[gatling] trait StructureSupport extends StructureBuilder[ChainBuilder] {

  override protected def actionBuilders: List[ActionBuilder] = Nil

  override protected def chain(newActionBuilders: Seq[ActionBuilder]): ChainBuilder =
    ChainBuilder(newActionBuilders.toList)
}
