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

import org.junit.jupiter.api.Test;

class ProofTypeTest {

  @Test
  void fromValue_matchesZkboostsCanonicalMapping() {
    assertThat(ProofType.fromValue(0)).contains(ProofType.ETHREX_OPENVM);
    assertThat(ProofType.fromValue(1)).contains(ProofType.ETHREX_SP1);
    assertThat(ProofType.fromValue(2)).contains(ProofType.ETHREX_ZISK);
    assertThat(ProofType.fromValue(3)).contains(ProofType.RETH_OPENVM);
    assertThat(ProofType.fromValue(4)).contains(ProofType.RETH_SP1);
    assertThat(ProofType.fromValue(5)).contains(ProofType.RETH_ZISK);
    assertThat(ProofType.fromValue(6)).isEmpty();
  }

  @Test
  void fromIdentifier_matchesZkboostsCanonicalMapping() {
    assertThat(ProofType.fromIdentifier("reth-zisk")).contains(ProofType.RETH_ZISK);
    assertThat(ProofType.fromIdentifier("ethrex-openvm")).contains(ProofType.ETHREX_OPENVM);
    assertThat(ProofType.fromIdentifier("not-a-real-zkvm")).isEmpty();
  }

  @Test
  void identifiersAreKebabCase() {
    for (final ProofType proofType : ProofType.values()) {
      assertThat(proofType.getIdentifier()).matches("[a-z0-9]+(-[a-z0-9]+)*");
    }
  }
}
