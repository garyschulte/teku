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
import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ProofType;

/**
 * HTTP+SSE client for zkboost's real prover API, confirmed against {@code eth-act/lighthouse}'s
 * {@code optional-proofs} branch ({@code
 * beacon_node/execution_layer/src/eip8025/proof_node_client.rs}):
 *
 * <ol>
 *   <li>{@code POST /v1/execution_proof_requests?proof_types=<zkvm-identifier>} - body is the raw
 *       SSZ bytes of a {@code NewPayloadRequest} (see {@code
 *       NewPayloadRequestHasher#sszSerialize}), not a full signed block. Response JSON: {@code
 *       {"new_payload_request_root": "0x..."}}.
 *   <li>{@code GET /v1/execution_proof_requests?new_payload_request_root=<root>} - a shared SSE
 *       stream, filtered server-side to this root, that emits {@code "proof_complete"} / {@code
 *       "proof_failure"} named events once the (potentially long-running) prover finishes. The
 *       event itself is just a notification, not the proof bytes.
 *   <li>{@code GET /v1/execution_proofs/{root}/{zkvm-identifier}} - fetches the actual proof bytes
 *       once notified of completion.
 * </ol>
 */
public class RestExecutionProofProverClient implements ExecutionProofProverClient {

  private static final Logger LOG = LogManager.getLogger();
  private static final MediaType OCTET_STREAM_MEDIA_TYPE =
      MediaType.parse("application/octet-stream");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OkHttpClient httpClient;
  private final HttpUrl requestsUrl;
  private final HttpUrl proofsUrl;

  public RestExecutionProofProverClient(final OkHttpClient httpClient, final String endpoint) {
    this.httpClient = httpClient;
    final HttpUrl baseUrl = HttpUrl.get(endpoint);
    this.requestsUrl = baseUrl.newBuilder().addPathSegments("v1/execution_proof_requests").build();
    this.proofsUrl = baseUrl.newBuilder().addPathSegments("v1/execution_proofs").build();
  }

  @Override
  public SafeFuture<Bytes> requestProof(
      final Bytes32 newPayloadRequestRoot, final int proofType, final Bytes newPayloadRequestSsz) {
    final Optional<ProofType> zkvmIdentifier = ProofType.fromValue(proofType);
    if (zkvmIdentifier.isEmpty()) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException("No known zkVM identifier for proof type " + proofType));
    }
    final SafeFuture<Bytes> result = new SafeFuture<>();
    final HttpUrl submitUrl =
        requestsUrl
            .newBuilder()
            .addQueryParameter("proof_types", zkvmIdentifier.get().getIdentifier())
            .build();
    final Request request =
        new Request.Builder()
            .url(submitUrl)
            .post(RequestBody.create(newPayloadRequestSsz.toArrayUnsafe(), OCTET_STREAM_MEDIA_TYPE))
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
                try (Response ignored = response;
                    ResponseBody responseBody = response.body()) {
                  if (!response.isSuccessful()) {
                    result.completeExceptionally(
                        new RuntimeException(
                            "Execution proof request to "
                                + submitUrl
                                + " was rejected with status "
                                + response.code()));
                    return;
                  }
                  checkServerRoot(responseBody, newPayloadRequestRoot);
                  openResultEventStream(newPayloadRequestRoot, zkvmIdentifier.get(), result);
                } catch (final IOException e) {
                  result.completeExceptionally(e);
                }
              }
            });
    return result;
  }

  private void checkServerRoot(final ResponseBody responseBody, final Bytes32 expectedRoot)
      throws IOException {
    if (responseBody == null) {
      return;
    }
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(responseBody.string());
      final String serverRoot = node.path("new_payload_request_root").asText("");
      if (!serverRoot.isEmpty() && !expectedRoot.toHexString().equalsIgnoreCase(serverRoot)) {
        LOG.warn(
            "zkboost computed a different new_payload_request_root ({}) than Teku did ({}) for"
                + " the same NewPayloadRequest bytes - possible SSZ encoding mismatch",
            serverRoot,
            expectedRoot);
      }
    } catch (final IOException e) {
      LOG.debug("Failed to parse execution proof request submission response", e);
    }
  }

  private void openResultEventStream(
      final Bytes32 newPayloadRequestRoot,
      final ProofType zkvmIdentifier,
      final SafeFuture<Bytes> result) {
    final HttpUrl eventsUrl =
        requestsUrl
            .newBuilder()
            .addQueryParameter("new_payload_request_root", newPayloadRequestRoot.toHexString())
            .build();
    final AtomicReference<BackgroundEventSource> eventSourceRef = new AtomicReference<>();
    final BackgroundEventSource eventSource =
        new BackgroundEventSource.Builder(
                new ExecutionProofResultEventHandler(
                    newPayloadRequestRoot,
                    () -> fetchProof(newPayloadRequestRoot, zkvmIdentifier, result, eventSourceRef),
                    error -> {
                      result.completeExceptionally(error);
                      closeQuietly(eventSourceRef.get());
                    },
                    () -> closeQuietly(eventSourceRef.get())),
                new EventSource.Builder(ConnectStrategy.http(eventsUrl).httpClient(httpClient)))
            .build();
    eventSourceRef.set(eventSource);
    eventSource.start();
  }

  private void fetchProof(
      final Bytes32 newPayloadRequestRoot,
      final ProofType zkvmIdentifier,
      final SafeFuture<Bytes> result,
      final AtomicReference<BackgroundEventSource> eventSourceRef) {
    final HttpUrl downloadUrl =
        proofsUrl
            .newBuilder()
            .addPathSegment(newPayloadRequestRoot.toHexString())
            .addPathSegment(zkvmIdentifier.getIdentifier())
            .build();
    final Request request = new Request.Builder().url(downloadUrl).get().build();
    httpClient
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(final Call call, final IOException e) {
                result.completeExceptionally(e);
                closeQuietly(eventSourceRef.get());
              }

              @Override
              public void onResponse(final Call call, final Response response) {
                try (Response ignored = response) {
                  if (!response.isSuccessful() || response.body() == null) {
                    result.completeExceptionally(
                        new RuntimeException(
                            "Failed to download completed proof from "
                                + downloadUrl
                                + ", status "
                                + response.code()));
                    return;
                  }
                  result.complete(Bytes.wrap(response.body().bytes()));
                } catch (final IOException e) {
                  result.completeExceptionally(e);
                } finally {
                  closeQuietly(eventSourceRef.get());
                }
              }
            });
  }

  private void closeQuietly(final BackgroundEventSource eventSource) {
    if (eventSource != null) {
      eventSource.close();
    }
  }
}
