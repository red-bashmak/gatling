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

package io.gatling.javaapi.http;

import io.gatling.core.action.builder.ActionBuilder;
import java.util.function.Function;

/**
 * DSL for building SSE connect actions
 *
 * <p>Immutable, so all methods return a new occurrence and leave the original unmodified.
 */
public final class SseConnectActionBuilder
    extends RequestWithBodyActionBuilder<
        SseConnectActionBuilder, io.gatling.http.request.builder.sse.SseConnectRequestBuilder>
    implements SseAwaitActionBuilder<
        SseConnectActionBuilder, io.gatling.http.request.builder.sse.SseConnectRequestBuilder> {

  SseConnectActionBuilder(io.gatling.http.request.builder.sse.SseConnectRequestBuilder wrapped) {
    super(wrapped);
  }

  @Override
  public SseConnectActionBuilder make(
      Function<
              io.gatling.http.request.builder.sse.SseConnectRequestBuilder,
              io.gatling.http.request.builder.sse.SseConnectRequestBuilder>
          f) {
    return new SseConnectActionBuilder(f.apply(wrapped));
  }

  @Override
  public ActionBuilder asScala() {
    return wrapped;
  }
}
