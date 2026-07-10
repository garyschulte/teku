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

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * HTTP+SSE client for a zkboost-shaped external prover service: POSTs the block's raw SSZ bytes to
 * {@code {endpoint}/v1/execution_proof_requests?new_payload_request_root=...&proof_type=...} to
 * kick off proving, then opens a single-use SSE stream at {@code
 * {endpoint}/v1/execution_proof_requests/{root}/{proofType}/events} to receive the resulting proof
 * bytes once the (potentially long-running) prover completes. This has NOT been cross-verified
 * against zkboost's actual request/response schema - treat as best-effort pending such
 * verification, matching the same caveat already applied to {@code
 * RestExecutionProofVerifierClient} (see EIP-8025 gap-analysis M6/M8 notes).
 */
public class RestExecutionProofProverClient implements ExecutionProofProverClient {

  private static final MediaType OCTET_STREAM_MEDIA_TYPE =
      MediaType.parse("application/octet-stream");

  private final OkHttpClient httpClient;
  private final HttpUrl requestsUrl;

  public RestExecutionProofProverClient(final OkHttpClient httpClient, final String endpoint) {
    this.httpClient = httpClient;
    this.requestsUrl =
        HttpUrl.get(endpoint).newBuilder().addPathSegments("v1/execution_proof_requests").build();
  }

  @Override
  public SafeFuture<Bytes> requestProof(
      final Bytes32 newPayloadRequestRoot, final int proofType, final Bytes blockSsz) {
    final SafeFuture<Bytes> result = new SafeFuture<>();
    final HttpUrl submitUrl =
        requestsUrl
            .newBuilder()
            .addQueryParameter("new_payload_request_root", newPayloadRequestRoot.toHexString())
            .addQueryParameter("proof_type", Integer.toString(proofType))
            .build();
    final Request request =
        new Request.Builder()
            .url(submitUrl)
            .post(RequestBody.create(blockSsz.toArrayUnsafe(), OCTET_STREAM_MEDIA_TYPE))
            .build();

    httpClient
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(final Call call, final IOException e) {
                result.completeExceptionally(e);
              }

              @Override
              public void onResponse(final Call call, final Response response) {
                try (Response ignored = response) {
                  if (!response.isSuccessful()) {
                    result.completeExceptionally(
                        new RuntimeException(
                            "Execution proof request to "
                                + submitUrl
                                + " was rejected with status "
                                + response.code()));
                    return;
                  }
                  openResultEventStream(newPayloadRequestRoot, proofType, result);
                }
              }
            });
    return result;
  }

  private void openResultEventStream(
      final Bytes32 newPayloadRequestRoot, final int proofType, final SafeFuture<Bytes> result) {
    final HttpUrl eventsUrl =
        requestsUrl
            .newBuilder()
            .addPathSegment(newPayloadRequestRoot.toHexString())
            .addPathSegment(Integer.toString(proofType))
            .addPathSegment("events")
            .build();
    final AtomicReference<BackgroundEventSource> eventSourceRef = new AtomicReference<>();
    final BackgroundEventSource eventSource =
        new BackgroundEventSource.Builder(
                new ExecutionProofResultEventHandler(
                    result, () -> closeQuietly(eventSourceRef.get())),
                new EventSource.Builder(ConnectStrategy.http(eventsUrl).httpClient(httpClient)))
            .build();
    eventSourceRef.set(eventSource);
    eventSource.start();
  }

  private void closeQuietly(final BackgroundEventSource eventSource) {
    if (eventSource != null) {
      eventSource.close();
    }
  }
}
