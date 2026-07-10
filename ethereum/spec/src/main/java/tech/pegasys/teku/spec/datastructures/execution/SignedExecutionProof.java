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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.MessageWithValidatorId;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/**
 * EIP-8025 {@code SignedExecutionProof} — an {@link ExecutionProof} plus the index and BLS
 * signature of the active-validator "prover" that generated it, per {@code
 * specs/_features/eip8025/beacon-chain.md}.
 */
public class SignedExecutionProof
    extends Container3<SignedExecutionProof, ExecutionProof, SszUInt64, SszSignature>
    implements MessageWithValidatorId {

  SignedExecutionProof(
      final SignedExecutionProofSchema schema,
      final ExecutionProof message,
      final UInt64 validatorIndex,
      final BLSSignature signature) {
    super(schema, message, SszUInt64.of(validatorIndex), new SszSignature(signature));
  }

  SignedExecutionProof(final SignedExecutionProofSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public ExecutionProof getMessage() {
    return getField0();
  }

  public UInt64 getValidatorIndex() {
    return getField1().get();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }

  @Override
  public int getValidatorId() {
    return getValidatorIndex().intValue();
  }

  @Override
  public SignedExecutionProofSchema getSchema() {
    return (SignedExecutionProofSchema) super.getSchema();
  }
}
