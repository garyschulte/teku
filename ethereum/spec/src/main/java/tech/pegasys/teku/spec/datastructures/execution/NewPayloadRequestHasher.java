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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

/**
 * Computes {@code new_payload_request_root = hash_tree_root(NewPayloadRequest)} for EIP-8025 {@link
 * PublicInput}, per {@code specs/gloas/fork-choice.md}'s {@code verify_execution_payload_envelope}
 * (which is where {@code eip8025/fork-choice.md} constructs and uses the {@code NewPayloadRequest}
 * it feeds to the {@code ProofEngine}).
 *
 * <p>{@code NewPayloadRequest} itself is a plain (non-SSZ) dataclass in consensus-specs, not a
 * {@code Container} - it has no spec-defined canonical hash-tree-root. This class treats it as an
 * SSZ container matching its dataclass field order (execution_payload, versioned_hashes,
 * parent_beacon_block_root, execution_requests) since that is the only construction implied by
 * {@code request_proofs(...) -> Root} returning a root value at all. This has NOT been verified
 * byte-for-byte against Prysm's Go implementation or an official cross-client test vector - treat
 * as best-effort pending such verification (see EIP-8025 gap-analysis M2 notes).
 *
 * <p>Teku's own {@link NewPayloadRequest} POJO stores {@code execution_requests} pre-flattened to
 * the engine-API {@code List<Bytes>} shape, but the spec's field is the typed {@code
 * ExecutionRequests} container itself (confirmed by {@code assert
 * hash_tree_root(envelope.execution_requests) == bid.execution_requests_root} in
 * gloas/fork-choice.md) - callers must pass that typed object separately since it is not
 * recoverable from the flattened bytes.
 */
public final class NewPayloadRequestHasher {

  private NewPayloadRequestHasher() {}

  public static Bytes32 hashTreeRoot(
      final NewPayloadRequest request,
      final Optional<ExecutionRequests> executionRequests,
      final int maxVersionedHashesPerBlock) {
    final ExecutionPayload executionPayload = request.getExecutionPayload();
    if (request.getVersionedHashes().isEmpty()) {
      return hashBellatrix(executionPayload);
    }
    final List<VersionedHash> versionedHashes = request.getVersionedHashes().orElseThrow();
    final Bytes32 parentBeaconBlockRoot =
        request
            .getParentBeaconBlockRoot()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "parentBeaconBlockRoot must be present when versionedHashes are present"));
    if (executionRequests.isEmpty()) {
      return hashDeneb(
          executionPayload, versionedHashes, parentBeaconBlockRoot, maxVersionedHashesPerBlock);
    }
    return hashElectra(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequests.get(),
        maxVersionedHashesPerBlock);
  }

  private static Bytes32 hashBellatrix(final ExecutionPayload executionPayload) {
    return new BellatrixSchema(executionPayloadSchema(executionPayload))
        .create(executionPayload)
        .hashTreeRoot();
  }

  private static Bytes32 hashDeneb(
      final ExecutionPayload executionPayload,
      final List<VersionedHash> versionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final int maxVersionedHashesPerBlock) {
    final DenebSchema schema =
        new DenebSchema(executionPayloadSchema(executionPayload), maxVersionedHashesPerBlock);
    return schema
        .create(
            executionPayload, versionedHashesList(versionedHashes, schema), parentBeaconBlockRoot)
        .hashTreeRoot();
  }

  private static Bytes32 hashElectra(
      final ExecutionPayload executionPayload,
      final List<VersionedHash> versionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final ExecutionRequests executionRequests,
      final int maxVersionedHashesPerBlock) {
    final ElectraSchema schema =
        new ElectraSchema(
            executionPayloadSchema(executionPayload),
            maxVersionedHashesPerBlock,
            executionRequests.getSchema());
    return schema
        .create(
            executionPayload,
            versionedHashesList(versionedHashes, schema),
            parentBeaconBlockRoot,
            executionRequests)
        .hashTreeRoot();
  }

  @SuppressWarnings("unchecked")
  private static SszSchema<ExecutionPayload> executionPayloadSchema(
      final ExecutionPayload executionPayload) {
    return (SszSchema<ExecutionPayload>) executionPayload.getSchema();
  }

  private static SszList<SszBytes32> versionedHashesList(
      final List<VersionedHash> versionedHashes, final HasVersionedHashesSchema schemaHolder) {
    return schemaHolder
        .getVersionedHashesSchema()
        .createFromElements(versionedHashes.stream().map(vh -> SszBytes32.of(vh.get())).toList());
  }

  private interface HasVersionedHashesSchema {
    SszListSchema<SszBytes32, SszList<SszBytes32>> getVersionedHashesSchema();
  }

  @SuppressWarnings("unchecked")
  private static SszListSchema<SszBytes32, SszList<SszBytes32>> versionedHashesSchema(
      final int maxLength) {
    return (SszListSchema<SszBytes32, SszList<SszBytes32>>)
        (SszListSchema<SszBytes32, ?>)
            SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, maxLength);
  }

  private static final class Bellatrix extends Container1<Bellatrix, ExecutionPayload> {
    private Bellatrix(final BellatrixSchema schema, final ExecutionPayload executionPayload) {
      super(schema, executionPayload);
    }

    private Bellatrix(final BellatrixSchema schema, final TreeNode node) {
      super(schema, node);
    }
  }

  private static final class BellatrixSchema extends ContainerSchema1<Bellatrix, ExecutionPayload> {
    private BellatrixSchema(final SszSchema<ExecutionPayload> executionPayloadSchema) {
      super("NewPayloadRequestBellatrix", namedSchema("execution_payload", executionPayloadSchema));
    }

    private Bellatrix create(final ExecutionPayload executionPayload) {
      return new Bellatrix(this, executionPayload);
    }

    @Override
    public Bellatrix createFromBackingNode(final TreeNode node) {
      return new Bellatrix(this, node);
    }
  }

  private static final class Deneb
      extends Container3<Deneb, ExecutionPayload, SszList<SszBytes32>, SszBytes32> {
    private Deneb(
        final DenebSchema schema,
        final ExecutionPayload executionPayload,
        final SszList<SszBytes32> versionedHashes,
        final Bytes32 parentBeaconBlockRoot) {
      super(schema, executionPayload, versionedHashes, SszBytes32.of(parentBeaconBlockRoot));
    }

    private Deneb(final DenebSchema schema, final TreeNode node) {
      super(schema, node);
    }
  }

  private static final class DenebSchema
      extends ContainerSchema3<Deneb, ExecutionPayload, SszList<SszBytes32>, SszBytes32>
      implements HasVersionedHashesSchema {
    private DenebSchema(
        final SszSchema<ExecutionPayload> executionPayloadSchema,
        final int maxVersionedHashesPerBlock) {
      super(
          "NewPayloadRequestDeneb",
          namedSchema("execution_payload", executionPayloadSchema),
          namedSchema("versioned_hashes", versionedHashesSchema(maxVersionedHashesPerBlock)),
          namedSchema("parent_beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    private Deneb create(
        final ExecutionPayload executionPayload,
        final SszList<SszBytes32> versionedHashes,
        final Bytes32 parentBeaconBlockRoot) {
      return new Deneb(this, executionPayload, versionedHashes, parentBeaconBlockRoot);
    }

    @Override
    public Deneb createFromBackingNode(final TreeNode node) {
      return new Deneb(this, node);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszListSchema<SszBytes32, SszList<SszBytes32>> getVersionedHashesSchema() {
      return (SszListSchema<SszBytes32, SszList<SszBytes32>>) getFieldSchema1();
    }
  }

  private static final class Electra
      extends Container4<
          Electra, ExecutionPayload, SszList<SszBytes32>, SszBytes32, ExecutionRequests> {
    private Electra(
        final ElectraSchema schema,
        final ExecutionPayload executionPayload,
        final SszList<SszBytes32> versionedHashes,
        final Bytes32 parentBeaconBlockRoot,
        final ExecutionRequests executionRequests) {
      super(
          schema,
          executionPayload,
          versionedHashes,
          SszBytes32.of(parentBeaconBlockRoot),
          executionRequests);
    }

    private Electra(final ElectraSchema schema, final TreeNode node) {
      super(schema, node);
    }
  }

  private static final class ElectraSchema
      extends ContainerSchema4<
          Electra, ExecutionPayload, SszList<SszBytes32>, SszBytes32, ExecutionRequests>
      implements HasVersionedHashesSchema {
    private ElectraSchema(
        final SszSchema<ExecutionPayload> executionPayloadSchema,
        final int maxVersionedHashesPerBlock,
        final SszSchema<ExecutionRequests> executionRequestsSchema) {
      super(
          "NewPayloadRequestElectra",
          namedSchema("execution_payload", executionPayloadSchema),
          namedSchema("versioned_hashes", versionedHashesSchema(maxVersionedHashesPerBlock)),
          namedSchema("parent_beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("execution_requests", executionRequestsSchema));
    }

    private Electra create(
        final ExecutionPayload executionPayload,
        final SszList<SszBytes32> versionedHashes,
        final Bytes32 parentBeaconBlockRoot,
        final ExecutionRequests executionRequests) {
      return new Electra(
          this, executionPayload, versionedHashes, parentBeaconBlockRoot, executionRequests);
    }

    @Override
    public Electra createFromBackingNode(final TreeNode node) {
      return new Electra(this, node);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszListSchema<SszBytes32, SszList<SszBytes32>> getVersionedHashesSchema() {
      return (SszListSchema<SszBytes32, SszList<SszBytes32>>) getFieldSchema1();
    }
  }
}
