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

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;

// TODO(M4): this is a placeholder pending the gossip rework to a single global topic. Real
// REJECT/IGNORE/ACCEPT semantics (active-validator + BLS signature check from M3, dedup keyed by
// (new_payload_request_root, proof_type), IGNORE-on-unknown-payload-root) land there.
public class ExecutionProofGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  // TODO maybe change this to be a map of block/proof in the future
  private final Set<ExecutionProof> receivedValidExecutionProofSet;

  public static ExecutionProofGossipValidator create() {
    return new ExecutionProofGossipValidator(
        // 8 proof types * 2 epochs * slots per epoch 32 based on mainnet for now
        LimitedSet.createSynchronized(8 * 64));
  }

  public ExecutionProofGossipValidator(final Set<ExecutionProof> receivedValidExecutionProofSet) {

    this.receivedValidExecutionProofSet = receivedValidExecutionProofSet;
  }

  public SafeFuture<InternalValidationResult> validate(final ExecutionProof executionProof) {

    // Already seen and valid
    if (receivedValidExecutionProofSet.contains(executionProof)) {
      LOG.trace("Received duplicate execution proof {}", executionProof);
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    // some of the todos in the LH prototype apply to us atm
    // TODO: Add timing validation based on slot
    // TODO: Add block existence validation

    // Validated the execution proof
    LOG.trace(
        "Received and validated execution proof for new payload request root {}, proof type {}",
        executionProof.getPublicInput().getNewPayloadRequestRoot(),
        executionProof.getProofType());
    receivedValidExecutionProofSet.add(executionProof);
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }
}
