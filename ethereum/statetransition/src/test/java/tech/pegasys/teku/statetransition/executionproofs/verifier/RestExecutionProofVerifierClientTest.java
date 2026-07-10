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

package tech.pegasys.teku.statetransition.executionproofs.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestExecutionProofVerifierClientTest {

  private final MockWebServer mockWebServer = new MockWebServer();
  private RestExecutionProofVerifierClient client;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer.start();
    client =
        new RestExecutionProofVerifierClient(new OkHttpClient(), mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void returnsTrueWhenServiceReportsValid() throws Exception {
    mockWebServer.enqueue(
        new MockResponse().setBody("{\"status\": \"VALID\"}").setResponseCode(200));
    final Bytes32 newPayloadRequestRoot = Bytes32.fromHexStringLenient("0x01");
    final Bytes proofData = Bytes.fromHexString("0x0123");

    final boolean result = client.verify(newPayloadRequestRoot, 3, proofData).join();

    assertThat(result).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .startsWith("/v1/execution_proof_verifications")
        .contains("new_payload_request_root=" + newPayloadRequestRoot.toHexString())
        .contains("proof_type=3");
    assertThat(recordedRequest.getBody().readByteArray()).isEqualTo(proofData.toArrayUnsafe());
  }

  @Test
  void returnsFalseWhenServiceReportsInvalid() {
    mockWebServer.enqueue(
        new MockResponse().setBody("{\"status\": \"INVALID\"}").setResponseCode(200));

    final boolean result =
        client
            .verify(Bytes32.fromHexStringLenient("0x01"), 0, Bytes.fromHexString("0x0123"))
            .join();

    assertThat(result).isFalse();
  }

  @Test
  void returnsFalseOnHttpErrorResponse() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    final boolean result =
        client
            .verify(Bytes32.fromHexStringLenient("0x01"), 0, Bytes.fromHexString("0x0123"))
            .join();

    assertThat(result).isFalse();
  }

  @Test
  void failsTheFutureWhenServiceIsUnreachable() throws Exception {
    mockWebServer.shutdown();

    final boolean threw =
        client
            .verify(Bytes32.fromHexStringLenient("0x01"), 0, Bytes.fromHexString("0x0123"))
            .exceptionally(error -> true)
            .join();

    assertThat(threw).isTrue();
  }
}
