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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequestHasher;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutor implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionLayerChannel executionLayer;
  private final SignedBeaconBlock block;
  private final MergeTransitionBlockValidator transitionBlockValidator;
  private final ExecutionProofManager executionProofManager;
  private final int maxVersionedHashesPerBlock;
  private Optional<SafeFuture<PayloadValidationResult>> result = Optional.empty();

  ForkChoicePayloadExecutor(
      final SignedBeaconBlock block,
      final ExecutionLayerChannel executionLayer,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final ExecutionProofManager executionProofManager,
      final int maxVersionedHashesPerBlock) {
    this.block = block;
    this.transitionBlockValidator = transitionBlockValidator;
    this.executionLayer = executionLayer;
    this.executionProofManager = executionProofManager;
    this.maxVersionedHashesPerBlock = maxVersionedHashesPerBlock;
  }

  public static ForkChoicePayloadExecutor create(
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final ExecutionLayerChannel executionLayer,
      final ExecutionProofManager executionProofManager) {
    final int maxVersionedHashesPerBlock =
        spec.atSlot(block.getSlot())
            .getConfig()
            .toVersionDeneb()
            .map(SpecConfigDeneb::getMaxBlobCommitmentsPerBlock)
            .orElse(0);
    return new ForkChoicePayloadExecutor(
        block,
        executionLayer,
        new MergeTransitionBlockValidator(spec, recentChainData),
        executionProofManager,
        maxVersionedHashesPerBlock);
  }

  public SafeFuture<PayloadValidationResult> getExecutionResult() {
    return result.orElse(
        SafeFuture.completedFuture(new PayloadValidationResult(PayloadStatus.VALID)));
  }

  @Override
  public boolean optimisticallyExecute(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final NewPayloadRequest payloadToExecute) {
    final ExecutionPayload executionPayload = payloadToExecute.getExecutionPayload();
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it checks the parentRoot matches
      return true;
    }
    recordNewPayloadRequestRoot(payloadToExecute);
    result =
        Optional.of(
            executionLayer
                .engineNewPayload(payloadToExecute, block.getSlot())
                .thenCompose(
                    result -> {
                      if (result.hasValidStatus()) {
                        return transitionBlockValidator.verifyTransitionBlock(
                            latestExecutionPayloadHeader, block);
                      } else {
                        return SafeFuture.completedFuture(new PayloadValidationResult(result));
                      }
                    })
                .exceptionally(
                    error -> {
                      LOG.error("Error while validating payload", error);
                      return new PayloadValidationResult(PayloadStatus.failedExecution(error));
                    }));

    return true;
  }

  private void recordNewPayloadRequestRoot(final NewPayloadRequest payloadToExecute) {
    try {
      final Optional<ExecutionRequests> executionRequests =
          block
              .getBeaconBlock()
              .flatMap(beaconBlock -> beaconBlock.getBody().getOptionalExecutionRequests());
      final Bytes32 newPayloadRequestRoot =
          NewPayloadRequestHasher.hashTreeRoot(
              payloadToExecute, executionRequests, maxVersionedHashesPerBlock);
      executionProofManager.recordNewPayloadRequestRoot(block.getRoot(), newPayloadRequestRoot);
    } catch (final RuntimeException e) {
      LOG.debug("Failed to compute new_payload_request_root for block {}", block.getRoot(), e);
    }
  }
}
