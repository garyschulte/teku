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
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Watches zkboost's shared {@code GET /v1/execution_proof_requests} SSE stream (filtered
 * server-side to a single {@code new_payload_request_root}) for the {@code "proof_complete"} /
 * {@code "proof_failure"} named events documented in {@code eth-act/lighthouse}'s {@code
 * beacon_node/execution_layer/src/eip8025/types.rs}. The event itself only carries a
 * root/proof_type notification, not the proof bytes - {@link RestExecutionProofProverClient}
 * fetches those separately via {@code GET /v1/execution_proofs/{root}/{proof_type}} once notified.
 * Extracted into its own class so the event-parsing logic is unit-testable without a live SSE
 * connection (mirrors the existing {@code EventSourceHandler} pattern).
 */
class ExecutionProofResultEventHandler implements BackgroundEventHandler {

  private static final Logger LOG = LogManager.getLogger();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Bytes32 expectedRoot;
  private final Runnable onProofComplete;
  private final Consumer<Throwable> onFailure;
  private final Runnable onEventProcessed;

  ExecutionProofResultEventHandler(
      final Bytes32 expectedRoot,
      final Runnable onProofComplete,
      final Consumer<Throwable> onFailure,
      final Runnable onEventProcessed) {
    this.expectedRoot = expectedRoot;
    this.onProofComplete = onProofComplete;
    this.onFailure = onFailure;
    this.onEventProcessed = onEventProcessed;
  }

  @Override
  public void onOpen() {}

  @Override
  public void onClosed() {}

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) {
    switch (event) {
      case "proof_complete" -> handleProofComplete(messageEvent.getData());
      case "proof_failure" -> handleProofFailure(messageEvent.getData());
      default -> LOG.trace("Ignoring unexpected zkboost SSE event '{}'", event);
    }
  }

  @Override
  public void onComment(final String comment) {}

  @Override
  public void onError(final Throwable throwable) {
    onFailure.accept(throwable);
    onEventProcessed.run();
  }

  private void handleProofComplete(final String data) {
    if (!matchesExpectedRoot(data)) {
      return;
    }
    onProofComplete.run();
    onEventProcessed.run();
  }

  private void handleProofFailure(final String data) {
    if (!matchesExpectedRoot(data)) {
      return;
    }
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(data);
      final String reason = node.path("reason").asText("unknown");
      final String error = node.path("error").asText("");
      onFailure.accept(
          new RuntimeException(
              "Execution proof request failed - reason: " + reason + ", error: " + error));
    } catch (final IOException e) {
      onFailure.accept(e);
    }
    onEventProcessed.run();
  }

  private boolean matchesExpectedRoot(final String data) {
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(data);
      final Bytes32 root = Bytes32.fromHexString(node.path("new_payload_request_root").asText());
      return root.equals(expectedRoot);
    } catch (final IOException | IllegalArgumentException e) {
      LOG.debug("Failed to parse zkboost SSE event '{}'", data, e);
      return false;
    }
  }
}
