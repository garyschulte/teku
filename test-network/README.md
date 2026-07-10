Kurtosis Testnet
==============

[Kurtosis](https://github.com/kurtosis-tech/ethereum-package) can be used to spin up a Docker-based 
test network. A sample [network_params.yaml](./network_params.yaml) configuration file has been
provided to run a basic 4-node Teku/Besu network with a mainnet preset and a total of 256 validators (64 per node).

For exhaustive Kurtosis configuration options, please check the full YAML schema [here](https://github.com/kurtosis-tech/ethereum-package#configuration).

How To Run
----------

* Make sure Kurtosis is [installed](https://docs.kurtosis.com/install/).
* Run `kurtosis run --enclave test-network github.com/ethpandaops/ethereum-package --args-file network_params.yaml`

If you would like to use a locally built Teku image, first run `./gradlew distDocker` in the root
directory and then use `cl_image: consensys/teku:develop` in the `network_params.yaml` file.

To tear down the test network, you can simply run `kurtosis enclave rm -f test-network`.

Monitoring/Debugging
----------

[Dora](https://github.com/ethpandaops/dora), [el_forkmon](https://github.com/ethereum/nodemonitor),
Prometheus/Grafana and [rpc-snooper](https://github.com/ethpandaops/rpc-snooper) have been enabled
to allow for an easier monitoring/debugging of the network/nodes.

EIP-8025 (Optional Execution Proofs) Interop Devnet
----------

[eip8025-devnet.yaml](./eip8025-devnet.yaml) runs Teku alongside `eth-act/lighthouse`'s
`optional-proofs` branch, both pointed at a shared `zkboost` prover/verifier service - the same
devnet shape as `ethereum-package`'s own `.github/tests/zkboost.yaml`, with a Teku participant added
using this branch's `--Xexecution-proof-*` flags. See the comments at the top of that file for the
exact success criteria and how to run it; it needs a Teku image built from this branch
(`./gradlew distDocker`) since the flags it exercises aren't in any released image yet.




