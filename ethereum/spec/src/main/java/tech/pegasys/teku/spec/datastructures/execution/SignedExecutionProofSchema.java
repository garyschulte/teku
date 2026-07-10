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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PROOF_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedExecutionProofSchema
    extends ContainerSchema3<SignedExecutionProof, ExecutionProof, SszUInt64, SszSignature> {

  public SignedExecutionProofSchema(final SchemaRegistry schemaRegistry) {
    super(
        "SignedExecutionProof",
        namedSchema("message", schemaRegistry.get(EXECUTION_PROOF_SCHEMA)),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedExecutionProof create(
      final ExecutionProof message, final UInt64 validatorIndex, final BLSSignature signature) {
    return new SignedExecutionProof(this, message, validatorIndex, signature);
  }

  @Override
  public SignedExecutionProof createFromBackingNode(final TreeNode node) {
    return new SignedExecutionProof(this, node);
  }
}
