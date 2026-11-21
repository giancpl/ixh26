# Integration and acceptance checklist

This records the earlier `68ff268` MVP delivery. For the current main-based
PR sequence and checks, see [CONSOLIDATION.md](../CONSOLIDATION.md).

Baseline: Android `5ecef6749c38186b5f256d4d30fb8333f20d290e`, including
settlement `1052ed6`. Integration branch: `integration/nostr-photo-mvp`.
No default-branch change, history rewrite or force push is part of this work.

## Agent handoffs

| Owner | Branch | Delivery | Validation |
|---|---|---|---|
| A | `feat/nostr-contracts` | `8b9425f`, `00cb5cb`, `42e6264`: contracts, protocol, bundle CLI | 35 protocol tests; genuine SDK signatures and OTS serialization |
| B | `feat/social-api` | `0deb864`, `aa90d5b`: API and durable workers | 22 offline backend tests, including lost leases and outage recovery |
| C | `feat/android-nostr` | `d6ab7b0`, `da53dbd`: wrapper and complete debug app | APK, 11 native JVM tests and lint passed |
| D/coordinator | `feat/social-infra` | Packaging, isolated Compose images/volumes, scripts, CI, docs; integrated delivery | 103 offline tests on Python 3.14 and Docker 3.11; real Docker checks |

Shared phase-gate commit: `ef664b34b6ca5e0a8b7d7dc3183bc003762b0be2`.
Both backend and Android consumer worktrees were fast-forwarded to this commit
before parallel feature implementation. Contracts are `contracts/openapi.json` and
`contracts/fixtures/`; changes must update consumers and vectors together.

## Automatic acceptance

- [x] Frozen SDK-signed event, JPEG, payload and hashes; negative variants.
- [x] Unicode, JSON escapes, tag order and profile rename Python vectors.
- [x] Independent bundle CLI ignores receipt and reconstructs payload.
- [x] Android base build with official Nostr SDK and JDK 17 (APK + native JVM test).
- [x] Backend media/post limits, concurrency, restart and independent retry.
- [x] Existing 46 settlement offline checks remain passing.
- [x] Python/Kotlin shared vectors, actual native SDK checks.
- [x] Debug APK, unit tests and lint pass (11 tests, zero errors; lint warnings only).
- [x] Real standalone regtest integration remains passing (1 passed, 4.71 s).
- [x] Real social Docker lifecycle from fresh project volumes succeeds.
- [x] Bundle pending/confirmed and modified JPEG rejection through CLI.
- [x] Service restart retains posts, media and proofs; same hashes independently verified afterward.

See [VALIDATION.md](VALIDATION.md) for actual commands, results and artifact
hashes. The settlement package is unchanged relative to the approved baseline.
The remote default branch `main` advanced independently; this delivery retains
the requested baseline and does not merge or change the unrelated web/server
work. The coordinator's working tree is isolated at `/tmp/ixh26-photo-mvp`;
the original checkout may be used by another task and must not be switched.

## Actual two-phone acceptance — deferred by the user

Update 2026-11-21: the [single-Pixel device run](../reviews/PIXEL9_PRO_2026-11-21.md)
now exercises capture, publication, hardware enrollment, v3 signing, both real
OTS proofs, export and independent verification. The checklist below retains
the separate two-phone gate; one handset does not establish Bob acceptance.

No phones were connected during initial implementation. The user requested
that this test be performed later. Do not mark these complete from headless
service tests, JVM tests or an APK build.

- [ ] Record date, APK commit/hash, Android versions and two distinct pubkeys.
- [ ] Install and configure USB reverse on Alice and Bob.
- [ ] Alice creates profile, captures and publishes an actual camera JPEG.
- [ ] Bob receives relay feed; local signature and hash pass; OTS pending.
- [ ] Explicit operator mining; confirmed regtest height/time within 120 s.
- [ ] Changed photo fails integrity; network failure remains unavailable.
- [ ] Export actual phone bundle; verify independently with CLI.
- [ ] Restart apps/services; profiles, post and proof remain usable.
