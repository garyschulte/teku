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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

/** EIP-8025 execution proof submission (prototype - route/shape may still change). */
public class PostExecutionProof extends RestApiEndpoint {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/pool/execution_proofs";
  private final NodeDataProvider nodeDataProvider;

  public PostExecutionProof(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getNodeDataProvider(), schemaCache);
  }

  public PostExecutionProof(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.nodeDataProvider = provider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("submitPoolExecutionProof")
        .summary("Submit SignedExecutionProof object to node's pool")
        .description(
            "Submits a signed EIP-8025 execution proof to the node's pool and, if it passes"
                + " validation, the node MUST broadcast it to the network.")
        .tags(TAG_BEACON)
        .requestBodyType(
            schemaCache
                .getSchemaDefinition(SpecMilestone.ELECTRA)
                .toVersionElectra()
                .orElseThrow()
                .getSignedExecutionProofSchema()
                .getJsonTypeDefinition())
        .response(
            SC_OK,
            "Signed execution proof has been successfully validated, added to the pool, and broadcast.")
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SignedExecutionProof signedExecutionProof = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future =
        nodeDataProvider.postExecutionProof(signedExecutionProof);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                LOG.debug(
                    "Execution proof submission failed status {}: {}",
                    internalValidationResult.code(),
                    internalValidationResult.getDescription().orElse(""));
                if (internalValidationResult.isIgnore()) {
                  return AsyncApiResponse.respondWithCode(SC_OK);
                }
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse("Invalid execution proof, it was rejected"));
              }
              return AsyncApiResponse.respondWithCode(SC_OK);
            }));
  }
}
