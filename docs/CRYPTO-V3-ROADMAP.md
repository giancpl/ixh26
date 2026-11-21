# v3 roadmap: test whether the exercise is reasonably solvable

Baseline: `db5a857` saves v1/v2 code; `c3ca43c` clarifies the checkpoint.
Specification: the current `crypto.md`, targeting P-256 evidence-v3. Hybrid v2
remains an independent experiment. Preserve its tests/bytes and legacy v1.

## Objective and feasibility gates

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
  pass in 65 Python methods; see `EVIDENCE-V3-FORMATS.md` for the exact boundary.
  Java import/JSON decoding remains outside this construction adapter.
- Stages 3/4 mechanisms: experimental Haar residual/reference/PCE implemented
  in Python and Java on decoded green arrays. Python additionally has a pinned
  optional JPEG adapter, exact-byte reference records and atomic publication.
  1,028 cross-language numeric comparisons pass; 69 Python methods pass.
  Decoder interoperability on Android and trusted physical provisioning remain
  separate requirements; see `PRNU-EXPERIMENT.md`.
- Stage 5 tooling checks disjoint sets and emits per-device/mode empirical
  counts, without approving thresholds. Real-device calibration remains open.
- Stage 7 offline orchestration and retained authority records implemented:
  authenticated updates/revocation, separately approved reference thresholds,
  both validators, enrolled-key issuance, verified sign-once retries and
  independent verification of current findings versus signed claims. 73 Python
  methods pass. See `V3-TRUST-AND-WORKFLOW.md` for administrative boundaries.
- Stage 6 has an injectable manufacturer adapter and conservative default
  presence inspection only. A qualifying production issuer/credential validator
  is still required before any real manufacturer pass is available.
- Old checkpoint documents describe the pre-v3 implementation, not completion of
  this roadmap. Record each subsequent step and its actual checks here.

Research basis for the feasibility gate: [Chen, Fridrich and Goljan,
Digital Imaging Sensor Identification (Further Study)](https://ws2.binghamton.edu/fridrich/Research/EI6505-25.pdf)
derives reference estimation/detection under a simplified sensor model and
discusses preprocessing and error estimation. It does not validate a particular
modern handset/app route or establish a universal 30-frame threshold. The
smartphone-processing paper linked in crypto.md could not be fetched in this
session (HTTP 403); no claim here rests on having reviewed its contents.
