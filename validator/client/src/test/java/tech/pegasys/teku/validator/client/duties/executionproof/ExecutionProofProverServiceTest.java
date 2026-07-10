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

package tech.pegasys.teku.validator.client.duties.executionproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ProofType;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.ValidatorIndexProvider;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class ExecutionProofProverServiceTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ExecutionProofProverClient proverClient = mock(ExecutionProofProverClient.class);
  private final Signer signer = mock(Signer.class);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final Validator validator = new Validator(publicKey, signer, Optional::empty);
  private final OwnedValidators validators = new OwnedValidators();

  private final ExecutionProofProverService service =
      new ExecutionProofProverService(
          spec,
          forkProvider,
          validatorApiChannel,
          validators,
          validatorIndexProvider,
          proverClient,
          ProofType.RETH_ZISK);

  @BeforeEach
  void setUp() {
    validators.addValidator(validator);
    when(forkProvider.getForkInfo(any()))
        .thenReturn(SafeFuture.completedFuture(mock(ForkInfo.class)));
    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(SafeFuture.completedFuture(Map.of(publicKey, 7)));
    when(signer.signExecutionProof(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
  }

  @Test
  void onHeadUpdate_provesAndSubmitsProofForBlockWithExecutionPayload() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Bytes32 headBlockRoot = block.getRoot();
    when(validatorApiChannel.getBeaconBlockByRoot(headBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    final Bytes proofData = Bytes.fromHexString("0xabcdef");
    when(proverClient.requestProof(any(), eq(ProofType.RETH_ZISK.getValue()), any()))
        .thenReturn(SafeFuture.completedFuture(proofData));
    when(validatorApiChannel.sendSignedExecutionProof(any())).thenReturn(SafeFuture.COMPLETE);

    service.onHeadUpdate(
        block.getSlot(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        headBlockRoot);

    verify(proverClient).requestProof(any(), eq(ProofType.RETH_ZISK.getValue()), any());
    final ArgumentCaptor<SignedExecutionProof> captor =
        ArgumentCaptor.forClass(SignedExecutionProof.class);
    verify(validatorApiChannel).sendSignedExecutionProof(captor.capture());
    final SignedExecutionProof submitted = captor.getValue();
    assertThat(submitted.getValidatorId()).isEqualTo(7);
    final ExecutionProof executionProof = submitted.getMessage();
    assertThat(executionProof.getProofData().getBytes()).isEqualTo(proofData);
  }

  @Test
  void onHeadUpdate_doesNothingWhenBlockHasNoValidators() {
    final OwnedValidators emptyValidators = new OwnedValidators();
    final ExecutionProofProverService serviceWithNoValidators =
        new ExecutionProofProverService(
            spec,
            forkProvider,
            validatorApiChannel,
            emptyValidators,
            validatorIndexProvider,
            proverClient,
            ProofType.RETH_ZISK);

    serviceWithNoValidators.onHeadUpdate(
        UInt64.ONE,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());

    verify(validatorApiChannel, never()).getBeaconBlockByRoot(any());
  }

  @Test
  void onHeadUpdate_doesNotRequestProofForPreMergeBlock() {
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil phase0DataStructureUtil = new DataStructureUtil(phase0Spec);
    final SignedBeaconBlock block = phase0DataStructureUtil.randomSignedBeaconBlock();
    when(validatorApiChannel.getBeaconBlockByRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    service.onHeadUpdate(
        block.getSlot(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        block.getRoot());

    verify(proverClient, never()).requestProof(any(), anyInt(), any());
  }

  @Test
  void onHeadUpdate_doesNothingWhenBlockCannotBeFetched() {
    final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getBeaconBlockByRoot(headBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    service.onHeadUpdate(
        UInt64.ONE,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        headBlockRoot);

    verify(proverClient, never()).requestProof(any(), anyInt(), any());
  }
}
