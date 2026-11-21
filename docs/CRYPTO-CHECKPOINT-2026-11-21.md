# Crypto module checkpoint before redesign — 2026-11-21

This is a restore/handover point, **not a production-readiness declaration**.
The user requested this checkpoint before a substantial change of direction.
Do not automatically continue the old backlog: read the next user instructions
and decide which assumptions/modules survive the redesign first.

Branch: `leomain`. Parent before this checkpoint: `7c3ea88` (`ADD: verifier
module`). The commit containing this file also saves the pending hybrid v2,
hardware-wrapping, journal and transport implementation. Locate its exact ID:

```sh
git log --oneline -- docs/CRYPTO-CHECKPOINT-2026-11-21.md
```

## Scope and instructions that were in force at the save point

- This branch implements the **crypto module**, not the complete product.
- XRPL is excluded. Bitcoin/OpenTimestamps remains the anchor backend.
- Android app builds, SDK license acceptance, UI/server integration and device
  validation are deferred to another branch. Do not restart the SDK installation
  merely because local tooling is available. The user declined license acceptance.
- No RSA device keys; the enrollment verifier also rejects unsupported RSA
  issuer paths before verifying signatures. Deployment needs trusted EC chains.
- The user explicitly approved **P-256 hardware identity + AES hardware wrapping
  of software ML-DSA-65/Ed25519 keys**, acknowledging plaintext keys in app RAM
  during signing. Native hardware ML-DSA/Ed25519 was never established.
- Preserve this checkpoint before changing formats, algorithms or trust model.
  The upcoming direction has not yet been specified in this document.

## Read these files first

| File | Role |
|---|---|
| `docs/crypto.md` | Original proposed **v1 P-256** protocol; not the hybrid specification |
| `docs/hybrid-v2.md` | Experimental v2 suite, byte contracts, wrapping boundary, APIs/CLI |
| `docs/enrollment-verifier.md` | Restricted attestation intake, caller-owned trust/status inputs and limits |
| `docs/capture-feasibility.md` | Real-capture experiment and unsigned diagnostic ZIP |
| `docs/implementation-progress.md` | Historical step-by-step implementation and test record |
| `android/README.md` | Java/Kotlin module map and deferred build/device status |

## Implemented code and its actual limits

| Area | Entry points | State at checkpoint |
|---|---|---|
| v1 encoding/commitments | `fingerprinto/cbor.py`, `core.py`; `ReceiptV1.java` | Canonical CBOR, field/size rules, SHA-256 tree and portable receipt construction; shared vectors pass |
| v1 signature checks | `fingerprinto/verification.py`; `InstallationSignature.java` | P-256 SPKI/point, strict DER/scalars, SHA256withECDSA over raw R; no implicit trust from signature validity |
| Hardware installation key | `InstallationKeys.kt` | Stable Keystore alias, external challenge, hardware KeyInfo guard, no-backup atomic record, locks, expected-ID checks; Android adapter is source-reviewed only |
| Enrollment intake | `android_attestation.py`, `enrollment.py`, `enrollment_requests.py` | Restricted pinned EC path, Android ASN.1, hardware/app/boot policy, supplied revocation snapshot, atomic scoped one-use challenge consumption; synthetic tests only |
| Hybrid signatures | `hybrid.py`; `HybridSoftwareKeys.java` | ML-DSA-65 **AND** Ed25519 on a versioned/key-bound transcript; both required, no downgrade; Java/Python real signatures interoperate |
| v2 receipts | `hybrid_receipt.py`; `HybridReceiptV2.java` | Distinct v2 root/CBOR/C, hardware-binding signature, supplied trusted expected hybrid ID; Java/Python roots, bytes and C agree |
| Hardware wrapping | `HybridKeyEnvelope.java`, `HardwareHybridVault.kt` | AES-GCM envelope, expected IDs and binding in AAD, bounded encrypted state; Java envelope tested with software fixture AES, actual Android hardware wrapping untested |
| Sign-once persistence | `receipt_journal.py`, `SignedReceiptJournal.kt` | Exact returned receipt bytes reused by operation/context digest; Python transaction/restart/concurrency tests pass; Kotlin adapter untested on Android |
| Bundle/CLI/OTS | `bundle.py`, `cli.py`, `anchoring.py` | Explicit v1/v2 transport, no-overwrite export, bounded import, required hybrid ID, SHA256(C) proof binding; real OTS client/serialization with mocked infrastructure |
| Capture diagnostic | `CaptureEvidence.java`, `CaptureReport.kt`, `capture.py`, CameraX callback | Original-byte diagnostic export and host hash/size checks; **unsigned**, not a credential or receipt; UI unbuilt/unexercised |

The existing `server/`, `web/` and ordinary photo upload do not automatically
use this full crypto lifecycle. A photo-only OTS proof cannot prove inclusion
of the new signed-receipt commitment.

## Invariants that must not be silently lost in a redesign

1. Origin, signature, enrollment and anchor are different evidence/decisions.
   A signature or Bitcoin inclusion cannot manufacture camera provenance.
2. Hardware P-256/AES keys are intended to remain non-exportable. Hybrid signing
   keys are software material, encrypted at rest and present in RAM for signing.
   Hardware attestation/binding does not remotely prove their AES wrapping.
3. v1/v2 versions, suite identifiers, domains, key encodings and transcripts are
   exact. Changing any needs a deliberate version/migration decision; no fallback
   to one signature, bare T, a legacy suite or an untrusted key identity.
4. `expected_device_id` comes from a trusted enrollment decision/registry.
   `inspect-hybrid` shows a **declared** ID, not an authoritative registration.
5. Network/root/revocation policy/evaluation time are verifier-owned. Neither
   a receipt flag nor `anchor.json` is trust configuration.
6. Finalized original bytes and detached evidence must be preserved. Changes
   need a new receipt. No credential validation is inferred from hashing JPEGs.
7. Persist the exact signature/receipt before any external submission. Signing
   again can change C. Journal producers must not submit or perform external
   side effects while the local persistence transaction is pending.
8. `commitment.bin` contains exactly 32 raw bytes of C. The OTS starting digest
   is **SHA256(C)**, not C, hex text, a newline or the photo hash.
9. Missing evidence/policy is indeterminate; no synthetic fixture or mocked
   node is evidence of real hardware provenance or blockchain confirmation.

## Work still missing, with concrete completion criteria

### A. Decide the new direction before extending current assumptions

Resolve with the user's next instructions: which signature suite survives,
whether hardware identity/wrapping remains, which provenance claim is intended,
and whether v1/v2 formats remain compatible/readable. Then update the relevant
specification and expected-profile policy before changing code. This checkpoint
does not pre-approve any replacement design.

### B. Origin validation and policy — not implemented

- Select an actual C2PA/vendor validator and supported issuer/capture profiles.
- Define trusted issuers and the precise signed assertion that means original
  capture. A valid manifest signature or genuine photo ingredient alone is
  insufficient. No issuer allowlist/positive accepted sample is supplied.
- Validate signature, active manifest, asset hard binding and capture semantics
  independently on the exact original J and detached A, if applicable.
- Return accepted/rejected/indeterminate with reason codes and policy/version.
- Required evidence: a positive sample produced by the actual app route and
  negative cases for authenticated-byte tampering, edited provenance, absent
  credential and untrusted issuer. Native-camera success is only a control.
- If the new direction changes the origin requirement, revise the claim and
  issuance gate explicitly instead of silently treating absence as acceptance.

### C. Complete enrollment-decision retention and later verification

- `EnrollmentRequests` stores challenge, policy digest, issuance/expiry,
  consumption time and **hardware** installation ID. It does not retain the
  complete decision, hybrid ID/suite, chain, policy and status evidence.
- `enroll_receipt` is **v1 fresh intake**, not a hybrid enrollment orchestrator.
  The attestation checker verifies the hardware key; v2 verification checks the
  hybrid binding/signatures separately. Joining them into one retained accepted
  enrollment is still missing.
- Atomically retain an accepted decision bound to hardware SPKI/ID, exact hybrid
  descriptor/ID/suite, policy, evidence digests and evaluation time. Handle a crash
  after consuming the challenge but before returning/persisting the full report.
- Add later receipt verification against that trusted record. Never repeat fresh
  intake on an already-consumed challenge. Define current versus historical
  assessment, revocation/expiry/revocation-of-enrollment and rotation semantics.
- If decisions must be portable, define their authentication and trusted-time
  evidence. Plain result JSON or a phone-supplied DB is not such evidence.
- Current CLI/API deliberately leave enrollment indeterminate even for a valid
  expected hybrid ID. Completion needs tests proving wrong-suite/key/record,
  expired/revoked enrollment, replay and corrupted/restored state cannot pass.

### D. Production trust/status acquisition — caller supplied, not implemented

- Maintain trusted EC Android roots independently and test the restricted path
  profile against the intended handset's actual chain.
- Fetch/authenticate Android revocation status through a verifier-owned channel;
  bound parsing, include suspended serials, define freshness/cache/failure policy
  and archive exact source evidence. `RevocationSnapshot` currently assumes the
  trusted caller has already done this; it authenticates no arbitrary JSON.
- Define minimum release/OS/patch/boot-key policy, allowed shared-UID package set,
  signer rotation and policy transitions. These defaults are not a deployment.

### E. Crypto lifecycle/orchestration and recovery — primitives exist

- Connect immutable original/metadata snapshots, origin checks, accepted
  enrollment, signing, journal storage and anchor queue through one explicit
  state machine. Current low-level signing primitives do not enforce the whole
  issuance policy. Do not wire them directly to a UI boolean.
- Derive/persist the operation ID and context digest consistently, including suite,
  key identity, input/evidence bytes and metadata. The journal currently relies
  on the caller to bind all inputs correctly.
- Persist/reconcile anchor attempts and uncertain submissions against the saved
  receipt. Keep signed, submission pending/failed and independently verified
  states distinct. No background retry scheduler/state machine exists.
- Handle interrupted Keystore provisioning, orphan aliases/records, key loss,
  uninstall/restore, rotation and old-receipt retention explicitly. Current code
  fails safely on incomplete/mismatched state; it provides no recovery UI/service.
- Store/export real enrollment evidence and signed bundles consistently. The
  diagnostic capture sidecar/current-session export is not this lifecycle.

### F. Validation deferred/outside the branch's current implementation scope

- Compile/apply Android API/lint checks and run real device tests on the other
  branch. Kotlin adapters, provider packaging, Keystore/AtomicFile/file-lock
  behavior, lifecycle cancellation and key invalidation have not been exercised.
- Check Bouncy Castle packaging/provider/API compatibility and provisioning on
  the supported Android versions, without replacing AndroidKeyStore.
- Obtain hardware-backed attestation and an accepted real capture sample, then
  verify independently. A fixture P-256 key or fixture AES key is software.
- Validate a real Bitcoin/regtest lifecycle separately if required. Current
  unit/integration tests simulate calendars/RPC. Minimum confirmation depth is
  not implemented; OTS verification follows the node's current active chain.
- Security review of the custom hybrid profile, bounded DER reader, restricted
  path validation, state recovery and dependencies remains necessary before
  deployment. Do not describe component-standard algorithms as an audited or
  standardized complete hybrid protocol.

## Verification evidence available at this checkpoint

Latest implementation runs, **not new device/production checks**:

| Check | Recorded result | Meaning |
|---|---|---|
| Windows Python unittest discovery | 56 methods passed | Core, signatures, synthetic enrollment, journals, bundles/CLI and hybrid transport |
| WSL selected pytest suite | 127 tests, 90 subtests passed | Same module plus existing settlement tests and real OTS client/serialization with simulated calendars/RPC |
| Standalone JVM tests | Passed | v1 vectors, exact-root JCA signing, Java capture export, hybrid signatures, AES envelope, v2 receipt construction |
| Java ↔ Python hybrid interoperability | Passed | Both signature components; Java v2 root, wire bytes and C match Python |
| Android app/device/SDK tests | Not completed | User deferred app builds and declined SDK license acceptance |
| Real origin credential / real blockchain confirmation | Not performed | No positive physical-device/provenance claim follows from current tests |

Reproduce without building the app:

```sh
python -m pip install -e '.[test]'
python -m unittest discover -s tests/fingerprinto -v
# POSIX Python environment for settlement's fcntl/Bitcoin dependencies:
python -m pytest -q tests/fingerprinto tests/test_hybrid_anchoring.py \
  tests/test_receipt_anchoring.py tests/test_settlement.py \
  tests/test_verification.py tests/test_upgrade.py tests/test_regtest.py tests/test_models.py
# JDK 17+, Python hybrid extra, verified external BC JAR:
bash android/tests/run-receipt-vectors.sh
bash android/tests/run-installation-signatures.sh
bash android/tests/run-hybrid-tests.sh /path/to/bcprov-jdk18on-1.87.jar
```

Pinned crypto dependencies: cryptography >=46.0.7,<47; pqcrypto 0.4.0
(PQClean ML-DSA-65); Bouncy Castle Java 1.87. Gradle Wrapper 8.11.1 is already
in the repository, but its presence does not authorize an app build/license
acceptance on this branch. Python and Java secrets are not portability fixtures.

## Repository and local-only artifacts

The checkpoint commit saves source, tests and documentation. Do not add local
toolchains, virtualenvs, databases, runtime data, APKs, private keys or plaintext
hybrid private payloads. `.tools-android/`, `.venv-fingerprinto/`, build outputs
and runtime stores are ignored. The local ignored tools are only a convenience;
their existence/content is not part of the committed restore point.

For a future redesign, inspect the checkpoint commit and create an isolated
checkout/branch if useful. Do not reset/delete the user's later work to restore
this snapshot. Update this handover with the new direction only after it is
actually specified; preserve the old profile/fixtures intentionally if migration
compatibility is required.
