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

package tech.pegasys.teku.validator.client.duties.executionproof;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Client for an external, zkboost-shaped EIP-8025 prover service: submits a block for proving and
 * asynchronously returns the resulting proof bytes once the prover completes.
 */
public interface ExecutionProofProverClient {

  ExecutionProofProverClient NOOP =
      (newPayloadRequestRoot, proofType, blockSsz) ->
          SafeFuture.failedFuture(
              new UnsupportedOperationException("No execution proof prover endpoint configured"));

  /**
   * Requests a proof of type {@code proofType} for the block whose SSZ-serialized bytes are {@code
   * blockSsz}, keyed by its {@code new_payload_request_root}. The prover is expected to be a
   * long-running, asynchronous job - the returned future resolves once the proof is ready.
   */
  SafeFuture<Bytes> requestProof(Bytes32 newPayloadRequestRoot, int proofType, Bytes blockSsz);
}
