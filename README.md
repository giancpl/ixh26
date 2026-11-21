# UPP Bitcoin settlement

The proposed FingerprinTO receipt implementation is in `fingerprinto/`.
Current manufacturer/PRNU v3 work follows the
[v3 roadmap](docs/CRYPTO-V3-ROADMAP.md), with explicit empirical feasibility gates.
For the saved state before the crypto redesign, start with the
[crypto checkpoint](docs/CRYPTO-CHECKPOINT-2026-11-21.md).
Its independent bundle CLI, current checks and remaining Android/origin work
are described in [implementation progress](docs/implementation-progress.md).
The proposed protocol is in [the cryptographic manual](docs/crypto.md).

A standalone Python 3.11+ component that timestamps arbitrary **opaque binary
files** with OpenTimestamps (OTS). It hashes locally, stores standard portable
`.ots` proofs, retrieves upgrades, and verifies Bitcoin attestations against a
locally configured, synchronized Bitcoin Core node. Mainnet is the default;
regtest must be selected explicitly.

It does not parse manifests, process photos, verify signatures, generate keys,
or implement camera, device, C2PA, wallet, or authentication functionality.
No Bitcoin balance, wallet, seed, API key, or paid transaction is needed.
The optional local regtest calendar has its own development wallet with mined
test coins; the settlement API does not manage wallets.

## Local regtest: complete Docker environment

Docker Engine and Docker Compose are the only host dependencies. From the repo:

```bash
./scripts/regtest-demo.sh
```

The first build downloads dependencies. The script starts Bitcoin Core 30.2,
initializes the dedicated `ots` wallet and official OTS calendar, then runs a
real local lifecycle. It checks `PENDING` before mining, mines two blocks,
upgrades the standard `.ots` proof, verifies `CONFIRMED` against the regtest
node, and rejects a modified payload as `INVALID`. The final JSON includes the
artifact directory and independently checked block height/time. The lifecycle
has a 120-second deadline after startup; there is no continuous mining process.

Core binaries are checked against pinned official SHA-256 values for amd64 and
arm64. The calendar source is pinned to commit
`1e955ea3b036453bf795505d923f2643f773810c` and uses a separate Python environment
because its OTS dependency version differs from the settlement client's.

Everything runs on an internal Docker network, without published host ports.
Blockchain/wallet, calendar database/HMAC key, and demo artifacts have separate
persistent volumes. The bootstrap reuses existing state on subsequent starts.
The wallet is dedicated to the calendar; leave other wallet operations out of
this development environment. Each demo creates a distinct artifact directory
and serializes demo runs with a lock.

Every result has `network: "mainnet"` or `network: "regtest"`. **A regtest
`CONFIRMED` is evidence against your local development chain, not a mainnet
settlement.** A successful regtest verification also includes diagnostic code
`regtest_only`. The `.ots` format itself has no network identifier: select the
expected network explicitly and do not infer it from an untrusted JSON receipt.

To start services without running the demo:

```bash
docker compose -f compose.regtest.yml build
docker compose -f compose.regtest.yml up -d --wait --wait-timeout 120 bitcoin bootstrap calendar
```

Run individual commands in the settlement container:

```bash
docker compose -f compose.regtest.yml run --rm settlement stamp \
  /app/examples/example_payload.bin --out /artifacts/manual \
  --network regtest --calendar http://calendar:14788

# Pending before mining: exit code 2 is expected.
docker compose -f compose.regtest.yml run --rm settlement verify \
  /app/examples/example_payload.bin /artifacts/manual/example_payload.bin.ots \
  --network regtest --bitcoin-conf /config/client.conf

# Wait for an OTS transaction in the mempool before mining.
docker compose -f compose.regtest.yml exec bitcoin bitcoin-cli \
  -conf=/etc/bitcoin.conf -datadir=/bitcoin getrawmempool
docker compose -f compose.regtest.yml exec bitcoin bitcoin-cli \
  -conf=/etc/bitcoin.conf -datadir=/bitcoin -rpcwallet=ots -generate 2

docker compose -f compose.regtest.yml run --rm settlement upgrade \
  /artifacts/manual/example_payload.bin.ots \
  --network regtest --calendar http://calendar:14788
docker compose -f compose.regtest.yml run --rm settlement verify \
  /app/examples/example_payload.bin /artifacts/manual/example_payload.bin.ots \
  --network regtest --bitcoin-conf /config/client.conf
```

The calendar polls Core asynchronously: repeat upgrade after a short wait if it
has not processed both blocks yet. Already-stamped files are reused; use a new
payload/output path to demonstrate a fresh pending state. Demo mining assumes
exclusive use of this development calendar; avoid concurrent manual submissions
while the automated lifecycle runs.

Run real local integration tests, inspect services, or export artifacts:

```bash
docker compose -f compose.regtest.yml run --rm integration
docker compose -f compose.regtest.yml logs --tail 100 bitcoin bootstrap calendar
docker compose -f compose.regtest.yml ps
docker compose -f compose.regtest.yml run --rm --entrypoint python settlement \
  -c 'from pathlib import Path; print("\n".join(str(p) for p in Path("/artifacts").glob("run-*/summary.json")))'
```

To copy demo artifacts out, create a stopped tools container, copy, then remove
only that container:

```bash
docker compose -f compose.regtest.yml create settlement
docker compose -f compose.regtest.yml cp settlement:/artifacts ./regtest-artifacts
docker compose -f compose.regtest.yml rm -f settlement
```

Calendar receipts contain `http://calendar:14788`, resolved inside Compose.
Upgrade them from the container environment. Verification needs no calendar,
but requires the matching local blockchain. Keep volumes together if you want
to retain and independently recheck old regtest proofs.

```bash
# Stop services and keep all data.
docker compose -f compose.regtest.yml down
# Explicit reset: deletes this environment's blockchain, calendar, and artifacts.
docker compose -f compose.regtest.yml --profile tools down --volumes
```

## Install

On Linux or another POSIX platform with Python 3.11+:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e '.[test]'
```

This installs the official `opentimestamps-client==0.8.0` and
`opentimestamps==0.5.0`. POSIX file locks protect cooperating writers. Use a
local filesystem supporting advisory locks, hard links, and atomic renames.

## Stamp, upgrade, verify

```bash
python -m settlement stamp examples/example_payload.bin --out ./proofs
python -m settlement upgrade ./proofs/example_payload.bin.ots
python -m settlement verify examples/example_payload.bin ./proofs/example_payload.bin.ots
```

The installed `upp-settlement` command accepts the same arguments. Each command
prints one JSON result. `--help` prints ordinary command help.

Stamp creates `proofs/example_payload.bin.ots` and a metadata snapshot
`proofs/example_payload.bin.ots.json`. The original file is never modified.
Only a randomized OTS commitment is sent to calendars, through the official
client; the payload is read locally through the client's stdin. SHA-256 is also
computed independently with `hashlib` and checked against the resulting proof.
Calendars learn the network address and request timing, but receive no original
file, filename, metadata receipt, or unrandomized payload digest.

Initial receipts are `PENDING`: calendars aggregate commitments before anchoring
them in Bitcoin, which can take hours. Run `upgrade` later; it never waits for a
Bitcoin confirmation. A calendar failure retains the exact original proof and
returns `PENDING` with diagnostics. A retrieved block reference still does not
prove Bitcoin inclusion: only `verify` may return `CONFIRMED`.

`verify` never contacts calendars, upgrades proofs, writes metadata, or trusts
the JSON snapshot. Run `upgrade` explicitly to obtain new attestations. Unknown
attestation types are retained but cannot establish Bitcoin confirmation.

Repeating stamp for the same filename and matching bytes reuses the existing
proof without resubmission. A conflicting proof or orphan metadata file is an
error and is not overwritten. Existing JSON is never trusted. The JSON file is
the initial submission snapshot; it is not refreshed by upgrade or verification.
Use the current command/API result for current status and proof digest.

Proof publication refuses overwrites. Upgrades are validated in memory and
written through a temporary file and atomic replacement. `.ots.lock` files are
intentional persistent lock files, not proofs; their presence does not mean an
operation is running. Concurrent cooperating writers fail with `proof_busy`.
If receipt writing fails after proof publication, the proof is retained; retry
stamp to recover the missing metadata. No automatic retries or background work
are scheduled. Keep immutable inputs and avoid external concurrent edits.

## Results and exit codes

Results contain `version`, `backend`, `network`, `payload_sha256`, `proof_file`,
`proof_sha256`, `binding_checked`, `status`, `bitcoin`, and a list of
`diagnostics` with stable `code` and human-readable `message` fields. Errors may
have null digest/path fields. `payload_sha256` from upgrade is extracted from the
proof and is **not** a digest checked against original bytes.

| Status | Meaning | CLI exit code |
|---|---|---|
| `PENDING` | Receipt exists; independent Bitcoin verification has not completed | 0 for stamp/upgrade; 2 for verify |
| `CONFIRMED` | Original bytes match and an active-chain Bitcoin attestation was verified | 0 |
| `INVALID` | Digest mismatch, malformed proof, or disproven Bitcoin attestation | 3 |
| `UNVERIFIABLE` | Required node/source unavailable, unsynchronized, or unsupported | 4 |
| `ERROR` | Invalid input path, conflicting output, submission failure, or local operational error | 1 |

Stamp/upgrade always return `PENDING` on success, even for a proof already
containing Bitcoin attestations. Calendar upgrade failures remain pending with
diagnostics; a failed initial submission produces no usable receipt and is
`ERROR` (or `UNVERIFIABLE` for a subprocess deadline). Successful process exit is
never used as evidence of cryptographic validity.

Verified `bitcoin.block_height` and `bitcoin.block_time` (Unix seconds, UTC) are
populated only by successful verification. Receipt metadata is not itself a
cryptographic proof. Keep the portable `.ots` file and original exact bytes.

## Bitcoin Core

Run your own fully synchronized Bitcoin Core **mainnet** node with local RPC:

```ini
# ~/.bitcoin/bitcoin.conf
server=1
# Optional; historical block headers remain available on a pruned node.
prune=1000
```

Keep RPC bound locally. Default configuration discovery and cookie authentication
come from `python-bitcoinlib`, the official OTS client's Bitcoin dependency.
It reads `~/.bitcoin/bitcoin.conf` on Linux (platform defaults on macOS), uses
`datadir/.cookie`, and supports configured `rpcuser`/`rpcpassword` as a fallback.
No credentials are placed in result JSON. For a nondefault configuration:

```bash
python -m settlement verify examples/example_payload.bin \
  ./proofs/example_payload.bin.ots --bitcoin-conf /path/to/bitcoin.conf --timeout 10
```

Use ordinary mainnet settings and an absolute `datadir` when needed. The
underlying config reader supports simple key/value settings; do not rely on
`includeconf`, section-specific settings, or a custom `rpccookiefile`. Use a
dedicated simple config with the correct datadir/RPC settings instead.

The verifier checks `getblockchaininfo` against the explicitly selected network, requests the active-chain block hash
at the attested height, fetches its header, calls the official OTS
`verify_against_blockheader`, and checks the active-chain hash again. A missing
node, wrong network, initial synchronization, unavailable block, RPC failure, or
observed reorganization cannot produce `CONFIRMED`. No third-party explorer is
used. Confirmation uses the node's current active chain without an additional
minimum-depth policy; future reorganizations can change verification results.

## Python integration

```python
from pathlib import Path
from settlement import stamp_file, upgrade_proof, verify_file, Status

# Another component has already generated and saved its signed manifest.
# This package does not create, parse, or authenticate that manifest.
manifest_path = Path("output/signed-manifest.bin")
receipt = stamp_file(manifest_path, Path("proofs"))
print(receipt.to_dict())

if receipt.proof_file and receipt.status == Status.PENDING:
    upgrade = upgrade_proof(receipt.proof_file)  # Call again later if needed.
    verification = verify_file(manifest_path, receipt.proof_file)
    if verification.status == Status.CONFIRMED:
        print(verification.bitcoin.block_height)
```

For explicit configuration:

```python
from settlement import OTSConfig, SettlementService

service = SettlementService(OTSConfig(
    timeout=10,
    bitcoin_conf="/absolute/path/bitcoin.conf",
    # calendars=("https://your-explicitly-trusted-calendar.example",),
))
result = service.verify_file("manifest.bin", "manifest.bin.ots")
```

For regtest inside the Docker environment:

```python
service = SettlementService(OTSConfig(
    network="regtest",
    bitcoin_conf="/config/client.conf",
    calendars=("http://calendar:14788",),
))
```

Regtest RPC defaults to port 18443 and reads `datadir/regtest/.cookie` without
changing global Bitcoin parameters. Its simple config reader supports global
and `[regtest]` settings, `rpcconnect`, `rpcport`, `datadir`, `rpccookiefile`,
and a username/password fallback. Prefer cookie authentication; credentials
that cannot be safely represented by the upstream URL API are rejected. Use
absolute filesystem paths and a standalone configuration without `includeconf`.
The selected network must match the actual node before confirmation is possible.

Calendar configuration controls stamp endpoints and explicitly authorizes
additional upgrade endpoints; the official default OTS calendar whitelist
remains allowed on mainnet only. On regtest there are no default calendars:
stamp/upgrade require explicit endpoints, and only those exact URIs may be
contacted. HTTP is accepted only for explicitly configured regtest endpoints.
Arbitrary proof-supplied URLs are skipped and diagnosed.
Timeouts must be 1–300 seconds. Stamp has an outer subprocess deadline of
`timeout + 10` seconds. Upgrade uses the official client's bounded calendar
request helper; its deadline applies to each request, so multiple calendars can
take longer overall. RPC timeouts apply per request. Explicitly authorize only
calendars you intend to contact. Diagnostics suppress raw upstream exceptions
and stderr to avoid credential leakage.

## Tests

The evidence-v3 crypto-module experiment is documented in
[the v3 runbook](docs/V3-EXPERIMENT-RUNBOOK.md), with its
[roadmap and remaining acceptance gates](docs/CRYPTO-V3-ROADMAP.md).
Use `python -m fingerprinto v3 --help` for the explicit profile; v1 and hybrid-v2
remain separate. Synthetic passing tests do not establish real-camera accuracy.

```bash
python -m pytest -q
# Optional: full local integration against the running Docker services.
docker compose -f compose.regtest.yml run --rm integration
# Optional: creates a real timestamp and contacts public calendars.
UPP_NETWORK_TEST=1 python -m pytest -q -m network
```

The default suite blocks network connections. It uses real OTS serialization,
executes the official stamping CLI with simulated calendar submissions, and
mocks Bitcoin RPC while exercising the official attestation verifier. It covers
digest binding, malformed proofs, pending and confirmed states, unavailable
infrastructure, retries, timeouts, output conflicts, and atomic-write failures.
No real Bitcoin confirmation is required. A mocked node test is not evidence
that a proof has been independently verified on the real blockchain.
The `regtest` integration marker is skipped unless `UPP_REGTEST_TEST=1`; Compose
sets it only for the dedicated integration service. This test uses real RPC and
the official local calendar, with no fake attestations or mocked infrastructure.

## Guarantees and limitations

Successful verification provides Bitcoin-backed evidence that these exact bytes
existed by the attesting block's blockchain time, subject to SHA-256 security
and the validity of the chain checked by your trusted local node. Block time is
blockchain evidence, not a precise wall-clock measurement or photograph capture
time. This proves neither authorship nor content truth, signature validity,
device provenance, camera authenticity, or photo authenticity.

The payload must be finalized before timestamping. Never embed the resulting
proof into the same payload: that changes its digest and creates a circular
dependency. Payload changes require a separate timestamp. No image contents,
private keys, GPS coordinates, or device identifiers are added to receipts.

Official references: [OpenTimestamps](https://opentimestamps.org/),
[client](https://github.com/opentimestamps/opentimestamps-client),
[Python library](https://github.com/opentimestamps/python-opentimestamps),
[calendar server](https://github.com/opentimestamps/opentimestamps-server/tree/1e955ea3b036453bf795505d923f2643f773810c).
