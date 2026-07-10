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

package tech.pegasys.teku.statetransition.executionproofs.verifier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Client for an external EIP-8025 proof verifier service (e.g. zkboost), which implements the
 * spec's {@code ProofEngine.verify_execution_proof(...)} out of process.
 */
public interface ExecutionProofVerifierClient {

  ExecutionProofVerifierClient NOOP =
      (newPayloadRequestRoot, proofType, proofData) -> SafeFuture.completedFuture(true);

  /**
   * Returns a future that completes with {@code true} if the external service confirms the proof is
   * valid, {@code false} if it confirms the proof is invalid, or fails if the service could not be
   * reached / did not respond usefully - callers should treat a failed future the same as "not yet
   * verified", not as "invalid" (see {@link
   * tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator}'s timeout
   * handling).
   */
  SafeFuture<Boolean> verify(Bytes32 newPayloadRequestRoot, int proofType, Bytes proofData);
}
