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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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

/**
 * HTTP client for a zkboost-shaped external verifier service. POSTs raw proof bytes to {@code
 * {endpoint}/v1/execution_proof_verifications?new_payload_request_root=...&proof_type=...} and
 * expects a JSON response body like {@code {"status": "VALID"}} on success, per Prysm's
 * zkProofVerifier. This has NOT been cross-verified against zkboost's actual response schema
 * end-to-end - treat as best-effort pending such verification (see EIP-8025 gap-analysis M6 notes).
 */
public class RestExecutionProofVerifierClient implements ExecutionProofVerifierClient {

  private static final Logger LOG = LogManager.getLogger();
  private static final MediaType OCTET_STREAM_MEDIA_TYPE =
      MediaType.parse("application/octet-stream");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OkHttpClient httpClient;
  private final HttpUrl verificationsUrl;

  public RestExecutionProofVerifierClient(final OkHttpClient httpClient, final String endpoint) {
    this.httpClient = httpClient;
    this.verificationsUrl =
        HttpUrl.get(endpoint)
            .newBuilder()
            .addPathSegments("v1/execution_proof_verifications")
            .build();
  }

  @Override
  public SafeFuture<Boolean> verify(
      final Bytes32 newPayloadRequestRoot, final int proofType, final Bytes proofData) {
    final HttpUrl url =
        verificationsUrl
            .newBuilder()
            .addQueryParameter("new_payload_request_root", newPayloadRequestRoot.toHexString())
            .addQueryParameter("proof_type", Integer.toString(proofType))
            .build();
    final RequestBody body = RequestBody.create(proofData.toArrayUnsafe(), OCTET_STREAM_MEDIA_TYPE);
    final Request request = new Request.Builder().url(url).post(body).build();

    final SafeFuture<Boolean> result = new SafeFuture<>();
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
                    LOG.debug(
                        "Execution proof verification request to {} failed with status {}",
                        url,
                        response.code());
                    result.complete(false);
                    return;
                  }
                  final String responseBodyString =
                      responseBody != null ? responseBody.string() : "";
                  result.complete(isVerified(responseBodyString));
                } catch (final IOException e) {
                  result.completeExceptionally(e);
                }
              }
            });
    return result;
  }

  private boolean isVerified(final String responseBody) {
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(responseBody);
      return "VALID".equalsIgnoreCase(node.path("status").asText());
    } catch (final IOException e) {
      LOG.debug("Failed to parse execution proof verification response '{}'", responseBody, e);
      return false;
    }
  }
}
