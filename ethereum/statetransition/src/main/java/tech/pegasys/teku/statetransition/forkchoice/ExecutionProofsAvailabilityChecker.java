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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

/**
 * Execution proofs are advisory during this optional (non-mandatory) phase of EIP-8025: a block is
 * never blocked or rejected because proofs are missing or fail to accumulate, matching Prysm's
 * "mark optimistic, reconcile later, never un-import" philosophy.
 *
 * <p>This decorator forwards the delegate's (blob/data column) {@link DataAndValidationResult}
 * <b>unchanged</b> - including its actual data - rather than substituting a differently-typed
 * result of its own. An earlier version of this class replaced the delegate's result with a new
 * {@code DataAndValidationResult<SignedExecutionProof>}, which broke {@code ForkChoice}'s blob
 * sidecar extraction (it discarded the delegate's real {@code BlobSidecar} data and produced a
 * validation status - {@code OPTIMISTIC} - that {@code ForkChoice}'s exact-enum checks didn't
 * recognize, throwing {@code IllegalStateException}). Proof validation still runs, but purely for
 * logging/observability (see {@link ExecutionProofManager#validateBlockWithExecutionProofs}) - a
 * block that ends up with zero valid proofs is logged, not un-imported.
 */
public class ExecutionProofsAvailabilityChecker
    implements AvailabilityChecker<SignedExecutionProof> {
  private static final Logger LOG = LogManager.getLogger();
  private final ExecutionProofManager executionProofManager;
  private final SignedBeaconBlock block;
  private final AvailabilityChecker<?> delegate;

  public ExecutionProofsAvailabilityChecker(
      final ExecutionProofManager executionProofManager,
      final SignedBeaconBlock block,
      final AvailabilityChecker<?> delegate) {
    this.executionProofManager = executionProofManager;
    this.delegate = delegate;
    this.block = block;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    delegate.initiateDataAvailabilityCheck();
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<SignedExecutionProof>> getAvailabilityCheckResult() {
    return delegate.getAvailabilityCheckResult().thenApply(this::forwardAndReconcile);
  }

  @SuppressWarnings("unchecked")
  private DataAndValidationResult<SignedExecutionProof> forwardAndReconcile(
      final DataAndValidationResult<?> delegateResult) {
    if (delegateResult.isSuccess()) {
      LOG.debug(
          "Delegate availability check for block {} succeeded; kicking off execution proof"
              + " reconciliation (advisory only, does not affect import)",
          block.getRoot());
      reconcileInBackground();
    }
    // Forward the delegate's result (and its actual data) unchanged - see class Javadoc.
    return (DataAndValidationResult<SignedExecutionProof>) delegateResult;
  }

  /**
   * Purely for logging/observability - never affects import. A block that never accumulates enough
   * valid proofs stays imported; there is no un-import/slashing/reorg behaviour tied to this
   * outcome in this pass.
   */
  private void reconcileInBackground() {
    executionProofManager
        .validateBlockWithExecutionProofs(block)
        .finish(
            result ->
                LOG.debug(
                    "Execution proof reconciliation for block {}: {}",
                    block.getRoot(),
                    result.toLogString()),
            error ->
                LOG.debug(
                    "Execution proof reconciliation for block {} failed", block.getRoot(), error));
  }
}
