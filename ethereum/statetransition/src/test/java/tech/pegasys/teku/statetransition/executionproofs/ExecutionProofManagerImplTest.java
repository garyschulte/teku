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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

class ExecutionProofManagerImplTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionProofGossipValidator gossipValidator =
      mock(ExecutionProofGossipValidator.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ExecutionProofManagerImpl manager =
      new ExecutionProofManagerImpl(gossipValidator, proof -> {}, 1, asyncRunner, spec);

  @BeforeEach
  void setUp() {
    when(gossipValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
  }

  @Test
  void validateBlockWithExecutionProofs_findsProofsCachedByNewPayloadRequestRootNotByBlockRoot() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Bytes32 newPayloadRequestRoot = dataStructureUtil.randomBytes32();
    manager.recordNewPayloadRequestRoot(block.getRoot(), newPayloadRequestRoot);

    final SignedExecutionProof proof = randomSignedExecutionProofFor(newPayloadRequestRoot);
    manager.onLocallySubmittedExecutionProof(proof).join();

    final SafeFuture<DataAndValidationResult<SignedExecutionProof>> resultFuture =
        manager.validateBlockWithExecutionProofs(block);
    asyncRunner.executeQueuedActions();
    final DataAndValidationResult<SignedExecutionProof> result = resultFuture.join();

    assertThat(result.isValid()).isTrue();
    assertThat(result.data()).containsExactly(proof);
  }

  @Test
  void validateBlockWithExecutionProofs_notAvailableWhenRootNeverRecorded() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    final SafeFuture<DataAndValidationResult<SignedExecutionProof>> resultFuture =
        manager.validateBlockWithExecutionProofs(block);
    asyncRunner.executeQueuedActions();
    final DataAndValidationResult<SignedExecutionProof> result = resultFuture.join();

    assertThat(result.isNotAvailable()).isTrue();
  }

  private SignedExecutionProof randomSignedExecutionProofFor(final Bytes32 newPayloadRequestRoot) {
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    final ExecutionProofSchema executionProofSchema =
        schemaDefinitionsElectra.getExecutionProofSchema();
    return schemaDefinitionsElectra
        .getSignedExecutionProofSchema()
        .create(
            executionProofSchema.create(
                dataStructureUtil.randomBytes(5),
                1,
                executionProofSchema.getPublicInputSchema().create(newPayloadRequestRoot)),
            dataStructureUtil.randomValidatorIndex(),
            dataStructureUtil.randomSignature());
  }
}
