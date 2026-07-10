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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

class ExecutionProofsAvailabilityCheckerTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

  private final ExecutionProofManager executionProofManager = mock(ExecutionProofManager.class);

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> delegate = mock(AvailabilityChecker.class);

  private ExecutionProofsAvailabilityChecker checker;

  @BeforeEach
  void setUp() {
    checker = new ExecutionProofsAvailabilityChecker(executionProofManager, block, delegate);
    // never resolves - if the checker were still waiting on this, the test would hang/timeout
    when(executionProofManager.validateBlockWithExecutionProofs(any()))
        .thenReturn(new SafeFuture<>());
  }

  @Test
  void initiateDataAvailabilityCheck_delegatesAndDoesNotBlock() {
    when(delegate.initiateDataAvailabilityCheck()).thenReturn(true);

    final boolean result = checker.initiateDataAvailabilityCheck();

    assertThat(result).isTrue();
    verify(delegate).initiateDataAvailabilityCheck();
  }

  @Test
  void getAvailabilityCheckResult_resolvesImmediatelyForwardingDelegateResultWhenValid() {
    final List<BlobSidecar> blobSidecars = List.of(dataStructureUtil.randomBlobSidecar());
    final DataAndValidationResult<BlobSidecar> delegateResult =
        DataAndValidationResult.validResult(blobSidecars);
    when(delegate.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(delegateResult));

    final DataAndValidationResult<SignedExecutionProof> result =
        checker.getAvailabilityCheckResult().join();

    // forwarded unchanged - same validation status and same underlying data, not replaced with a
    // differently-typed/empty execution-proof result
    assertThat(result.isValid()).isTrue();
    assertThat(result.getDataAsBlobSidecars()).contains(blobSidecars);
  }

  @Test
  void getAvailabilityCheckResult_triggersReconciliationInBackgroundWhenDelegateSucceeds() {
    when(delegate.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(DataAndValidationResult.notRequired()));

    checker.getAvailabilityCheckResult().join();

    verify(executionProofManager).validateBlockWithExecutionProofs(block);
  }

  @Test
  void getAvailabilityCheckResult_doesNotReconcileWhenDelegateFails() {
    when(delegate.getAvailabilityCheckResult())
        .thenReturn(
            SafeFuture.completedFuture(
                DataAndValidationResult.notAvailable(new RuntimeException("no blobs"))));

    final DataAndValidationResult<SignedExecutionProof> result =
        checker.getAvailabilityCheckResult().join();

    assertThat(result.isNotAvailable()).isTrue();
    verify(executionProofManager, never()).validateBlockWithExecutionProofs(any());
  }
}
