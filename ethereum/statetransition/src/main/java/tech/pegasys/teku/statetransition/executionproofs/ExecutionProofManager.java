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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionProofManager {

  ExecutionProofManager NOOP =
      new ExecutionProofManager() {

        @Override
        public SafeFuture<InternalValidationResult> onReceivedExecutionProofGossip(
            SignedExecutionProof signedExecutionProof, Optional<UInt64> arrivalTimestamp) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public void onExecutionProofPublish(
            final SignedExecutionProof signedExecutionProof, final RemoteOrigin remoteOrigin) {}

        @Override
        public void subscribeToValidExecutionProofs(
            final ValidExecutionProofListener sidecarsListener) {}

        @Override
        public SafeFuture<DataAndValidationResult<SignedExecutionProof>>
            validateBlockWithExecutionProofs(final SignedBeaconBlock block) {
          return SafeFuture.completedFuture(DataAndValidationResult.notRequired());
        }

        @Override
        public SafeFuture<Void> generateProofs(SignedBlockContainer blockContainer) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public void recordNewPayloadRequestRoot(
            final Bytes32 blockRoot, final Bytes32 newPayloadRequestRoot) {}

        @Override
        public Optional<Bytes32> getNewPayloadRequestRoot(final Bytes32 blockRoot) {
          return Optional.empty();
        }

        @Override
        public boolean isKnownNewPayloadRequestRoot(final Bytes32 newPayloadRequestRoot) {
          return false;
        }
      };

  void onExecutionProofPublish(
      SignedExecutionProof signedExecutionProof, RemoteOrigin remoteOrigin);

  SafeFuture<InternalValidationResult> onReceivedExecutionProofGossip(
      SignedExecutionProof signedExecutionProof, Optional<UInt64> arrivalTimestamp);

  void subscribeToValidExecutionProofs(
      ExecutionProofManager.ValidExecutionProofListener executionProofListener);

  SafeFuture<DataAndValidationResult<SignedExecutionProof>> validateBlockWithExecutionProofs(
      final SignedBeaconBlock block);

  interface ValidExecutionProofListener {
    void onNewValidExecutionProof(
        SignedExecutionProof signedExecutionProof, RemoteOrigin remoteOrigin);
  }

  SafeFuture<Void> generateProofs(SignedBlockContainer blockContainer);

  /**
   * Records the {@code new_payload_request_root} (EIP-8025 {@code
   * hash_tree_root(NewPayloadRequest)}) computed for a locally-imported block, so it can later be
   * correlated against incoming {@code SignedExecutionProof}s' {@code public_input}.
   */
  void recordNewPayloadRequestRoot(Bytes32 blockRoot, Bytes32 newPayloadRequestRoot);

  Optional<Bytes32> getNewPayloadRequestRoot(Bytes32 blockRoot);

  /**
   * Reverse check used by gossip validation: is this a {@code new_payload_request_root} we know
   * about (i.e. computed for some locally-imported block), as opposed to one referencing a block we
   * haven't seen yet (which should be IGNOREd, not REJECTed).
   */
  boolean isKnownNewPayloadRequestRoot(Bytes32 newPayloadRequestRoot);
}
