/*
 * Copyright Consensys Software Inc., 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.validator.client.duties.executionproof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Completes a pending proof-request future from a single zkboost-shaped SSE result event, shaped
 * like {@code {"proof_data": "0x..."}}. Extracted from {@link RestExecutionProofProverClient} so
 * the event-parsing logic can be unit tested directly, without a live SSE connection (mirroring
 * {@code EventSourceHandler}, which is tested the same way).
 */
class ExecutionProofResultEventHandler implements BackgroundEventHandler {

  private static final Logger LOG = LogManager.getLogger();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SafeFuture<Bytes> result;
  private final Runnable onEventProcessed;

  ExecutionProofResultEventHandler(
      final SafeFuture<Bytes> result, final Runnable onEventProcessed) {
    this.result = result;
    this.onEventProcessed = onEventProcessed;
  }

  @Override
  public void onOpen() {}

  @Override
  public void onClosed() {}

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) {
    completeFromEvent(messageEvent.getData());
    onEventProcessed.run();
  }

  @Override
  public void onComment(final String comment) {}

  @Override
  public void onError(final Throwable throwable) {
    result.completeExceptionally(throwable);
    onEventProcessed.run();
  }

  private void completeFromEvent(final String data) {
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(data);
      result.complete(Bytes.fromHexString(node.path("proof_data").asText()));
    } catch (final IOException | IllegalArgumentException e) {
      LOG.debug("Failed to parse execution proof result event '{}'", data, e);
      result.completeExceptionally(e);
    }
  }
}
