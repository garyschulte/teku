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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionProof;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.storage.client.RecentChainData;

class ExecutionProofGossipValidatorTest {

  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.ELECTRA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());

  private final BLSKeyPair proverKeyPair = BLSTestUtil.randomKeyPair(1);
  private final int proverValidatorIndex = 0;

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionProofManager executionProofManager = mock(ExecutionProofManager.class);

  private BeaconState state;
  private ExecutionProofGossipValidator validator;

  @BeforeEach
  void setUp() {
    state =
        new BeaconStateTestBuilder(dataStructureUtil)
            .slot(dataStructureUtil.randomSlot().longValue())
            .validator(
                new Validator(
                    proverKeyPair.getPublicKey(),
                    dataStructureUtil.randomBytes32(),
                    spec.getGenesisSpecConfig().getMaxEffectiveBalance(),
                    false,
                    UInt64.ZERO,
                    UInt64.ZERO,
                    FAR_FUTURE_EPOCH,
                    FAR_FUTURE_EPOCH))
            .build();
    when(recentChainData.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(executionProofManager.isKnownNewPayloadRequestRoot(any())).thenReturn(true);

    validator = ExecutionProofGossipValidator.create(spec, recentChainData);
    validator.setExecutionProofManager(executionProofManager);
  }

  private SignedExecutionProof signedProof(final BLSKeyPair keyPair, final UInt64 validatorIndex) {
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();
    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionProof(
            executionProof, epoch, state.getForkInfo());
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), signingRoot);
    return schemaDefinitionsElectra
        .getSignedExecutionProofSchema()
        .create(executionProof, validatorIndex, signature);
  }

  @Test
  void acceptsAValidProof() {
    final SignedExecutionProof proof =
        signedProof(proverKeyPair, UInt64.valueOf(proverValidatorIndex));

    assertThat(validator.validate(proof)).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void rejectsAnInvalidSignature() {
    final SignedExecutionProof proof =
        signedProof(BLSTestUtil.randomKeyPair(2), UInt64.valueOf(proverValidatorIndex));

    assertThat(validator.validate(proof).join().code()).isEqualTo(ValidationResultCode.REJECT);
  }

  @Test
  void rejectsAnOutOfRangeValidatorIndex() {
    final SignedExecutionProof proof = signedProof(proverKeyPair, UInt64.valueOf(99));

    assertThat(validator.validate(proof).join().code()).isEqualTo(ValidationResultCode.REJECT);
  }

  @Test
  void ignoresAnUnknownNewPayloadRequestRoot() {
    when(executionProofManager.isKnownNewPayloadRequestRoot(any())).thenReturn(false);
    final SignedExecutionProof proof =
        signedProof(proverKeyPair, UInt64.valueOf(proverValidatorIndex));

    assertThat(validator.validate(proof)).isCompletedWithValue(InternalValidationResult.IGNORE);
  }

  @Test
  void ignoresADuplicateProof() {
    final SignedExecutionProof proof =
        signedProof(proverKeyPair, UInt64.valueOf(proverValidatorIndex));

    assertThat(validator.validate(proof)).isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(validator.validate(proof)).isCompletedWithValue(InternalValidationResult.IGNORE);
  }
}
