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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionProofTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.ELECTRA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionProofSchema executionProofSchema =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
          .getExecutionProofSchema();

  private final Bytes proofData = Bytes.fromHexString("0x0123");
  private final int proofType = 4;
  private final PublicInput publicInput =
      executionProofSchema.getPublicInputSchema().create(dataStructureUtil.randomBytes32());

  @Test
  public void objectEquality() {
    final ExecutionProof executionProof1 =
        executionProofSchema.create(proofData, proofType, publicInput);
    final ExecutionProof executionProof2 =
        executionProofSchema.create(proofData, proofType, publicInput);

    assertThat(executionProof1).isEqualTo(executionProof2);
  }

  @Test
  public void objectAccessorMethods() {
    final ExecutionProof executionProof =
        executionProofSchema.create(proofData, proofType, publicInput);

    assertThat(executionProof.getProofData().getBytes()).isEqualTo(proofData);
    assertThat(executionProof.getProofType()).isEqualTo(proofType);
    assertThat(executionProof.getPublicInput()).isEqualTo(publicInput);
  }

  @Test
  public void proofTypeIsUnsignedUint8() {
    final ExecutionProof executionProof = executionProofSchema.create(proofData, 200, publicInput);

    assertThat(executionProof.getProofType()).isEqualTo(200);
  }

  @Test
  public void roundTripSSZ() {
    final ExecutionProof executionProof =
        executionProofSchema.create(proofData, proofType, publicInput);

    final Bytes sszBytes = executionProof.sszSerialize();
    final ExecutionProof deserializedObject = executionProofSchema.sszDeserialize(sszBytes);

    assertThat(executionProof).isEqualTo(deserializedObject);
  }

  @Test
  public void randomExecutionProofRoundTripsViaDataStructureUtil() {
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();

    final Bytes sszBytes = executionProof.sszSerialize();
    final ExecutionProof deserializedObject = executionProofSchema.sszDeserialize(sszBytes);

    assertThat(executionProof).isEqualTo(deserializedObject);
  }
}
