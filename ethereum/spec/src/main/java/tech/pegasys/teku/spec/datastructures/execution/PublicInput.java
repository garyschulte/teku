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

import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * EIP-8025 {@code PublicInput} — the value an {@link ExecutionProof} attests to: the hash-tree-root
 * of the {@code NewPayloadRequest} the proof was generated against.
 */
public class PublicInput extends Container1<PublicInput, SszBytes32> {

  public PublicInput(final PublicInputSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public PublicInput(final PublicInputSchema schema, final SszBytes32 newPayloadRequestRoot) {
    super(schema, newPayloadRequestRoot);
  }

  public SszBytes32 getNewPayloadRequestRoot() {
    return getField0();
  }

  @Override
  public PublicInputSchema getSchema() {
    return (PublicInputSchema) super.getSchema();
  }
}
