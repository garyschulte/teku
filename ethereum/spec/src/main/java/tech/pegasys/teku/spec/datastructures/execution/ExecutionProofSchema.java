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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionProofSchema
    extends ContainerSchema3<ExecutionProof, SszByteList, SszByte, PublicInput> {

  // Matches Prysm's optional-proofs branch (OffchainLabs/prysm), the interop target for this
  // feature. The eip8025 consensus-specs text currently says 4 MiB but that constant is still
  // in flux upstream; Lighthouse's earlier prototype used 1 MiB. Revisit once the spec settles.
  public static final long MAX_PROOF_SIZE = 300_000;

  public ExecutionProofSchema() {
    super(
        "ExecutionProof",
        namedSchema("proof_data", SszByteListSchema.create(MAX_PROOF_SIZE)),
        namedSchema("proof_type", SszPrimitiveSchemas.UINT8_SCHEMA),
        namedSchema("public_input", new PublicInputSchema()));
  }

  public ExecutionProof create(
      final SszByteList proofData, final SszByte proofType, final PublicInput publicInput) {
    return new ExecutionProof(this, proofData, proofType, publicInput);
  }

  public ExecutionProof create(
      final Bytes proofData, final int proofType, final PublicInput publicInput) {
    return create(
        getProofDataSchema().fromBytes(proofData), SszByte.asUInt8(proofType), publicInput);
  }

  @Override
  public ExecutionProof createFromBackingNode(final TreeNode node) {
    return new ExecutionProof(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszByteListSchema<SszByteList> getProofDataSchema() {
    return (SszByteListSchema<SszByteList>) getFieldSchema0();
  }

  public PublicInputSchema getPublicInputSchema() {
    return (PublicInputSchema) getFieldSchema2();
  }
}
