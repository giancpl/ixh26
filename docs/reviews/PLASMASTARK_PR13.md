# PlasmaStark PR #13: documentation integration review

Reviewed source: `leomain@4a05fa02e8c8aa8387c08153c0c843fbeb8b6da7`.
The documentation was reviewed at `42b40a8`; the subsequent commit only adds
the pitch illustration, which was inspected separately and left unchanged.
Integration base: `main@726a3bf`, after PR #11 and PR #12. All reconciliation
is on `integration/plasmastark-pr13`; the author's branch is untouched.

## Findings and resolution

| Priority | Finding | Resolution in this integration |
|---|---|---|
| P1 | The root README replaces PhoTO's app identity/setup with a FingerprinTO-only overview, conflicting with the newly merged product documentation. | Keep the PhoTO overview and retain the useful threat model/construction in `docs/crypto/README.md`. Keep package names and cryptographic domains unchanged. |
| P1 | Current-facing README/pitch claims say Android capture/signing is missing and hardening is untested, although PR #10/#12 now supply execution evidence and software integration. | Update current claims and pitch; preserve older audits/handoffs as explicitly historical. Real manufacturer validation, phone acceptance and measured calibration remain open. |
| P1 | File moves leave links from the current documentation map and integration follow-ups pointing at removed paths. | Repair 21 broken local references and two documentation-map links whose meaning changed when a new crypto README was added. Validate local targets and section anchors. |
| P2 | The new 360-line settlement guide duplicates the operational guide already maintained on main, with older status statements. | Retain its new URL as a short entry point to canonical `docs/SETTLEMENT.md`. |
| Coordination | PR #13 is a draft and explicitly asks not to merge or enable auto-merge; it also conflicts with current main in both READMEs. | Prepare a separate, merge-ready integration PR with resolved conflicts. Do not promote, push to or rewrite `leomain`. |

The technical exposition distinguishes byte integrity, installation-key
possession, manufacturer evidence, statistical PRNU and timestamp semantics.
It does not turn the installation key into a sensor key or claim a universal
physical-origin guarantee. The model-level indistinguishability argument is
explicitly conditional; long-term trust archiving is presented as proposed.
No new legal/certification claim or cryptographic runtime behavior is introduced.

## Validation performed on the reconciled tree

- Python 3.14 offline suite: **251 passed, 134 subtests**, six opt-in integrations
  deselected. Public network access is blocked by the test fixture.
- JDK 17/Python 3.11 portable checks, Docker networking disabled and cached
  checksum-verified Bouncy Castle: 12 v1/boundary vectors, 4 signed v3 vectors,
  4,500 reducer combinations, 160 PRNU guards, 1,028 numeric comparisons,
  canonical reference import and hybrid signatures/receipts in both directions.
- **183 local links** and **17 section anchors** checked before this report was
  added; no unresolved target. Diagram PNGs regenerated successfully from the
  relocated source using the existing browser, Playwright and Mermaid bundle.
  Generated review images are local artifacts; existing tracked PNGs are retained.
- Actual stage-example CLI check: original synthetic v3 bundle returns exit 2
  with enrollment pending; changed asset with the original receipt returns exit 3.
  Neither command contacts calendars or claims trusted physical origin.
- Spoken pitch: **271 words total, 118 crypto words**, below the 280/140 budgets.
  The 180/90-second limits remain an unmeasured rehearsal requirement.
- `git diff --check` passes. No application/settlement/crypto implementation or
  Android source is changed; the only Python diff is the renderer's source path.

Logs and prepared synthetic stage bundles are retained locally in the ignored
`social-artifacts/pr13-review/` directory. Earlier PR #12 APK/device software
checks are linked as historical evidence, not represented as a new phone test.
Actual Pixel/GrapheneOS and physical PRNU acceptance remain deferred.
