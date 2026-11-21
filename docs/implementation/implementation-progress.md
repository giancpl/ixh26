# FingerprinTO implementation progress

**Latest audit:** [CRYPTO-MODULE-AUDIT.md](CRYPTO-MODULE-AUDIT.md) distinguishes
implemented primitives, source corrections awaiting the test branch, missing
manufacturer/Android integration, and empirical acceptance gates. The module
must not be described as entirely implemented. Regulatory notes are in
[../crypto/regulations.md](../crypto/regulations.md).

**Current v3 work:** [CRYPTO-V3-ROADMAP.md](CRYPTO-V3-ROADMAP.md) tracks the
manufacturer/PRNU direction. Implemented pieces now include the shared reducer,
v3 receipt/attachment codecs, experimental PRNU numeric core, reference records,
authority-signed enrollment state, enrollment possession proof and offline
sign-once issuance/independent verification. The CLI/runbook make the experiment
executable. Real-device calibration and a real manufacturer validator are still
open; see [V3-EXPERIMENT-RUNBOOK.md](V3-EXPERIMENT-RUNBOOK.md).

**Save point before redesign:** start with
[CRYPTO-CHECKPOINT-2026-11-21.md](CRYPTO-CHECKPOINT-2026-11-21.md) for the current
module map, outstanding work, trust boundaries and deferred validation.
The numbered sections below are historical snapshots, not cumulative claims
that every later requirement has already been satisfied. Read the user's new
direction before continuing the old backlog.

At save commit `db5a857` (`ADD: partial 1`), `docs/crypto/crypto.md` already introduces
the **v3 P-256 manufacturer/PRNU proposal** and moves v1 into a historical
appendix. At that save point the implementation covered v1/v2 only: the v3
codec, reducer, PRNU reference/scoring/calibration and attachments were missing.
The current roadmap above supersedes that historical backlog as stages finish.
The prior v1/v2 test counts are not validation of v3.

## Piece 1: deterministic encoding and commitments

Implemented in `fingerprinto/`, independently of Android and the existing OTS
flows. `Receipt.from_bytes` checks canonical CBOR, schema, field types and size
limits. `commitments` reconstructs all hashes through C and SHA256(C).

Run the encoding-only tests without optional dependencies from the repository root:

```sh
python -m unittest discover -s tests/fingerprinto -p test_core.py -v
```

The four fixtures in `tests/fingerprinto/fixtures/v1.json` are copied from the
synthetic vectors in `docs/crypto/crypto.md`. They are interoperability fixtures, not
accepted camera or enrollment evidence.

This piece does **not** validate SPKI curve points, certificate DER/chains,
ECDSA signatures, camera credentials, enrollment or ledger inclusion. It does
not issue receipts, change capture/upload behaviour, or return a trusted
verification status. The asset API currently takes bytes in memory; streaming
and whole-bundle import limits belong to the integration layer.

## Piece 2: independent installation-signature verification

Implemented in `fingerprinto/verification.py`, using `cryptography` for P-256
point validation and ECDSA. Install the optional dependency with
`python -m pip install -e '.[fingerprinto]'`. The encoding core remains usable
without this dependency. The test extra also includes the verifier dependency.

```python
from fingerprinto.verification import verify_receipt

# Original binary bytes, not JSON metadata or a caller's verification boolean.
result = verify_receipt(receipt_bytes, original_asset_bytes, detached_credential_bytes)
print(result.to_dict())
```

Use `None` for the credential in embedded mode. Missing original evidence yields
an indeterminate signature result. The verifier reconstructs R, enforces the
exact v1 SPKI representation, validates the P-256 point, checks strict ECDSA
DER/scalar bounds and verifies SHA256withECDSA over R. It does not normalize s
or retry with an old protocol domain. Certificate envelopes and X.509 parsing
are checked independently; a malformed certificate does not prevent reporting
an otherwise valid installation signature.

The result exposes separate profile, origin, installation signature, enrollment
and anchor checks, stable reason codes, evaluation time and reconstructed hashes.
`profile=accepted` describes the implemented encoding checks only. It is not an
overall trust decision. No overall accepted/verified flag is returned.

Origin and enrollment remain indeterminate without the future validators and
verifier-owned policy. Certificate parsing does **not** validate signatures in
the certificate chain, trust roots, revocation, Android attestation, application
identity, or enrollment challenge freshness. An anchor is absent when no evidence
is supplied and indeterminate when opaque evidence is supplied; neither outcome
asserts whether an anchor exists elsewhere. No network requests are made.

Run both pieces' tests in an environment with the optional dependency:

```sh
python -m unittest discover -s tests/fingerprinto -v
```

Validated on Windows / Python 3.11 with cryptography 46.0.7: all 12 test methods
pass, including all four fixture signatures, modified assets/fields, malformed
keys/signatures/certificates, old-domain rejection, JCA-compatible hashing and
distinct commitments for the two valid ECDSA s representations. These are
offline cryptographic checks, not Android/device or ledger integration tests.

## Piece 3: Bitcoin/OTS receipt transport

Implemented in `fingerprinto/anchoring.py`. This module uses the existing POSIX
settlement backend and its pinned OTS dependencies; use Linux/WSL or Docker.
The encoding and signature-only modules remain usable on Windows.

```python
from fingerprinto.anchoring import stamp_receipt, verify_receipt_ots
from settlement import OTSConfig, SettlementService

config = OTSConfig(network="mainnet", bitcoin_conf="/absolute/path/bitcoin.conf")
# Explicit network action. Inputs are an already-signed receipt and original bytes.
submission = stamp_receipt(receipt_bytes, asset_bytes, "receipt-anchors",
                           credential_bytes_or_none, config=config)
# Inspect submission.settlement.status and diagnostics before continuing.
proof_path = submission.settlement.proof_file

# Later, explicitly retrieve available attestations. Upgrade never confirms.
if proof_path:
    upgrade = SettlementService(config).upgrade_proof(proof_path)
    # Inspect upgrade.status and diagnostics. Verification is a separate action.
    from pathlib import Path
    result = verify_receipt_ots(receipt_bytes, asset_bytes, credential_bytes_or_none,
                                proof=Path(proof_path).read_bytes(), config=config)
    print(result.to_dict())
```

`export_commitment` atomically publishes exactly C's 32 raw bytes at
`<output_dir>/<C hex>/commitment.bin`, refusing conflicting contents. Repeated
exports reuse identical content. `stamp_receipt` requires a well-formed receipt
and valid installation signature, then reuses settlement's locking, no-overwrite
proof publication and idempotent submission. It does not create a signature or
assert accepted origin/enrollment. Call it only as an explicit network action.

`verify_receipt_ots` reconstructs C from the receipt and original evidence,
requires SHA-256 and a proof digest equal to SHA256(C), and calls the existing
Bitcoin header verifier. It rejects photo-only proofs, hex-text/newline variants,
and treating C itself as the file digest. No calendars are contacted and no
files are written by verification. Configuration supplies the expected network;
it is never inferred from receipt metadata. Proofs are limited to 16 MiB.

Anchor outcomes are absent, pending, verified, failed or indeterminate. Results
include backend/network, and verified block height/time when available. Regtest
retains the `regtest_only` diagnostic. A verified anchor can coexist with an
invalid signature: inclusion and signature authenticity are independent checks.
This backend inherits settlement's active-chain semantics without a minimum
confirmation-depth policy. Future reorganizations can change the result.

Validated in WSL / Python 3.12: **75 tests and 62 subtests passed**, covering the
new modules and existing settlement unit tests. Calendar submissions use the
real OTS CLI with a simulated calendar; Bitcoin RPC is simulated while the
official attestation verifier runs. No real-chain confirmation, public calendar
submission or regtest Docker lifecycle was performed.

```sh
python -m pytest -q tests/fingerprinto tests/test_receipt_anchoring.py \
  tests/test_settlement.py tests/test_verification.py tests/test_upgrade.py \
  tests/test_regtest.py tests/test_models.py
```

Keep receipt.cbor, original asset and any detached credential: commitment.bin
and its OTS proof alone are not a complete evidence bundle. This piece does not
implement a bundle archive/importer or change the app's existing photo uploads.

## Piece 4: evidence-bundle export/import

Implemented in `fingerprinto/bundle.py`. This is a local ZIP transport convention
for v1 evidence; it does not change the signed CBOR schema in `../crypto/crypto.md`.

```python
from fingerprinto.bundle import EvidenceBundle, export_bundle, import_bundle, verify_bundle

bundle = EvidenceBundle(receipt_bytes, asset_bytes, credential_bytes_or_none,
                        ots_proof=ots_bytes_or_none,
                        anchor_locator=None)
export_bundle(bundle, "evidence.zip")  # Parent must exist; refuses any overwrite.
received = import_bundle("evidence.zip")
result = verify_bundle(received)       # Offline; verifies the installation signature.
print(result.to_dict())
```

The fixed layout contains `receipt.cbor`, `asset.bin`, reconstructed
`commitment.bin`, optional `credential.bin`, optional `commitment.bin.ots` and
optional `anchor.json`. Evidence bytes and signatures are preserved verbatim.
Embedded mode forbids a detached credential; detached mode requires one.
Import checks the bundled C against the original evidence. Import/export checks
schema and transport consistency, not trust: invalid signatures can be carried
for independent diagnosis. Call verification to determine signature validity.

The optional locator is UTF-8 JSON with `backend` and `network`, plus a 64-hex
`transaction_hash` for `backend: "xrpl"`. Bitcoin locators use
`backend: "opentimestamps-bitcoin"` and mainnet/regtest without a transaction hash.
Duplicate/unknown fields, URLs and incompatible proof/backend combinations are
rejected. Locator hints are unauthenticated. A locator without a proof yields
an indeterminate anchor outcome, not proof of absence.

For explicit Bitcoin verification on POSIX:

```python
from fingerprinto.bundle import verify_bundle_ots
from settlement import OTSConfig

# Select the expected network independently of anchor.json.
result = verify_bundle_ots(received, config=OTSConfig(network="mainnet"))
```

ZIP import never extracts paths. It accepts only the fixed names, rejects links,
duplicates, encryption and unsupported compression, checks CRCs, and enforces
per-member and 160 MiB aggregate uncompressed limits. Compressed archive input
is capped at 161 MiB. Reads are bounded during decompression. The original
asset is streamed from ZIP into bounded memory; the returned API keeps binary
evidence in memory, so callers should process large bundles in a worker.
Export uses temporary storage and atomic no-overwrite publication.

## Audit of pieces 1–4

Source files and fixtures are present on `leomain`. Windows signature/core tests
were rerun (22 tests passed), and the POSIX suite was rerun in WSL (87 tests and
67 subtests passed) against real OTS serialization,
the official stamping CLI and official attestation verifier. Calendar/RPC
responses are simulated. Tests include all four manual vectors, malformed DER,
old-domain rejection, file tampering, stamp/upgrade/verify, bundle round trips,
malicious ZIP members, bounded decompression, CRC corruption, atomic publication
failure and verifier-owned network selection.

At the pieces 1–4 audit, code and documentation were still local/uncommitted;
later commits and the redesign checkpoint supersede that Git-state note.
Passing these tests does not
establish camera-route feasibility, Android enrollment, origin acceptance or
real-chain inclusion. The HTTP server/web/Android suites have not been run in
this audit; their application flows are not wired to these new modules yet.

## Piece 5: executable bundle CLI

Run `python -m fingerprinto --help` from the checkout. Installing
`python -m pip install -e '.[fingerprinto]'` also provides the `fingerprinto`
command. Pack and inspect require no cryptographic trust decision. Offline
verification requires the cryptography extra; Bitcoin verification uses the
POSIX settlement environment.

```sh
python -m fingerprinto pack --receipt receipt.cbor --asset original.jpg --out evidence.zip
# Add --credential credential.bin for detached credentials.
# Optionally add --ots-proof commitment.bin.ots and --anchor-locator anchor.json.
python -m fingerprinto inspect evidence.zip
python -m fingerprinto verify evidence.zip
python -m fingerprinto verify-ots evidence.zip --network mainnet --bitcoin-conf /absolute/path/bitcoin.conf
```

Every command prints one JSON result, except `--help`. Input files are size
bounded before and during reading. Output bundles refuse overwrites. Commands
do not generate installation keys, sign new receipts, or submit to calendars.
`verify-ots` only performs the explicit Bitcoin verification and requires
`--network`; it never infers that choice from the bundle's locator.

Exit codes: 0 for successful pack/inspect, 1 for usage, input, dependency or I/O
errors; verification uses 3 for any rejected/invalid/failed check, 2 for any
indeterminate/absent/pending check, and 0 only when all checks are conclusive
and successful. Thus a valid installation signature or even a verified Bitcoin
anchor still returns 2 while origin/enrollment are unimplemented. Pack/inspect
JSON explicitly reports `trust_evaluated: false`.

This completes an executable path from existing evidence files to a portable
bundle and independent checks. Production receipt issuance and the application's
capture/upload integration remain separate outstanding steps.

Validation after this piece: 27 tests passed on Windows; 93 tests and 67
subtests passed in WSL, including existing settlement tests. CLI pack/inspect/
verify run in fresh Python subprocesses on all four fixtures. Bitcoin CLI
verification uses simulated RPC with the official attestation verifier. No real
calendar submission, hardware test or blockchain confirmation was performed.

## Piece 6: Android capture diagnostic export

The CameraX completion path now records capture diagnostics and provides
**Esporta originale e rapporto** using Android's document picker. The Java ZIP
writer preserves the completed file bytes and rechecks SHA-256/size during
copying. Device/app/capture-route observations are sampled and saved with the
private original. Unique filenames and an in-progress capture guard prevent
collisions between rapid captures. Hashing/report creation/export use IO work.

See [capture-feasibility.md](capture-feasibility.md) for the exact experimental
procedure and remaining host validation. This diagnostic ZIP is not a signed
receipt bundle and cannot pass as an accepted origin claim.

Java writer tests were compiled and executed successfully on a standalone JVM.
Android SDK/Gradle and a supported handset are unavailable in this environment;
the Android UI integration is source-reviewed but not compiled/device-tested.
No real credential was obtained or validated. Existing Python parts are unchanged.

## Piece 7: independent capture diagnostic check

`python -m fingerprinto check-capture capture.zip` now checks the diagnostic
export described in [capture-feasibility.md](capture-feasibility.md). It streams
and hashes the original JPEG without extracting it, validates the unsigned
report schema and archive limits, and reports matching/mismatched size/hash.
It never reports accepted origin or authenticated device/app metadata.

Validation: 32 Python tests passed on Windows. The Java writer was compiled and
executed in WSL and its ZIP was checked by the actual Python CLI in a reproducible
interop test. CRC/structure limits, modified hashes/sizes, invalid JSON/types,
links/unknown members and incomplete-vs-invalid exit codes are covered. This is
transport validation with synthetic evidence; Android build/device validation
and real credential validation remain outstanding.

## Piece 8: reproducible Android build configuration

Added Gradle Wrapper 8.11.1 with the official distribution SHA-256, Windows and
POSIX launchers, and [Android build instructions](../../android/README.md). The
existing standalone export test is now included in `testDebugUnitTest` through
a JUnit adapter. Downloaded local JDK/Gradle/command-line tools live in the
ignored `.tools-android/` directory; no global Java/Gradle installation changed.

Wrapper generation completed successfully. The Android app build did not
complete: SDK package installation required acceptance of the Google license,
and the user explicitly chose to prepare configuration only. No license was
accepted; the attempted build was interrupted. The Gradle JUnit task remains
unrun. The 32 Python tests were rerun successfully. Android capture UI,
permissions, document picker and camera output still require build/device tests.

## Piece 9: Android-compatible receipt construction

Added `ReceiptV1.java`: deterministic CBOR encoding, bounded streaming evidence
hashing, the exact tree through R, receipt export with a supplied unchanged DER
signature, C and SHA256 helpers. Constructor input arrays and certificate arrays
are copied to prevent later caller mutation. UTF-8 encoding rejects unpaired
surrogates and enforces encoded-byte limits. Signed 64-bit times include the
minimum negative value. No new signature, origin or enrollment decision is made.

Validated using the local JDK 17 compiler and a standalone JVM, without the
Android SDK or accepting its license. All intermediates, receipt bytes and C
match the four manual fixtures. Four JCA signatures verify over R and fail over
bare T. Eight additional Python-generated integer/Unicode boundary cases pass;
tests also cover mutated caller arrays, altered asset roots, credential placement,
empty credentials, source I/O failure and invalid Unicode/text sizes.
Reproduce from the repository root with
`bash android/tests/run-receipt-vectors.sh` (JDK 17+ and Python 3.11+ required).

This completes the Android-side byte construction library. Keystore generation,
attested enrollment, signing and CameraX integration are still outstanding;
the full Android application remains unbuilt pending the SDK setup decision.

## Piece 10: installation key and signing module

Added `InstallationKeys.kt` with stable P-256 AndroidKeyStore provisioning,
external enrollment challenge, explicit StrongBox requirement, serialized
provisioning, atomic local SPKI/challenge records excluded from backup, evidence
retrieval and signRoot guarded by the expected installation identifier. Existing
aliases, incomplete state and key mismatches fail instead of replacing identity.
No local state flag is treated as enrollment or camera-origin acceptance.

Added `InstallationSignature.java`: exact SPKI/P-256 validation, strict DER
and scalar validation, JCA SHA256withECDSA signing and immediate verification.
It preserves signature bytes without s normalization. JVM tests cover genuine
signatures, wrong root/key, invalid points, malformed DER and root-size errors.
A fresh Java-produced signature was independently verified by the Python module.
The standalone tests have a Gradle JUnit adapter for later use on the build
branch. No Android SDK installation, Android app build or hardware test occurred.
The Android-specific adapter is source-reviewed only, including atomic storage
and process/file locking; its device behavior remains to be tested.

The API is a primitive, not production receipt issuance: origin validation and
independent enrollment acceptance must precede signing; the caller must persist
the exact signature/receipt once and reuse it for retries. Full signed-record
persistence and interrupted provisioning recovery remain outstanding.

## Piece 11: independent enrollment verifier

Added a bounded Android attestation DER reader, restricted pinned-chain and
hardware/application policy verification, verifier-owned revocation snapshot
checks, persistent policy-scoped challenge issuance and atomic one-use
consumption. `enroll_receipt` checks receipt signature possession before intake;
it returns separate enrollment/origin/signature outcomes and policy diagnostics.
No phone-supplied trust flags, roots, time or revocation metadata are used.

See [../crypto/enrollment-verifier.md](../crypto/enrollment-verifier.md) for inputs, API, restricted
X.509/Android profiles and remaining deployment responsibilities. In particular,
authenticated production revocation acquisition, real trust configuration and
durable/historical enrollment-decision recovery are not supplied by this piece.
No production Android hardware compatibility is claimed from synthetic tests.

Validation: 44 Python tests passed, including synthetic attestation acceptance,
bad signatures, wrong root/key/app/hardware/boot state, stale/missing revocation,
unknown profiles, malformed DER, appended attacker certificates, policy scope,
expiry, replay, concurrent consumption, issuer RSA/EC, StrongBox and receipt
proof of possession. No SDK or app build was used; XRPL remains out of scope.

## Piece 12: experimental hybrid suite and hardware wrapping

Implemented v2 ML-DSA-65 AND Ed25519 primitives/receipt construction in Python
and Java with distinct profiles, domains and strict lengths. The verifier pins
an enrolled hybrid device ID and requires both signatures. The hardware P-256
key binds the complete hybrid public-key descriptor. The new Python/Java receipt
wire bytes, root and commitment agree; v1/v2 decoding does not fall back.

After explicit user agreement, added Android hardware AES-GCM wrapping of the
software hybrid keys, with atomic encrypted storage, hardware KeyInfo checks,
identity/binding checks and temporary buffer clearing. This is protection at
rest; the hybrid keys enter app RAM for signing and are not native hardware PQ
keys. Software-only P-256 installation keys are now rejected locally. The
enrollment verifier was restricted to EC issuer chains to follow the no-RSA
instruction; policy digests include that restriction.

Added Python/Android sign-once journals to reuse exact receipt/signature bytes
on retry and reject operation/input conflicts. Python journal tests cover
concurrency, restart reuse, failure rollback and accidental corruption. Android
adapters are source-reviewed only; no SDK/license operation or app build was
performed. JVM tests use software fixture keys and do not prove hardware backing.

Validation: 51 Python unittest methods passed on Windows; 117 tests and 90
subtests passed in WSL, including existing OTS checks. Real ML-DSA/Ed25519
signatures cross-verify in both directions using BC 1.87 and PQClean/pqcrypto
0.4.0. JVM tests cover the AES envelope, binding, v2 receipt bytes and C.
See [../crypto/hybrid-v2.md](../crypto/hybrid-v2.md) for exact byte contracts and limitations.

## Piece 13: explicit hybrid bundle/CLI/OTS transport

Connected the v2 profile through explicitly selected bundle import/export,
offline verification, submission and Bitcoin verification. Commands are
`pack-hybrid`, `inspect-hybrid`, `verify-hybrid`, `stamp-hybrid` and
`verify-hybrid-ots`; expected device ID is required for verification/submission,
and Bitcoin commands require an explicit network. Existing commands remain v1;
there is no automatic fallback or trust in a bundle-supplied identity/network.

Both signature checks precede submission. OTS binds SHA256(C), and retrieved
Bitcoin attestations are checked through the existing verifier. Results retain
independent signature/origin/enrollment/anchor outcomes. A confirmed anchor does
not repair invalid signatures or mark origin/enrollment accepted. Explicit ZIP
profile selection reuses the same path, size, CRC and no-overwrite controls.

The v2 receipt can reconstruct C for diagnostic transport even when a component
signature fails. Hardware-binding errors fail v2 decoding. A pinned device ID
alone still does not constitute a validated retained enrollment decision.
See [../crypto/hybrid-v2.md](../crypto/hybrid-v2.md) for commands, APIs and trust boundaries.

Validation: 56 Windows unittest methods passed; 127 tests and 90 subtests passed
in WSL. New coverage includes embedded/detached bundles, profile/identity
requirements, invalid component transport, malformed binding, CLI submission
and reuse, confirmed proofs with unresolved trust, wrong proof contract, wrong
network and refusal to submit invalid signatures. Official OTS serialization
and stamping client are used with simulated calendars/RPC. No real calendar,
Bitcoin confirmation, Android build or hardware test occurred.

## Next pieces (crypto-module scope)

1. Establish the real camera-credential route on target hardware, as required
   by the manual, before enabling production receipt issuance.
2. Origin-credential validator and policy; production Android trust configuration
   and authenticated revocation-status acquisition for the implemented enrollment
   verifier. Validate its restricted path profile against actual target hardware.
3. Durable, authenticated enrollment-decision retention/recovery, including the
   accepted hybrid device ID/suite. The intake verifier, challenge store and
   local sign-once journal exist, but historical receipt verification must bind
   to that retained decision. Explicit v2 bundle/CLI/OTS transport is now wired.
4. Module-level integration and tests with real evidence. Application compilation
   and UI/server integration are deferred to another branch by user request;
   XRPL is excluded. Existing Bitcoin/OTS module transport remains available.

Hardware attestation and origin acceptance require deployment trust policy and
real samples; synthetic fixtures cannot establish either.
