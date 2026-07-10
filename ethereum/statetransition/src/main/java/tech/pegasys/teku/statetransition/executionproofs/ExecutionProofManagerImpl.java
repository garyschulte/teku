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

package tech.pegasys.teku.statetransition.executionproofs;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProofSchema;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ExecutionProofManagerImpl implements ExecutionProofManager {

  // dummy-generator-only concept, gone once M8 replaces ExecutionProofGeneratorImpl with a real
  // VC-driven prover
  private static final int DUMMY_PROOF_TYPE_FANOUT = 8;

  final ExecutionProofGossipValidator executionProofGossipValidator;

  private final Subscribers<ValidExecutionProofListener> receivedExecutionProofSubscribers =
      Subscribers.create(true);

  private final Map<Bytes32, Set<SignedExecutionProof>> validatedExecutionProofsByBlockRoot =
      new ConcurrentHashMap<>();
  private final Consumer<SignedExecutionProof> onCreatedProof;

  // TODO(M7): keyed by block root as a bridge until availability-checking is reworked to key
  // everything by new_payload_request_root directly. 2 epochs * 32 slots worth of blocks,
  // matching the existing proof-retention window sizing elsewhere in this class.
  private final Map<Bytes32, Bytes32> newPayloadRequestRootsByBlockRoot =
      LimitedMap.createSynchronizedLRU(64);

  private static final Logger LOG = LogManager.getLogger();
  private final int attemptsToGetProof = 3;
  private final ExecutionProofGenerator executionProofGenerator;
  private final Duration proofGenerationDelay;
  private final AsyncRunner asyncRunner;
  private final boolean isProofGenerationEnabled;
  private final int minProofsRequired;
  private final Spec spec;

  public ExecutionProofManagerImpl(
      final ExecutionProofGossipValidator executionProofGossipValidator,
      final ExecutionProofGenerator executionProofGenerator,
      final Consumer<SignedExecutionProof> onCreatedProof,
      final boolean isProofGenerationEnabled,
      final int minProofsRequired,
      final Duration proofGenerationDelay,
      final AsyncRunner asyncRunner,
      final Spec spec) {
    this.executionProofGossipValidator = executionProofGossipValidator;
    this.onCreatedProof = onCreatedProof;
    this.isProofGenerationEnabled = isProofGenerationEnabled;
    this.minProofsRequired = minProofsRequired;
    this.executionProofGenerator = executionProofGenerator;
    this.proofGenerationDelay = proofGenerationDelay;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
  }

  @Override
  public void onExecutionProofPublish(
      final SignedExecutionProof signedExecutionProof, final RemoteOrigin remoteOrigin) {
    LOG.trace("Published execution proof {}", signedExecutionProof);
  }

  @Override
  public SafeFuture<InternalValidationResult> onReceivedExecutionProofGossip(
      final SignedExecutionProof signedExecutionProof, final Optional<UInt64> arrivalTimestamp) {
    LOG.debug("Received execution proof for block {}", signedExecutionProof);
    return executionProofGossipValidator
        .validate(signedExecutionProof)
        .thenApply(
            result -> {
              if (result.isAccept()) {
                // TODO check if proof for same block and subnet already exists this could be a
                // different proof for same block and subnet
                // in this case do we want to replace a existing valid proof with a new one?
                LOG.debug("Adding execution proof for block {} to cache", signedExecutionProof);
                validatedExecutionProofsByBlockRoot
                    .computeIfAbsent(
                        signedExecutionProof
                            .getMessage()
                            .getPublicInput()
                            .getNewPayloadRequestRoot()
                            .get(),
                        k -> ConcurrentHashMap.newKeySet())
                    .add(signedExecutionProof);
                LOG.debug(
                    "Added execution proof to cache {}",
                    validatedExecutionProofsByBlockRoot.toString());
              } else {
                LOG.debug(
                    "Rejected execution proof for new payload request root {}: {}",
                    signedExecutionProof.getMessage().getPublicInput().getNewPayloadRequestRoot(),
                    result);
              }
              return result;
            });
  }

  @Override
  public void subscribeToValidExecutionProofs(
      final ValidExecutionProofListener executionProofListener) {
    receivedExecutionProofSubscribers.subscribe(executionProofListener);
  }

  @Override
  public SafeFuture<DataAndValidationResult<SignedExecutionProof>> validateBlockWithExecutionProofs(
      final SignedBeaconBlock block) {

    return asyncRunner.runAsync(
        () -> {
          SafeFuture<DataAndValidationResult<SignedExecutionProof>> validationResult =
              new SafeFuture<>();
          LOG.debug("starting validation of execution proofs for block {}", block.getRoot());
          for (int attempt = 0; attempt < attemptsToGetProof; attempt++) {
            final DataAndValidationResult<SignedExecutionProof> result = checkForValidProofs(block);
            if (result.isValid()) {
              LOG.debug(
                  "Found valid proofs for block {} on attempt {}", block.getRoot(), attempt + 1);
              validationResult = SafeFuture.completedFuture(result);
              break;
            } else {
              if (attempt == attemptsToGetProof - 1) {
                validationResult =
                    SafeFuture.completedFuture(
                        DataAndValidationResult.notAvailable(
                            new RuntimeException(
                                "No valid execution proofs found for block " + block.getRoot())));
              }
            }
            try {
              // sleep for a 1/4 of the slot time based
              Thread.sleep(spec.getSlotDurationMillis(block.getSlot()) / 4);
            } catch (InterruptedException e) {
              LOG.debug("Interrupted while waiting for validation of proofs");
              throw new RuntimeException(e);
            }
          }
          LOG.debug("Checking proofs for block {}", block.getRoot());

          return validationResult;
        });
  }

  private DataAndValidationResult<SignedExecutionProof> checkForValidProofs(
      final SignedBeaconBlock block) {
    if (validatedExecutionProofsByBlockRoot.containsKey(block.getRoot())) {
      final List<SignedExecutionProof> proofs =
          validatedExecutionProofsByBlockRoot.get(block.getRoot()).stream().toList();
      LOG.debug(
          "Found {} previously validated proofs for block {}", proofs.size(), block.getRoot());
      if (proofs.size() >= minProofsRequired) {
        return DataAndValidationResult.validResult(proofs);
      } else {
        return DataAndValidationResult.invalidResult(proofs);
      }
    } else {
      return DataAndValidationResult.notAvailable();
    }
  }

  @Override
  public SafeFuture<Void> generateProofs(final SignedBlockContainer blockContainer) {

    if (!isProofGenerationEnabled) {
      return SafeFuture.completedFuture(null);
    }
    final Bytes32 blockRoot = blockContainer.getSignedBlock().getRoot();
    final SignedExecutionProofSchema signedExecutionProofSchema =
        SchemaDefinitionsElectra.required(
                spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions())
            .getSignedExecutionProofSchema();

    asyncRunner
        .runAsync(
            () -> {
              // Generate proofs for all (dummy) proof types
              IntStream.range(0, DUMMY_PROOF_TYPE_FANOUT)
                  .forEach(
                      proofType -> {
                        executionProofGenerator
                            .generateExecutionProof(blockContainer, proofType, proofGenerationDelay)
                            .finish(
                                proof -> {
                                  LOG.trace("Generated proof for subnet {}", proof.getProofType());
                                  // TODO(M8): the dummy generator produces no real signature;
                                  // this placeholder will never pass real signature
                                  // verification (correctly so) until the VC-driven prover
                                  // replaces this whole generator.
                                  final SignedExecutionProof signedExecutionProof =
                                      signedExecutionProofSchema.create(
                                          proof, UInt64.ZERO, BLSSignature.empty());
                                  validatedExecutionProofsByBlockRoot
                                      .computeIfAbsent(
                                          proof.getPublicInput().getNewPayloadRequestRoot().get(),
                                          k -> ConcurrentHashMap.newKeySet())
                                      .add(signedExecutionProof);
                                  onCreatedProof.accept(signedExecutionProof);
                                },
                                error ->
                                    LOG.error(
                                        "Failed to generate proof for block {} proof type {}",
                                        blockRoot,
                                        proofType,
                                        error));
                      });
            })
        .finish(
            () -> LOG.debug("Completed generating execution proofs for block {}", blockRoot),
            error ->
                LOG.error("Failed to generate execution proofs for block {}", blockRoot, error));

    return SafeFuture.completedFuture(null);
  }

  @Override
  public void recordNewPayloadRequestRoot(
      final Bytes32 blockRoot, final Bytes32 newPayloadRequestRoot) {
    newPayloadRequestRootsByBlockRoot.put(blockRoot, newPayloadRequestRoot);
  }

  @Override
  public Optional<Bytes32> getNewPayloadRequestRoot(final Bytes32 blockRoot) {
    return Optional.ofNullable(newPayloadRequestRootsByBlockRoot.get(blockRoot));
  }

  @Override
  public boolean isKnownNewPayloadRequestRoot(final Bytes32 newPayloadRequestRoot) {
    // small bounded map (64 entries) - a linear scan is fine
    return newPayloadRequestRootsByBlockRoot.containsValue(newPayloadRequestRoot);
  }
}
