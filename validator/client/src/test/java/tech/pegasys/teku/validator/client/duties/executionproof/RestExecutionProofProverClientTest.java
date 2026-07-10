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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class RestExecutionProofProverClientTest {

  private final MockWebServer mockWebServer = new MockWebServer();
  private RestExecutionProofProverClient client;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer.start();
    client =
        new RestExecutionProofProverClient(
            new OkHttpClient.Builder().readTimeout(Duration.ofMillis(500)).build(),
            mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void requestProof_submitsBlockBytesAsOctetStreamWithCorrectQueryParams() throws Exception {
    final Bytes32 newPayloadRequestRoot = Bytes32.fromHexStringLenient("0x01");
    final Bytes blockSsz = Bytes.fromHexString("0xabcdef");
    mockWebServer.enqueue(new MockResponse().setResponseCode(202));
    // second request is the SSE GET the client opens after a successful submission - respond with
    // a plain 404 so the background event source's onError fires quickly rather than idling.
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    final SafeFuture<Bytes> ignored = client.requestProof(newPayloadRequestRoot, 3, blockSsz);

    final RecordedRequest submitRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
    assertThat(submitRequest).isNotNull();
    assertThat(submitRequest.getMethod()).isEqualTo("POST");
    assertThat(submitRequest.getPath())
        .startsWith("/v1/execution_proof_requests")
        .contains("new_payload_request_root=" + newPayloadRequestRoot.toHexString())
        .contains("proof_type=3");
    assertThat(submitRequest.getBody().readByteArray()).isEqualTo(blockSsz.toArrayUnsafe());
  }

  @Test
  void requestProof_failsTheFutureWhenSubmissionIsRejected() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    final SafeFuture<Bytes> result =
        client.requestProof(Bytes32.fromHexStringLenient("0x01"), 0, Bytes.fromHexString("0x0123"));

    final boolean threw = result.handle((value, error) -> error != null).join();
    assertThat(threw).isTrue();
  }

  @Test
  void requestProof_failsTheFutureWhenServiceIsUnreachable() throws Exception {
    mockWebServer.shutdown();

    final SafeFuture<Bytes> result =
        client.requestProof(Bytes32.fromHexStringLenient("0x01"), 0, Bytes.fromHexString("0x0123"));

    final boolean threw = result.handle((value, error) -> error != null).join();
    assertThat(threw).isTrue();
  }
}
