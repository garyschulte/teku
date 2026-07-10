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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

/**
 * Reconstructs the equivalent {@link NewPayloadRequest} for an already-imported block, from the
 * block alone - i.e. without requiring the {@code BeaconState} that {@code
 * BlockProcessor#computeNewPayloadRequest(BeaconState, BeaconBlockBody)} normally takes as an
 * argument during state-transition.
 *
 * <p>This is possible because every field {@code computeNewPayloadRequest} derives from state is
 * also independently recoverable from the block itself: {@code parentBeaconBlockRoot} is exactly
 * {@link SignedBeaconBlock#getParentRoot()} (the value {@code
 * state.getLatestBlockHeader().getParentRoot()} holds by the time that method runs mid-block
 * processing is the parent root of the block currently being processed - the same block), and
 * {@code versionedHashes}/{@code executionRequests} are pure functions of the block body content.
 * Used by the validator-client execution-proof prover (see EIP-8025 gap-analysis M8 notes) which
 * only has access to a fetched block, not the beacon state at that slot.
 */
public final class NewPayloadRequestBuilder {

  private NewPayloadRequestBuilder() {}

  public static NewPayloadRequest fromBlock(final Spec spec, final SignedBeaconBlock block) {
    final BeaconBlockBody body = block.getMessage().getBody();
    final ExecutionPayload executionPayload =
        body.getOptionalExecutionPayload()
            .orElseThrow(
                () -> new IllegalArgumentException("Block does not contain an execution payload"));

    final Optional<SszList<SszKZGCommitment>> blobKzgCommitments =
        body.getOptionalBlobKzgCommitments();
    if (blobKzgCommitments.isEmpty()) {
      return new NewPayloadRequest(executionPayload);
    }

    final MiscHelpers miscHelpers = spec.atSlot(block.getSlot()).miscHelpers();
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.get().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = block.getParentRoot();

    final Optional<ExecutionRequests> executionRequests = body.getOptionalExecutionRequests();
    if (executionRequests.isEmpty()) {
      return new NewPayloadRequest(executionPayload, versionedHashes, parentBeaconBlockRoot);
    }

    final ExecutionRequestsDataCodec executionRequestsDataCodec =
        new ExecutionRequestsDataCodec(
            spec.atSlot(block.getSlot())
                .getSchemaDefinitions()
                .toVersionElectra()
                .orElseThrow()
                .getExecutionRequestsSchema());
    return new NewPayloadRequest(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec.encode(executionRequests.get()));
  }
}
