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

import static org.assertj.core.api.Assertions.assertThat;

import com.launchdarkly.eventsource.MessageEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class ExecutionProofResultEventHandlerTest {

  private final Bytes32 expectedRoot = Bytes32.fromHexStringLenient("0x01");
  private final AtomicBoolean proofCompleted = new AtomicBoolean(false);
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final AtomicBoolean eventProcessed = new AtomicBoolean(false);

  private final ExecutionProofResultEventHandler handler =
      new ExecutionProofResultEventHandler(
          expectedRoot,
          () -> proofCompleted.set(true),
          failure::set,
          () -> eventProcessed.set(true));

  @Test
  void onMessage_proofComplete_forExpectedRootTriggersCallback() {
    handler.onMessage(
        "proof_complete", messageEvent("{\"new_payload_request_root\": \"" + expectedRoot + "\"}"));

    assertThat(proofCompleted).isTrue();
    assertThat(eventProcessed).isTrue();
    assertThat(failure).hasValue(null);
  }

  @Test
  void onMessage_proofComplete_forDifferentRootIsIgnored() {
    final Bytes32 otherRoot = Bytes32.fromHexStringLenient("0x02");
    handler.onMessage(
        "proof_complete", messageEvent("{\"new_payload_request_root\": \"" + otherRoot + "\"}"));

    assertThat(proofCompleted).isFalse();
    assertThat(eventProcessed).isFalse();
  }

  @Test
  void onMessage_proofFailure_forExpectedRootCompletesExceptionally() {
    handler.onMessage(
        "proof_failure",
        messageEvent(
            "{\"new_payload_request_root\": \""
                + expectedRoot
                + "\", \"reason\": \"ProvingError\", \"error\": \"boom\"}"));

    assertThat(proofCompleted).isFalse();
    assertThat(failure.get()).isNotNull();
    assertThat(failure.get().getMessage()).contains("ProvingError").contains("boom");
    assertThat(eventProcessed).isTrue();
  }

  @Test
  void onMessage_unrelatedEventNameIsIgnored() {
    handler.onMessage("some_other_event", messageEvent("{}"));

    assertThat(proofCompleted).isFalse();
    assertThat(failure).hasValue(null);
    assertThat(eventProcessed).isFalse();
  }

  @Test
  void onError_completesExceptionallyAndClosesTheStream() {
    final RuntimeException error = new RuntimeException("connection reset");

    handler.onError(error);

    assertThat(failure).hasValue(error);
    assertThat(eventProcessed).isTrue();
  }

  private MessageEvent messageEvent(final String data) {
    return new MessageEvent(data);
  }
}
