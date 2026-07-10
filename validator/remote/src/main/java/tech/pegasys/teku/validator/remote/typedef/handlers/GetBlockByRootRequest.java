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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_BLOCK;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

/**
 * {@code GET /eth/v2/beacon/blocks/{block_id}}, used by the execution-proof prover duty (EIP-8025)
 * to fetch the content of a block it needs to prove, given only the root from a head-update event.
 */
public class GetBlockByRootRequest extends AbstractTypeDefRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ResponseHandler<SignedBeaconBlock> responseHandler;

  public GetBlockByRootRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
    this.responseHandler =
        new ResponseHandler<SignedBeaconBlock>().withHandler(SC_OK, this::handleBlockResult);
  }

  public Optional<SignedBeaconBlock> submit(final Bytes32 blockRoot) {
    final Map<String, String> headers =
        Map.of("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    return get(
        GET_BLOCK,
        Map.of("block_id", blockRoot.toHexString()),
        emptyMap(),
        emptyMap(),
        headers,
        responseHandler);
  }

  private Optional<SignedBeaconBlock> handleBlockResult(
      final Request request, final Response response) {
    try {
      final String consensusVersion = response.header(HEADER_CONSENSUS_VERSION);
      if (consensusVersion == null) {
        LOG.warn(
            "Response to {} is missing the {} header", request.url(), HEADER_CONSENSUS_VERSION);
        return Optional.empty();
      }
      final SchemaDefinitions schemaDefinitions =
          spec.forMilestone(SpecMilestone.valueOf(consensusVersion.toUpperCase(Locale.ROOT)))
              .getSchemaDefinitions();
      final String contentType = response.header("Content-Type");
      if (contentType != null && MediaType.parse(contentType).is(MediaType.OCTET_STREAM)) {
        return Optional.of(
            schemaDefinitions
                .getSignedBeaconBlockSchema()
                .sszDeserialize(Bytes.of(response.body().bytes())));
      }
      return Optional.of(
          JsonUtil.parse(
              response.body().string(),
              schemaDefinitions.getSignedBeaconBlockSchema().getJsonTypeDefinition()));
    } catch (final IOException e) {
      LOG.error("Failed to parse block response from {}", request.url(), e);
      return Optional.empty();
    }
  }
}
