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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class NewPayloadRequestHasherTest {

  private static final int MAX_VERSIONED_HASHES = 6;
  private static final Bytes32 DEFAULT_ROOT = Bytes32.ZERO;

  @Test
  void bellatrix_producesStableNonDefaultRoot() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final NewPayloadRequest request =
        new NewPayloadRequest(dataStructureUtil.randomExecutionPayload());

    final Bytes32 root1 =
        NewPayloadRequestHasher.hashTreeRoot(request, Optional.empty(), MAX_VERSIONED_HASHES);
    final Bytes32 root2 =
        NewPayloadRequestHasher.hashTreeRoot(request, Optional.empty(), MAX_VERSIONED_HASHES);

    assertThat(root1).isNotEqualTo(DEFAULT_ROOT);
    assertThat(root1).isEqualTo(root2);
  }

  @Test
  void deneb_producesStableNonDefaultRootAndDependsOnAllFields() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();

    final NewPayloadRequest request =
        new NewPayloadRequest(executionPayload, versionedHashes, parentBeaconBlockRoot);
    final NewPayloadRequest requestWithDifferentParentRoot =
        new NewPayloadRequest(executionPayload, versionedHashes, dataStructureUtil.randomBytes32());

    final Bytes32 root1 =
        NewPayloadRequestHasher.hashTreeRoot(request, Optional.empty(), MAX_VERSIONED_HASHES);
    final Bytes32 root2 =
        NewPayloadRequestHasher.hashTreeRoot(request, Optional.empty(), MAX_VERSIONED_HASHES);
    final Bytes32 rootWithDifferentParentRoot =
        NewPayloadRequestHasher.hashTreeRoot(
            requestWithDifferentParentRoot, Optional.empty(), MAX_VERSIONED_HASHES);

    assertThat(root1).isNotEqualTo(DEFAULT_ROOT);
    assertThat(root1).isEqualTo(root2);
    assertThat(root1).isNotEqualTo(rootWithDifferentParentRoot);
  }

  @Test
  void electra_producesStableNonDefaultRootAndDependsOnExecutionRequests() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(2);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests();
    final ExecutionRequests otherExecutionRequests = dataStructureUtil.randomExecutionRequests();

    final NewPayloadRequest request =
        new NewPayloadRequest(executionPayload, versionedHashes, parentBeaconBlockRoot, List.of());

    final Bytes32 root1 =
        NewPayloadRequestHasher.hashTreeRoot(
            request, Optional.of(executionRequests), MAX_VERSIONED_HASHES);
    final Bytes32 root2 =
        NewPayloadRequestHasher.hashTreeRoot(
            request, Optional.of(executionRequests), MAX_VERSIONED_HASHES);
    final Bytes32 rootWithDifferentExecutionRequests =
        NewPayloadRequestHasher.hashTreeRoot(
            request, Optional.of(otherExecutionRequests), MAX_VERSIONED_HASHES);

    assertThat(root1).isNotEqualTo(DEFAULT_ROOT);
    assertThat(root1).isEqualTo(root2);
    assertThat(root1).isNotEqualTo(rootWithDifferentExecutionRequests);
  }

  @Test
  void differentForkShapes_produceDifferentRoots() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();

    final Bytes32 bellatrixShapeRoot =
        NewPayloadRequestHasher.hashTreeRoot(
            new NewPayloadRequest(executionPayload), Optional.empty(), MAX_VERSIONED_HASHES);
    final Bytes32 denebShapeRoot =
        NewPayloadRequestHasher.hashTreeRoot(
            new NewPayloadRequest(executionPayload, List.of(), dataStructureUtil.randomBytes32()),
            Optional.empty(),
            MAX_VERSIONED_HASHES);

    assertThat(bellatrixShapeRoot).isNotEqualTo(denebShapeRoot);
  }

  @Test
  void sszSerialize_producesStableNonEmptyBytesConsistentWithTheRoot() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(2);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests();
    final NewPayloadRequest request =
        new NewPayloadRequest(executionPayload, versionedHashes, parentBeaconBlockRoot, List.of());

    final Bytes serialized1 =
        NewPayloadRequestHasher.sszSerialize(
            request, Optional.of(executionRequests), MAX_VERSIONED_HASHES);
    final Bytes serialized2 =
        NewPayloadRequestHasher.sszSerialize(
            request, Optional.of(executionRequests), MAX_VERSIONED_HASHES);
    final Bytes32 root =
        NewPayloadRequestHasher.hashTreeRoot(
            request, Optional.of(executionRequests), MAX_VERSIONED_HASHES);

    assertThat(serialized1.size()).isPositive();
    assertThat(serialized1).isEqualTo(serialized2);
    assertThat(root).isNotEqualTo(DEFAULT_ROOT);
  }
}
