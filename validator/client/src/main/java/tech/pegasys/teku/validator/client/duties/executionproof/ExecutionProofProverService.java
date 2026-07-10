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

import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequestBuilder;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequestHasher;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.ValidatorIndexProvider;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

/**
 * EIP-8025 execution-proof prover duty: on every new head, fetches the block, asks an external
 * prover service ({@link ExecutionProofProverClient}) to prove it, signs the resulting proof with
 * any one of this validator client's local keys, and submits it to the beacon node for gossip.
 *
 * <p>This is a best-effort, single-proof-type-per-block implementation - it does not attempt the
 * fan-out-to-many-prover-types semantics the wire format otherwise allows (see EIP-8025
 * gap-analysis M5 notes on why the pool isn't keyed by a single proof per block). Any one local
 * validator is used as the signer; there is no notion of "the" designated prover in this pass.
 */
public class ExecutionProofProverService implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * The only proof type this prover requests in this pass - EIP-8025 allows multiple provers to
   * submit different proof types for the same block, but fanning out to many is out of scope here.
   */
  public static final int PROOF_TYPE = 0;

  private final Spec spec;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final OwnedValidators validators;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final ExecutionProofProverClient proverClient;

  public ExecutionProofProverService(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final ExecutionProofProverClient proverClient) {
    this.spec = spec;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validators = validators;
    this.validatorIndexProvider = validatorIndexProvider;
    this.proverClient = proverClient;
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    if (validators.hasNoValidators()) {
      return;
    }
    validatorApiChannel
        .getBeaconBlockByRoot(headBlockRoot)
        .thenAccept(
            maybeBlock ->
                maybeBlock.ifPresentOrElse(
                    this::proveAndSubmit,
                    () ->
                        LOG.debug(
                            "Could not fetch block {} to generate an execution proof for it",
                            headBlockRoot)))
        .finish(
            error ->
                LOG.debug(
                    "Failed to fetch block {} for execution proof generation",
                    headBlockRoot,
                    error));
  }

  private void proveAndSubmit(final SignedBeaconBlock block) {
    if (block.getMessage().getBody().getOptionalExecutionPayload().isEmpty()) {
      // Pre-merge - nothing to prove.
      return;
    }
    final Bytes32 newPayloadRequestRoot;
    try {
      newPayloadRequestRoot = computeNewPayloadRequestRoot(block);
    } catch (final RuntimeException e) {
      LOG.debug(
          "Failed to compute new_payload_request_root for block {}, skipping proof generation",
          block.getRoot(),
          e);
      return;
    }
    proverClient
        .requestProof(newPayloadRequestRoot, PROOF_TYPE, block.sszSerialize())
        .thenCompose(proofData -> signAndSubmit(newPayloadRequestRoot, proofData, block.getSlot()))
        .finish(
            error ->
                LOG.debug(
                    "Failed to generate/submit execution proof for block {}",
                    block.getRoot(),
                    error));
  }

  private Bytes32 computeNewPayloadRequestRoot(final SignedBeaconBlock block) {
    final NewPayloadRequest newPayloadRequest = NewPayloadRequestBuilder.fromBlock(spec, block);
    final Optional<ExecutionRequests> executionRequests =
        block.getMessage().getBody().getOptionalExecutionRequests();
    final int maxVersionedHashesPerBlock =
        spec.atSlot(block.getSlot())
            .getConfig()
            .toVersionDeneb()
            .map(SpecConfigDeneb::getMaxBlobCommitmentsPerBlock)
            .orElse(0);
    return NewPayloadRequestHasher.hashTreeRoot(
        newPayloadRequest, executionRequests, maxVersionedHashesPerBlock);
  }

  private SafeFuture<Void> signAndSubmit(
      final Bytes32 newPayloadRequestRoot, final Bytes proofData, final UInt64 slot) {
    final Optional<Validator> prover = validators.getValidators().stream().findFirst();
    if (prover.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionProof executionProof =
        schemaDefinitionsElectra
            .getExecutionProofSchema()
            .create(
                proofData,
                PROOF_TYPE,
                schemaDefinitionsElectra
                    .getExecutionProofSchema()
                    .getPublicInputSchema()
                    .create(newPayloadRequestRoot));
    return validatorIndexProvider
        .getValidatorIndicesByPublicKey()
        .thenCompose(
            indicesByPublicKey ->
                signAndSubmit(
                    prover.get(),
                    executionProof,
                    indicesByPublicKey,
                    slot,
                    schemaDefinitionsElectra));
  }

  private SafeFuture<Void> signAndSubmit(
      final Validator prover,
      final ExecutionProof executionProof,
      final Map<BLSPublicKey, Integer> indicesByPublicKey,
      final UInt64 slot,
      final SchemaDefinitionsElectra schemaDefinitionsElectra) {
    final Integer validatorIndex = indicesByPublicKey.get(prover.getPublicKey());
    if (validatorIndex == null) {
      LOG.debug("Validator index for {} not yet known, skipping this round", prover.getPublicKey());
      return SafeFuture.COMPLETE;
    }
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            (final ForkInfo forkInfo) ->
                prover.getSigner().signExecutionProof(executionProof, epoch, forkInfo))
        .thenCompose(
            signature -> {
              final SignedExecutionProof signedExecutionProof =
                  schemaDefinitionsElectra
                      .getSignedExecutionProofSchema()
                      .create(executionProof, UInt64.valueOf(validatorIndex), signature);
              return validatorApiChannel.sendSignedExecutionProof(signedExecutionProof);
            });
  }

  @Override
  public void onSlot(final UInt64 slot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  @Override
  public void onSyncCommitteeCreationDue(final UInt64 slot) {}

  @Override
  public void onContributionCreationDue(final UInt64 slot) {}

  @Override
  public void onPayloadAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttesterSlashing(final AttesterSlashing attesterSlashing) {}

  @Override
  public void onProposerSlashing(final ProposerSlashing proposerSlashing) {}

  @Override
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {}
}
