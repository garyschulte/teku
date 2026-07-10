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

package tech.pegasys.teku.spec.logic.common.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
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

class OperationSignatureVerifierExecutionProofTest {

  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.ELECTRA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final OperationSignatureVerifier operationSignatureVerifier =
      spec.getGenesisSpec().operationSignatureVerifier();

  private final BLSKeyPair proverKeyPair = BLSTestUtil.randomKeyPair(1);
  private final int proverValidatorIndex = 0;

  private BeaconState stateWithProver() {
    return new BeaconStateTestBuilder(dataStructureUtil)
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
  }

  private SignedExecutionProof signWithKeyPair(
      final BeaconState state, final ExecutionProof executionProof, final BLSKeyPair keyPair) {
    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionProof(
            executionProof, epoch, state.getForkInfo());
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), signingRoot);
    return SchemaDefinitionsElectra.required(spec.atSlot(state.getSlot()).getSchemaDefinitions())
        .getSignedExecutionProofSchema()
        .create(executionProof, UInt64.valueOf(proverValidatorIndex), signature);
  }

  @Test
  void verifiesAValidSignature() {
    final BeaconState state = stateWithProver();
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();
    final SignedExecutionProof signedExecutionProof =
        signWithKeyPair(state, executionProof, proverKeyPair);

    assertThat(
            operationSignatureVerifier.verifyExecutionProofSignature(
                state, signedExecutionProof, BLSSignatureVerifier.SIMPLE))
        .isTrue();
  }

  @Test
  void rejectsASignatureFromTheWrongKey() {
    final BeaconState state = stateWithProver();
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();
    final BLSKeyPair wrongKeyPair = BLSTestUtil.randomKeyPair(2);
    final SignedExecutionProof signedExecutionProof =
        signWithKeyPair(state, executionProof, wrongKeyPair);

    assertThat(
            operationSignatureVerifier.verifyExecutionProofSignature(
                state, signedExecutionProof, BLSSignatureVerifier.SIMPLE))
        .isFalse();
  }

  @Test
  void rejectsAnUnknownValidatorIndex() {
    final BeaconState state = stateWithProver();
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();
    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionProof(
            executionProof, epoch, state.getForkInfo());
    final BLSSignature signature = BLS.sign(proverKeyPair.getSecretKey(), signingRoot);
    final SignedExecutionProof signedExecutionProof =
        SchemaDefinitionsElectra.required(spec.atSlot(state.getSlot()).getSchemaDefinitions())
            .getSignedExecutionProofSchema()
            .create(executionProof, UInt64.valueOf(99), signature);

    assertThat(
            operationSignatureVerifier.verifyExecutionProofSignature(
                state, signedExecutionProof, BLSSignatureVerifier.SIMPLE))
        .isFalse();
  }
}
