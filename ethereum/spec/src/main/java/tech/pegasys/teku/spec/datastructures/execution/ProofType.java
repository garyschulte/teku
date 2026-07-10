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

import java.util.Arrays;
import java.util.Optional;

/**
 * The canonical EIP-8025 zkVM/prover identifiers, confirmed against {@code eth-act/lighthouse}'s
 * {@code optional-proofs} branch ({@code beacon_node/execution_layer/src/eip8025/types.rs}) - the
 * actual interop reference implementation. The numeric value is what's carried in the {@link
 * ExecutionProof#getProofType()} SSZ field; the identifier is the kebab-case string a
 * zkboost-shaped prover/verifier service expects at its HTTP boundary (query params, URL paths, SSE
 * payloads).
 */
public enum ProofType {
  ETHREX_RISC0(0, "ethrex-risc0"),
  ETHREX_SP1(1, "ethrex-sp1"),
  ETHREX_ZISK(2, "ethrex-zisk"),
  RETH_OPENVM(3, "reth-openvm"),
  RETH_RISC0(4, "reth-risc0"),
  RETH_SP1(5, "reth-sp1"),
  RETH_ZISK(6, "reth-zisk");

  private final int value;
  private final String identifier;

  ProofType(final int value, final String identifier) {
    this.value = value;
    this.identifier = identifier;
  }

  public int getValue() {
    return value;
  }

  public String getIdentifier() {
    return identifier;
  }

  public static Optional<ProofType> fromValue(final int value) {
    return Arrays.stream(values()).filter(proofType -> proofType.value == value).findFirst();
  }

  public static Optional<ProofType> fromIdentifier(final String identifier) {
    return Arrays.stream(values())
        .filter(proofType -> proofType.identifier.equals(identifier))
        .findFirst();
  }
}
