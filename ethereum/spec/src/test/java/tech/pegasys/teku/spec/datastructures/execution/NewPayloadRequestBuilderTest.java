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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class NewPayloadRequestBuilderTest {

  @Test
  void fromBlock_matchesBlockContentAndProducesTheSameRootAsTheBnSidePath() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    final NewPayloadRequest newPayloadRequest = NewPayloadRequestBuilder.fromBlock(spec, block);

    assertThat(newPayloadRequest.getExecutionPayload())
        .isEqualTo(block.getMessage().getBody().getOptionalExecutionPayload().orElseThrow());
    assertThat(newPayloadRequest.getParentBeaconBlockRoot()).contains(block.getParentRoot());
    assertThat(newPayloadRequest.getVersionedHashes()).isPresent();
    assertThat(newPayloadRequest.getExecutionRequests()).isPresent();

    final int maxVersionedHashesPerBlock =
        spec.atSlot(block.getSlot())
            .getConfig()
            .toVersionDeneb()
            .map(SpecConfigDeneb::getMaxBlobCommitmentsPerBlock)
            .orElseThrow();
    final Bytes32 root =
        NewPayloadRequestHasher.hashTreeRoot(
            newPayloadRequest,
            block.getMessage().getBody().getOptionalExecutionRequests(),
            maxVersionedHashesPerBlock);

    assertThat(root).isNotEqualTo(Bytes32.ZERO);
  }

  @Test
  void fromBlock_throwsWhenBlockHasNoExecutionPayload() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    assertThat(block.getMessage().getBody().getOptionalExecutionPayload()).isEmpty();
    assertThatThrownBy(() -> NewPayloadRequestBuilder.fromBlock(spec, block))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
