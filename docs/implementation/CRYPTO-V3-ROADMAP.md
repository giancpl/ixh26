# v3 roadmap: test whether the exercise is reasonably solvable

## Separate beacon track: implemented baseline, open physical gate

On `leomaster`, [external optical tokens](../prnu/BEACON-EXPERIMENT.md) are
implemented outside receipt v3: independent P-256 signature/session/slot check,
visible fixed-grid renderer, bounded image decoder and offline CLI. The
[validation record](../prnu/BEACON-VALIDATION.md) identifies the code SHA and
10 beacon + 9 targeted v3 regression tests. No v3 format or reducer work is
needed for this software-only result; no third origin level is proposed.

Next steps, in order:

1. Independently review token semantics and the splicing/relay counterexamples.
2. Specify and deploy trusted beacon provisioning/revocation, an honest release
   clock and verifier-issued session lifecycle; these are not supplied by the CLI.
3. Evaluate an error-correcting format and pinned decoder against real screens,
   JPEG, blur, perspective, reflections and moire. The current grid is only a baseline.
4. Decide whether a standalone result is useful enough to attach to the signed
   diagnostic report. Automatic attachment/issuance wiring is not implemented.
5. Reassess usefulness against adaptive composition/relay. Successful decoding
   must never be promoted to sensor origin or physical presence.

The stages below remain the manufacturer/PRNU roadmap; beacon tests do not
satisfy their empirical or implementation completion gates.


Baseline: `db5a857` saves v1/v2 code; `c3ca43c` clarifies the checkpoint.
Specification: the current `../crypto/crypto.md`, targeting P-256 evidence-v3. Hybrid v2
remains an independent experiment. Preserve its tests/bytes and legacy v1.

## Objective and feasibility gates

Commercial/legal research is tracked separately in
[../crypto/regulations.md](../crypto/regulations.md). It identifies
possible evidential/document-workflow support and a future qualified-timestamp
option; it does not certify this prototype or change the technical acceptance
gates below.

The result describes evidence for a file's capture/source, not the truth of a
scene. Manufacturer credentials and PRNU answer different questions. A real
camera photograph of a display can satisfy a capture claim. A camera reference
can be copied; a key/receipt alone does not bind the physical sensor to the key.

The plan is conditional on real-data feasibility, not on the amount of code we
can write. Thirty accepted enrollment frames are an initial trial, not a
guarantee. Fixed geometry makes the first experiment manageable, but native
camera processing, multi-camera switching, rescaling and denoising can make the
chosen app route unsuitable. If this happens, document unsupported routes and
withhold PRNU pass/mismatch; do not compensate by choosing a convenient threshold.

In particular, a statistical PRNU mismatch must not override manufacturer
evidence unless the rejection region has independently met the required error
policy on suitable captures. Without that calibration, the score is inconclusive.
Reference enrollment must be independent of the query and trusted by the
verifier. User assertions, EXIF and application-signed reports cannot supply it.

## Stages and completion criteria

| Stage | Work | Completion criterion |
|---|---|---|
| 1 | Shared evidence reducer and PRNU decision guards | Python/Java agree on all outcome pairs, failure priority, pending required checks and issuance/import gates; no secret, image or network operations |
| 2 | v3 evidence/receipt codecs and attachments | Explicit version 3, E arity/types, JSON byte limits/digests/duplicate-key checks, no fallback, golden vectors and independent tamper tests; v1/v2 remain unchanged |
| 3 | Versioned PRNU experiment | Freeze decoder/channel/orientation/crop, masks, wavelet/settings, artifact removal, floating-point format, shift grid and PCE; Python reference implementation with reproducible synthetic numeric tests, not a dummy matcher |
| 4 | Reference enrollment and policy records | Streaming estimator, masks/support counts, atomic exact-byte reference serialization/refHash, per-lens/mode separation and trusted camera/installation association |
| 5 | Held-out calibration and stop/go decision | Separate enrollment/development/final test sets; genuine and other units of same model; false-match, false-reject and inconclusive rates/counts per mode; acceptance and rejection enabled independently only by validated policy |
| 6 | Manufacturer validator and result adapter | Trusted issuer/capture semantics, hard binding to exact J/A; absent/unknown-policy vs definitive failure; both checks evaluated, with report agreement checks |
| 7 | Retained enrollment and issuance orchestration | Authenticated trusted decision/ref association/status history; both checks, policy/report snapshots, enrolled hardware key, sign-once journal and retry/anchor state linked consistently |
| 8 | v3 bundle/CLI/OTS and independent verification | Reconstruct from original bytes and attachments, recompute evidence using verifier-owned policy/reference, report signed claims separately from current findings; no premature overall acceptance |

Stages 2–4 can implement mechanisms without a phone. Stage 5 cannot be passed
by generated fixtures, a chosen frame count or successful compilation. Stage 6
needs an actual qualifying capture sample and trust configuration. If the
manufacturer route is unsupported, the absence is itself an honest result;
it does not prove PRNU feasibility or prevent the v3 "no verification" level.

App build/SDK acceptance, calibration/detail UI and upload-server integration
remain outside this branch's current scope. Java/JVM module tests are allowed;
no app build or SDK installation is needed. XRPL remains excluded; no RSA device
keys or issuer paths are introduced. Future implementation instructions can
reorder the stages, but must not silently remove the empirical gates.

## First implementation boundary

Stage 1 is a pure function of **independently produced** findings. It must never
be fed an app's signed `pass` code as though that were the verifier's own result.
Both check findings are mandatory; `pending` represents unfinished work, while
unavailable/inconclusive are completed assessments with no positive evidence.
The reducer does not schedule checks, authenticate policy or implement PRNU.

Before signing, an enrolled key and valid evidence binding are required. Missing
manufacturer evidence alone is permitted in v3. On import, a valid receipt
signature is additionally required. Definitive failures dominate pending checks.
Absent/calendar-pending anchoring does not block; an unresolved claimed anchor
is pending, and a disproven anchor blocks. A personal trust choice is deliberately
not a reducer input and cannot change verification or C.

## Progress

**Latest source audit:** [CRYPTO-MODULE-AUDIT.md](CRYPTO-MODULE-AUDIT.md).
Pre-sign attachment preflight, ordered floating-point accumulation, retained
status history and crash-recoverable acceptance/publication have been corrected
in source. These corrections have not been execution-tested on this branch.
The historical test counts below exclude them. Real manufacturer validation and
Android decoder/calibration/capture integration are still missing code, not
merely unexecuted tests.

- Stage 1 implemented in `fingerprinto/evidence_v3.py` and `EvidenceV3.java`.
  It returns decision eligibility only, not an accepted receipt or measured
  camera attribution. Pre-signing eligibility cannot offer a trust choice until
  the signed receipt exists and its required checks have completed.
- Reproduce Python logic tests with unittest discovery; compare both languages
  with `bash android/tests/run-evidence-v3.sh` using JDK 17+ and Python 3.11+.
- Validation completed: 62 Python unittest methods passed (including v1/v2
  regression tests), 4,500 typed state combinations agree between Python/Java,
  and 160 PRNU decision-gate combinations pass on the JVM. No image scorer,
  real reference, enrollment or device evidence is validated by these tests.
- Keep detailed validator reason codes/reports alongside the reducer output:
  it identifies failed/pending checks but does not replace their diagnostics.
- Stage 2 byte mechanisms implemented: strict Python v3 decode, signed
  attachments and transport; Java streaming construction. Four shared signed
  golden vectors cover the three levels and pass/pass. Tamper/schema/JSON tests
  pass in 65 Python methods; see `../crypto/EVIDENCE-V3-FORMATS.md` for the exact boundary.
  Java now also strictly decodes/verifies v3 CBOR and shares the enrollment
  possession-proof root. JSON attachment/current trust evaluation remains in
  the independent Python verifier.
- Stages 3/4 mechanisms: experimental Haar residual/reference/PCE implemented
  in Python and Java on decoded green arrays. Python additionally has a pinned
  optional JPEG adapter, exact-byte reference records and atomic publication.
  1,028 cross-language numeric comparisons pass; 69 Python methods pass.
  Decoder interoperability on Android and trusted physical provisioning remain
  separate requirements; see `../prnu/PRNU-EXPERIMENT.md`.
  Java also emits portable canonical reference records, independently imported
  and checked by Python; floating-point estimates are compared within tolerance.
- Stage 5 tooling checks disjoint sets and emits per-device/mode empirical
  counts, without approving thresholds. Real-device calibration remains open.
- Stage 7 offline orchestration and retained authority records implemented:
  authenticated updates/revocation, separately approved reference thresholds,
  both validators, enrolled-key issuance, verified sign-once retries and
  independent verification of current findings versus signed claims. 73 Python
  methods pass. See `../crypto/V3-TRUST-AND-WORKFLOW.md` for administrative boundaries.
- Stage 6 has an injectable manufacturer adapter and conservative default
  presence inspection only. A qualifying production issuer/credential validator
  is still required before any real manufacturer pass is available.
- Stage 8 implemented for offline bundle/CLI/OTS: explicit `fingerprinto v3`
  commands, bounded attachments/archives, separate claims/current findings,
  SHA256(C) proof verification and existing idempotent stamping. Simulated
  calendars/RPC test the byte contract, not a real Bitcoin confirmation.
- Optional personal trust/distrust is stored separately against C, never read
  by verification and disallowed while failed/pending or before signing.
- Validation at integration: 150 POSIX tests plus 123 subtests passed; 77
  Windows unittest methods passed. The subsequently added personal-choice
  isolation test also passed separately. All new JVM decision/vector/numeric
  checks pass, including independent import of the emitted Java reference.
- Current practical endpoint: runnable offline crypto/PRNU experiment, **not**
  proven physical-camera attribution. `V3-EXPERIMENT-RUNBOOK.md` gives commands
  and open acceptance gates. Real-device data/calibration, a real manufacturer
  validator, Android decoder/capture-worker integration and operational
  provisioning/recovery remain open. No app build/SDK acceptance was performed.
- Old checkpoint documents describe the pre-v3 implementation, not completion of
  this roadmap. Record each subsequent step and its actual checks here.

Research basis for the feasibility gate: [Chen, Fridrich and Goljan,
Digital Imaging Sensor Identification (Further Study)](https://ws2.binghamton.edu/fridrich/Research/EI6505-25.pdf)
derives reference estimation/detection under a simplified sensor model and
discusses preprocessing and error estimation. It does not validate a particular
modern handset/app route or establish a universal 30-frame threshold. The
smartphone-processing paper linked in ../crypto/crypto.md could not be fetched in this
session (HTTP 403); no claim here rests on having reviewed its contents.
