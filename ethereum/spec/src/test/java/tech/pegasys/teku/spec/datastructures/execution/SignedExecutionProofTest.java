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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedExecutionProofTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.ELECTRA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());

  private final SignedExecutionProofSchema signedExecutionProofSchema =
      schemaDefinitionsElectra.getSignedExecutionProofSchema();

  private final ExecutionProof message = dataStructureUtil.randomExecutionProof();
  private final UInt64 validatorIndex = dataStructureUtil.randomValidatorIndex();
  private final BLSSignature signature = dataStructureUtil.randomSignature();

  @Test
  public void objectEquality() {
    final SignedExecutionProof signedExecutionProof1 =
        signedExecutionProofSchema.create(message, validatorIndex, signature);
    final SignedExecutionProof signedExecutionProof2 =
        signedExecutionProofSchema.create(message, validatorIndex, signature);

    assertThat(signedExecutionProof1).isEqualTo(signedExecutionProof2);
  }

  @Test
  public void objectAccessorMethods() {
    final SignedExecutionProof signedExecutionProof =
        signedExecutionProofSchema.create(message, validatorIndex, signature);

    assertThat(signedExecutionProof.getMessage()).isEqualTo(message);
    assertThat(signedExecutionProof.getValidatorIndex()).isEqualTo(validatorIndex);
    assertThat(signedExecutionProof.getSignature()).isEqualTo(signature);
    assertThat(signedExecutionProof.getValidatorId()).isEqualTo(validatorIndex.intValue());
  }

  @Test
  public void roundTripSSZ() {
    final SignedExecutionProof signedExecutionProof =
        signedExecutionProofSchema.create(message, validatorIndex, signature);

    final Bytes sszBytes = signedExecutionProof.sszSerialize();
    final SignedExecutionProof deserializedObject =
        signedExecutionProofSchema.sszDeserialize(sszBytes);

    assertThat(signedExecutionProof).isEqualTo(deserializedObject);
  }

  @Test
  public void randomSignedExecutionProofRoundTripsViaDataStructureUtil() {
    final SignedExecutionProof signedExecutionProof =
        dataStructureUtil.randomSignedExecutionProof();

    final Bytes sszBytes = signedExecutionProof.sszSerialize();
    final SignedExecutionProof deserializedObject =
        signedExecutionProofSchema.sszDeserialize(sszBytes);

    assertThat(signedExecutionProof).isEqualTo(deserializedObject);
  }
}
