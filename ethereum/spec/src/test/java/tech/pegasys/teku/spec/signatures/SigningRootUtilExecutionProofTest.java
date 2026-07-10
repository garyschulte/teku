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

package tech.pegasys.teku.spec.signatures;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SigningRootUtilExecutionProofTest {

  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.ELECTRA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final UInt64 epoch = dataStructureUtil.randomEpoch();

  @Test
  void isStableForTheSameInputs() {
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();

    final Bytes root1 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof, epoch, forkInfo);
    final Bytes root2 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof, epoch, forkInfo);

    assertThat(root1).isEqualTo(root2);
  }

  @Test
  void differsForADifferentExecutionProof() {
    final ExecutionProof executionProof1 = dataStructureUtil.randomExecutionProof();
    final ExecutionProof executionProof2 = dataStructureUtil.randomExecutionProof();

    final Bytes root1 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof1, epoch, forkInfo);
    final Bytes root2 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof2, epoch, forkInfo);

    assertThat(root1).isNotEqualTo(root2);
  }

  @Test
  void differsForADifferentFork() {
    final ExecutionProof executionProof = dataStructureUtil.randomExecutionProof();
    final ForkInfo otherForkInfo = dataStructureUtil.randomForkInfo();

    final Bytes root1 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof, epoch, forkInfo);
    final Bytes root2 =
        signingRootUtil.signingRootForSignExecutionProof(executionProof, epoch, otherForkInfo);

    assertThat(root1).isNotEqualTo(root2);
  }
}
