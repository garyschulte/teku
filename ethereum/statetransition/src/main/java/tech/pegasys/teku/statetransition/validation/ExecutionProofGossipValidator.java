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

package tech.pegasys.teku.statetransition.validation;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Gossip validation for the single global {@code execution_proof} topic (EIP-8025). REJECT covers
 * structurally/cryptographically impossible input (empty proof, unknown/inactive prover, bad
 * signature); IGNORE covers "not our fault, might be valid later" cases (unknown payload root -
 * most likely a locally-lagging node, dedup). REJECT-on-failed-external-verification is deferred to
 * M6, once a real verifier client exists - until then, a structurally/cryptographically valid proof
 * is provisionally ACCEPTed.
 */
public class ExecutionProofGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<DedupKey> receivedValidExecutionProofKeys;

  // ExecutionProofManagerImpl needs this validator in its own constructor, so the manager itself
  // (used below to check whether a claimed new_payload_request_root corresponds to a block we
  // actually know about) is supplied after both are constructed - mirrors
  // ExecutionProofsAvailabilityCheckerFactory.setDelegate(...).
  private ExecutionProofManager executionProofManager = ExecutionProofManager.NOOP;

  public static ExecutionProofGossipValidator create(
      final Spec spec, final RecentChainData recentChainData) {
    return new ExecutionProofGossipValidator(
        spec,
        recentChainData,
        // 4 proof types per payload (spec's MAX_EXECUTION_PROOFS_PER_PAYLOAD) * 2 epochs * 32
        // slots per epoch based on mainnet, for now
        LimitedSet.createSynchronized(4 * 64));
  }

  ExecutionProofGossipValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final Set<DedupKey> receivedValidExecutionProofKeys) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.receivedValidExecutionProofKeys = receivedValidExecutionProofKeys;
  }

  public void setExecutionProofManager(final ExecutionProofManager executionProofManager) {
    this.executionProofManager = executionProofManager;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionProof signedExecutionProof) {
    final ExecutionProof executionProof = signedExecutionProof.getMessage();

    if (executionProof.getProofData().size() == 0) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject("execution proof has empty proof_data"));
    }

    final Bytes32 newPayloadRequestRoot =
        executionProof.getPublicInput().getNewPayloadRequestRoot().get();
    final DedupKey dedupKey = new DedupKey(newPayloadRequestRoot, executionProof.getProofType());
    if (receivedValidExecutionProofKeys.contains(dedupKey)) {
      LOG.trace("Received duplicate execution proof for {}", dedupKey);
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    if (!executionProofManager.isKnownNewPayloadRequestRoot(newPayloadRequestRoot)) {
      LOG.trace(
          "Received execution proof for unknown new_payload_request_root {}",
          newPayloadRequestRoot);
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    final Optional<SafeFuture<BeaconState>> maybeState = recentChainData.getBestState();
    if (maybeState.isEmpty()) {
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    return maybeState
        .get()
        .thenApply(state -> validateWithState(state, signedExecutionProof, dedupKey));
  }

  private InternalValidationResult validateWithState(
      final BeaconState state,
      final SignedExecutionProof signedExecutionProof,
      final DedupKey dedupKey) {
    final UInt64 validatorIndex = signedExecutionProof.getValidatorIndex();
    if (validatorIndex.isGreaterThanOrEqualTo(UInt64.valueOf(state.getValidators().size()))) {
      return InternalValidationResult.reject(
          "execution proof references unknown validator index %s", validatorIndex);
    }

    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final UInt64 currentEpoch = specVersion.miscHelpers().computeEpochAtSlot(state.getSlot());
    if (!specVersion
        .beaconStateAccessors()
        .getActiveValidatorIndices(state, currentEpoch)
        .contains(validatorIndex.intValue())) {
      return InternalValidationResult.reject(
          "execution proof prover %s is not an active validator", validatorIndex);
    }

    if (!specVersion
        .operationSignatureVerifier()
        .verifyExecutionProofSignature(state, signedExecutionProof, BLSSignatureVerifier.SIMPLE)) {
      return InternalValidationResult.reject("execution proof signature is invalid");
    }

    // TODO(M6): check the external verifier here and REJECT on failure; until then, a
    // structurally/cryptographically valid proof is provisionally accepted.
    receivedValidExecutionProofKeys.add(dedupKey);
    LOG.trace(
        "Received and validated execution proof for new payload request root {}, proof type {}",
        dedupKey.newPayloadRequestRoot(),
        dedupKey.proofType());
    return InternalValidationResult.ACCEPT;
  }

  /** Gossip dedup key per the spec: {@code (new_payload_request_root, proof_type)}. */
  private record DedupKey(Bytes32 newPayloadRequestRoot, int proofType) {}
}
