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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class PublicInputTest {

  private final PublicInputSchema schema = new PublicInputSchema();
  private final Bytes32 newPayloadRequestRoot = Bytes32.fromHexStringLenient("0x01");

  @Test
  public void objectEquality() {
    final PublicInput publicInput1 = schema.create(newPayloadRequestRoot);
    final PublicInput publicInput2 = schema.create(newPayloadRequestRoot);

    assertThat(publicInput1).isEqualTo(publicInput2);
  }

  @Test
  public void objectAccessorMethods() {
    final PublicInput publicInput = schema.create(newPayloadRequestRoot);

    assertThat(publicInput.getNewPayloadRequestRoot().get()).isEqualTo(newPayloadRequestRoot);
  }

  @Test
  public void roundTripSSZ() {
    final PublicInput publicInput = schema.create(newPayloadRequestRoot);

    final Bytes sszBytes = publicInput.sszSerialize();
    final PublicInput deserializedObject = schema.sszDeserialize(sszBytes);

    assertThat(publicInput).isEqualTo(deserializedObject);
  }
}
