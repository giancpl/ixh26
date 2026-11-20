# Integration and acceptance checklist

Baseline: Android `5ecef6749c38186b5f256d4d30fb8333f20d290e`, including
settlement `1052ed6`. Integration branch: `integration/nostr-photo-mvp`.
No default-branch change, history rewrite or force push is part of this work.

## Agent handoffs

| Owner | Branch | Delivery | Validation |
|---|---|---|---|
| A | `feat/nostr-contracts` | `8b9425f`, `00cb5cb`: contracts, protocol, bundle CLI | 76 Python tests passed, 2 skipped before social integration was added |
| B | `feat/social-api` | In progress | Pending |
| C | `feat/android-nostr` | Foundation `d6ab7b0`; full app in progress | Base APK and native SDK JVM smoke test passed |
| D/coordinator | `feat/social-infra` | Packaging, Compose, scripts, CI, docs | In progress |

Shared phase-gate commit: `ef664b34b6ca5e0a8b7d7dc3183bc003762b0be2`.
Both backend and Android consumer worktrees were fast-forwarded to this commit
before parallel feature implementation. Contracts are `contracts/openapi.json` and
`contracts/fixtures/`; changes must update consumers and vectors together.

## Automatic acceptance

- [x] Frozen SDK-signed event, JPEG, payload and hashes; negative variants.
- [x] Unicode, JSON escapes, tag order and profile rename Python vectors.
- [x] Independent bundle CLI ignores receipt and reconstructs payload.
- [x] Android base build with official Nostr SDK and JDK 17 (APK + native JVM test).
- [ ] Backend media/post limits, concurrency, restart and independent retry.
- [x] Existing 46 settlement offline checks remain passing.
- [ ] Python/Kotlin shared vectors, actual native SDK checks.
- [ ] Debug APK, unit tests and lint pass.
- [x] Real standalone regtest integration remains passing (1 passed, 4.71 s).
- [ ] Real social Docker lifecycle from fresh project volumes succeeds.
- [ ] Bundle pending/confirmed and modified JPEG rejection through CLI.
- [ ] Service restart retains posts, media and proofs.

## Actual two-phone acceptance — deferred by the user

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
