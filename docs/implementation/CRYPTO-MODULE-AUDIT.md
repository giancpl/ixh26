# Crypto module implementation audit

## Scoped addendum: external optical token

The earlier audit below remains a dated source review. On `leomaster`,
`fingerprinto/beacon.py` adds an isolated token experiment; see the
[specification](../prnu/BEACON-EXPERIMENT.md) and
[tested SHA/results](../prnu/BEACON-VALIDATION.md). This addendum is not a
new end-to-end audit or physical-camera validation.

| Added capability | Boundary |
|---|---|
| P-256 code signed by an external beacon, bound to verifier challenge and installation | Verifies token authenticity/session, not emission through optics |
| Fixed optical grid and bounded PNG/JPEG ROI decoder | Pinned Pillow baseline; no localisation, perspective correction or error correction |
| Verifier-owned pinned SPKI and slot/age checks | No deployment registry/revocation or atomic one-use session service |
| Standalone JSON report / CLI | Class S, no physical-origin pass; v3 wire/reducer unchanged |

Ten beacon tests and nine targeted v3 regressions passed at `8fb3b3c`.
Splicing and same-session reuse intentionally pass token checks while preserving
`physicalOriginVerified=false`. These are documented counterexamples to a
physical-origin claim. No real device, Android build or OTS verification ran.
This does not close the implementation/calibration gaps listed below.


Review entry point: [CRYPTO-REVIEW-HANDOFF.md](CRYPTO-REVIEW-HANDOFF.md), including
the required reading order and the distinction between historical results and
the untested audit corrections.

Date: **21 November 2026**. Reviewed baseline: `107f407` on `leomain`, plus the
audit corrections described below. This is a source/dependency/control-flow
review, **not** a new test run, build, device evaluation or security certification.
The user requested execution testing on another branch. Previous test counts
do not validate these new corrections.

Source corrections are saved in `84f3fae` (`FIX: validate before signing and
retain recoverable enrollment decisions with history`).

## Conclusion and scope

The offline cryptographic core is substantially implemented. **The entire
manufacturer/PRNU Android specification is not implemented.** In particular,
there is still missing integration/validator code, not merely testing to do.
Do not label this branch as a complete capture application or a calibrated
camera-attribution product. The empirical exercise remains an open stop/go gate.

The review covers the current `../crypto/crypto.md` requirements, the v3 roadmap and the
preserved v1/v2 implementations. UI, app compilation/SDK licence acceptance,
upload-server integration and XRPL remain outside the agreed branch scope.
No RSA device keys or RSA enrollment issuer paths were found in the module;
the mention in `enrollment.py` describes rejection of unsupported RSA paths.

## Requirement-to-source map

| Requirement | Source inspected | Implementation status |
|---|---|---|
| Restricted deterministic CBOR, bounded types and profile isolation | `cbor.py`, `core.py`, `receipt_v3.py`; `ReceiptV1.java`, `ReceiptV3.java`, `ProtocolCbor.java` | Implemented for versions 1, 2 (separate hybrid) and 3; Java v3 decode/signature verification does not evaluate JSON/trust/origin. |
| v3 E, F_I/F_P/F_D/R/C and SHA256(C) | `receipt_v3.py`, `ReceiptV3.java` | Implemented; P-256 v3 and hybrid-v2 have distinct domains/wires. Parsing/exporting a receipt is not acceptance. |
| Hardware P-256 installation identity and stable key lifecycle | `InstallationKeys.kt`, `InstallationSignature.java` | Mechanisms implemented with hardware checks, external enrollment challenge and app-private/no-backup state; actual Keystore execution remains unvalidated. |
| Hybrid ML-DSA-65 AND Ed25519 | `hybrid.py`, `hybrid_receipt.py`, `HybridSoftwareKeys.java`, `HybridKeyEnvelope.java`, `HardwareHybridVault.kt` | Separate v2 experiment implemented. Software hybrid secrets are AES-GCM wrapped using hardware Keystore AES and P-256-bound; they enter RAM during signing. No native hardware PQ claim. |
| Independent attestation, revocation and fresh one-use intake | `android_attestation.py`, `enrollment.py`, `enrollment_requests.py`, `enrollment_v3.py` | Mechanisms implemented. V3 verifies possession and exact attested app declarations before consumption. Actual Android roots/status/release policies are deployment inputs. |
| Durable enrollment acceptance and authority publication | `enrollment_requests.py`, `enrollment_v3.py`, `trust_v3.py` | Audit correction: accepted decision/proof retained in the same transaction as challenge consumption; scoped recovery API; original authority publication reused on retry. Current policy/revocation must still be reassessed by the provisioner. |
| Authenticated enrollment/reference status history | `trust_v3.py` | Audit correction: signed updates retained by installation/sequence, current/history consistency checked. Existing stores migrate their current record only; previously overwritten versions cannot be recovered. Whole-database rollback protection requires deployment controls. |
| Both origin checks, failure priority, pending and personal-choice gates | `evidence_v3.py`, `workflow_v3.py`, `EvidenceV3.java`, `personal_choice.py` | Implemented in offline orchestration/shared reducer. A manufacturer pass does not bypass PRNU. Personal choices are local and never verifier inputs. |
| Strict policy/report attachment hashes, JSON limits and ZIP bounds | `receipt_v3.py`, `bundle.py` | Implemented in Python. Audit correction: issuance validates report and worst-case signed bundle size before calling the signing key/journal. |
| Repeatable reference estimator, masks and fixed PCE | `prnu.py`, `PrnuExperiment.java` | Real experimental numeric implementation on green arrays. Audit correction: explicit ordered floating-point sums avoid Python-version-dependent accumulation. No universal attribution threshold is shipped. |
| Reference byte identity and policy/installation association | `prnu_records.py`, `PrnuReferenceRecord.java`, `trust_v3.py` | Canonical records and Java writer implemented; Python atomic publication and signed external associations implemented. Physical provisioning is not established by hashes or these administrative APIs. |
| Image decoder and per-lens/mode geometry | `prnu_image.py`, `Geometry` | Pinned Python JPEG route implemented. Equivalent supported Android decoder route is missing; current Java input is already decoded arrays. |
| Calibration measurement and independently approved pass/fail regions | `evaluate_samples`, `assess`, signed reference associations | Tooling/guards implemented. Dataset labels and approval are independently trusted inputs; real same-model, cross-device performance has not been established. |
| Production manufacturer credential validation | `manufacturer_unconfigured`, injectable `manufacturer_validator` | **Missing real validator.** Default code only inspects some JPEG candidates/placement conflicts and withholds pass. It is not C2PA validation and does not cover all manufacturer evidence formats. |
| Sign-once persistence and byte-preserving transport | `receipt_journal.py`, `workflow_v3.py`, `SignedReceiptJournal.kt`, `bundle.py` | Offline workflow and storage primitives implemented. A process dying before journal commit may invoke signing again on retry; no durable/anchored receipt was published by the transaction. Network submission must remain after durable commit. |
| OTS binding, explicit network, pending/failed anchor separation | `anchoring.py`, `anchoring_v3.py`, `cli_v3.py` | Adapters implemented over the existing settlement backend. Bitcoin time is not exposure time. Prior mocked RPC/calendar tests are not real-chain verification. |
| Final capture callback, calibration worker/private reference lifecycle, signed capture/import pipeline | `MainActivity.kt`, `CaptureReport.kt`, `CaptureEvidence.java` and module call sites | **Not connected.** The current callback exports unsigned capture diagnostics. It does not issue v3 receipts or run native calibration. |

Python paths in the table are under `fingerprinto/`; Java/Kotlin paths are under
`android/app/src/main/java/com/example/photoanchor/`.

## Missing implementation versus missing evidence

The following are genuine implementation gaps that cannot be called complete
just by moving testing to another branch:

1. A manufacturer validator for a chosen real credential format, with issuer
   trust policy, capture semantics, signature and exact-asset binding. The
   callback contract alone is insufficient. Format/sample/trust decisions are
   needed before implementing a production adapter; do not invent a synthetic
   format and present it as manufacturer support.
2. Android decoding/preprocessing route, cancellable calibration worker and
   private/atomic reference lifecycle, using a decoder configuration that an
   independent verifier can actually reproduce. Java numeric parity does not
   establish JPEG decoder interoperability.
3. Connecting the existing capture callback to both checks, current authenticated
   enrollment, report/policy freezing, hardware signing and sign-once persistence;
   connecting imports to complete mandatory verification. App screens/server
   queue wiring remain outside this branch, but the unconnected boundary must
   be visible to anyone integrating the crypto APIs.

Real-device calibration, attestation validation on the actual phone, witnessed
physical-camera enrollment and a qualifying manufacturer sample are **missing
acceptance evidence** as well. An authority may approve a reference only after
its required independent provisioning/calibration process, not because a demo
can call `sign_record`. Keeping thresholds null withholds the corresponding
PRNU pass/mismatch. Missing evidence must not be replaced with a convenient
threshold, trusted boolean or an inferred device identity from EXIF.

## Audit corrections to verify on the test branch

- Report diagnostics too large/deep/nonfinite or inconsistent with E must be
  rejected before any signing callback or new journal entry. Include the total
  bundle size at the boundary and ECDSA signatures up to 72 bytes.
- Run float reference/PCE vectors on Python 3.11 and 3.12+ and the JVM. Existing
  published reference bytes/refHash remain immutable; do not re-estimate an old
  record or silently change its decoder/method. The explicit sum follows the
  method's originally intended row-major binary64 accumulation.
- At fresh acceptance, inject a persistence failure and verify that both the
  challenge consumption and retained decision roll back. After a successful
  commit, simulate death before authority publication and recover the original
  accepted result without re-consuming the challenge.
- Recovery must refuse the wrong policy, key, challenge, app declarations or
  missing/wrong v3 possession proof. Generic legacy attestation acceptance must
  not be promoted to recovered v3 proof of possession.
- Repeat or race authority publication: reuse identical stored bytes; reject
  conflicting same-sequence decisions and never resurrect a grant after a
  higher-sequence revocation/update.
- Retain active/revoked/reference-policy changes as separate signed history
  entries. Check current/history disagreement and migrate a pre-audit database;
  do not fabricate missing historical versions.
- Then run the existing CBOR/signature, v3, hybrid, reducer, numeric, bundle,
  journal and OTS regressions, followed by Android/device/real-data checks.

Only source inspection, syntax/static checks and diff checks were performed
for this audit. Execute the above on the separate branch before claiming that
the new corrections pass. Regulatory relevance is documented separately in
[../crypto/regulations.md](../crypto/regulations.md); there is no new certification or qualified
timestamp implementation in this audit.
