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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class ExecutionProofResultEventHandlerTest {

  private final SafeFuture<Bytes> result = new SafeFuture<>();
  private final AtomicBoolean eventProcessed = new AtomicBoolean(false);
  private final ExecutionProofResultEventHandler handler =
      new ExecutionProofResultEventHandler(result, () -> eventProcessed.set(true));

  @Test
  void onMessage_completesResultWithParsedProofData() {
    handler.onMessage("message", messageEvent("{\"proof_data\": \"0x0123\"}"));

    assertThat(result).isCompletedWithValue(Bytes.fromHexString("0x0123"));
    assertThat(eventProcessed).isTrue();
  }

  @Test
  void onMessage_completesExceptionallyOnMalformedJson() {
    handler.onMessage("message", messageEvent("not json"));

    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThat(eventProcessed).isTrue();
  }

  @Test
  void onMessage_completesExceptionallyWhenProofDataIsNotValidHex() {
    handler.onMessage("message", messageEvent("{\"proof_data\": \"not hex\"}"));

    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThat(eventProcessed).isTrue();
  }

  @Test
  void onError_completesResultExceptionally() {
    final RuntimeException error = new RuntimeException("connection reset");

    handler.onError(error);

    assertThat(result).isCompletedExceptionally();
    assertThat(eventProcessed).isTrue();
  }

  private MessageEvent messageEvent(final String data) {
    return new MessageEvent(data);
  }
}
