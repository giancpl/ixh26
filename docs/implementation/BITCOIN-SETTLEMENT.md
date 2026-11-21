# Bitcoin/OpenTimestamps settlement guide

The canonical operational documentation is [SETTLEMENT.md](../SETTLEMENT.md).
It covers installation, the Python API/CLI, Bitcoin Core including pruned nodes,
mainnet/regtest selection, Docker lifecycle, statuses, exit codes and limitations.
All commands there run from the repository root.

This entry point is retained for links introduced by the crypto documentation
reorganisation. Keeping one operational guide avoids divergent setup instructions.

The settlement package receives opaque bytes and does not authenticate photo
origin. For PhoTO's two independent Nostr/v3 proofs and operator configuration,
see [native v3 integration](../V3-APP-INTEGRATION.md). For the cryptographic
construction and trust boundaries, see [the technical guide](../crypto/README.md).
