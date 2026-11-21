# Crypto branch review handoff

Branch: `leomain`, proposed base: `main`. This branch is submitted for independent
review as an incomplete crypto-module implementation. Keep the pull request in
draft; review and execution validation belong to other reviewers/the separate
test branch. This handoff does not authorise merge or automatic merge.

## Read before assessing completeness

1. [Implementation audit](CRYPTO-MODULE-AUDIT.md): requirement-to-source map,
   missing code and the scenarios to validate for the latest corrections.
2. [Current specification](crypto.md): v3 P-256 target, manufacturer/PRNU
   semantics, independent checks, immutable J, signed E and trust boundaries.
   Its historical appendix describes v1, not v3 behaviour.
3. [Roadmap](CRYPTO-V3-ROADMAP.md) and [experiment runbook](V3-EXPERIMENT-RUNBOOK.md):
   implemented mechanisms versus empirical stop/go gates and executable commands.
4. [PRNU experiment](PRNU-EXPERIMENT.md), [v3 formats](EVIDENCE-V3-FORMATS.md)
   and [trust/workflow](V3-TRUST-AND-WORKFLOW.md): actual algorithm, wire/JSON
   bounds, authority records, recovery and retry semantics.
5. [Hybrid v2](hybrid-v2.md): separate ML-DSA-65 AND Ed25519 experiment. Hardware
   AES protects software secrets at rest; those secrets enter RAM for signing.
6. [Regulations](regulations.md): dated official-source mapping and limitations
   on legal/commercial claims, not a compliance certificate.

The [implementation progress](implementation-progress.md) and
[pre-redesign checkpoint](CRYPTO-CHECKPOINT-2026-11-21.md) retain historical
context. Their older completion/test statements must not replace the current
audit. Review the entire diff against main: it includes the v1/v2 foundation,
Android diagnostic capture export and local Gradle configuration as well as v3.

## Missing code and acceptance evidence

- A production manufacturer-credential validator with trusted issuer policy,
  capture semantics and exact-file binding. The default candidate inspector and
  injectable callback do not implement C2PA/manufacturer validation.
- A reproducible supported Android decoder route, cancellable calibration
  worker and private/atomic camera-reference lifecycle. Shared numeric Java
  arrays are not evidence of native JPEG decoder interoperability.
- Connecting the Android capture/import lifecycle to the v3 checks, frozen
  policy/report, authenticated enrollment, hardware signing and receipt journal.
  Current capture export is unsigned diagnostics.
- Real-device attestation/provisioning, witnessed physical-camera reference
  association, disjoint calibration/final datasets and same-model impostor
  error measurements. No actual device attribution is established by fixtures.

These are not all test-only gaps. UI/server upload wiring, XRPL and app build/SDK
licence acceptance remain outside the agreed scope of this implementation branch.
Qualified eIDAS timestamps and legal certification are not implemented features.

## Validation provenance and reviewer priorities

Earlier integration recorded 150 POSIX tests plus 123 subtests and 77 Windows
unittest methods passing, followed by a separate personal-choice isolation test.
JVM checks recorded 4,500 reducer combinations, 160 PRNU gates, four signed v3
vectors, 1,028 numeric comparisons and independent import of Java reference
records. See the roadmap for the phase-specific results. These checks used
synthetic fixtures and simulated calendars/RPC, not a real phone or confirmation.

**Commit `84f3fae` changes code after those runs. Its execution tests have not
been run on this branch.** It adds pre-signing attachment/aggregate preflight,
ordered PRNU sums, atomic acceptance retention, scoped recovery, idempotent
authority publication and retained signed status history. Review transaction
failure/concurrency, existing-store migration, proof/policy/application scoping,
revocation replay and byte-preserving retries using the detailed audit checklist.
Only source inspection, Python syntax parsing and documentation/diff checks were
performed for those corrections. There was no app build or SDK licence acceptance.

Do not interpret historical green test counts as validation of the current head.
Before any subsequent merge decision, independently execute the applicable
regressions and resolve or explicitly scope the missing implementation. No merge
is requested by this handoff.
