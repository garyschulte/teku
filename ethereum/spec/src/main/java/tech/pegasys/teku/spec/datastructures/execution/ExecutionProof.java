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

import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * EIP-8025 {@code ExecutionProof} — matches {@code specs/_features/eip8025/beacon-chain.md} (also
 * the shape used by Prysm's {@code optional-proofs} branch): {@code proof_data}, {@code proof_type}
 * (an opaque EL/zkVM-combo identifier, not a closed enum), and the {@link PublicInput} the proof
 * attests to.
 */
public class ExecutionProof extends Container3<ExecutionProof, SszByteList, SszByte, PublicInput> {

  public ExecutionProof(final ExecutionProofSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public ExecutionProof(
      final ExecutionProofSchema schema,
      final SszByteList proofData,
      final SszByte proofType,
      final PublicInput publicInput) {
    super(schema, proofData, proofType, publicInput);
  }

  public SszByteList getProofData() {
    return getField0();
  }

  /** Returns the unsigned {@code uint8} EL/zkVM-combo identifier. */
  public int getProofType() {
    return Byte.toUnsignedInt(getField1().get());
  }

  public PublicInput getPublicInput() {
    return getField2();
  }

  @Override
  public ExecutionProofSchema getSchema() {
    return (ExecutionProofSchema) super.getSchema();
  }
}
