# FingerprinTO / PR #9 integration and validation

Native app follow-up: [connected v3 workflow](../V3-APP-INTEGRATION.md).
The source-branch status statements below predate this application integration;
real hardware/manufacturer/empirical calibration gates remain open.

The integration branch `integration/leomain-pr9` starts from main `814cb8e` and
merges the source history through `leomain@686a4ce`. Runtime review and fixes
use `f3bd1ef`; the later source commits add diagrams and their renderer only. The original `leomain`,
`feature-mobileApp` and default branch are not rewritten. This reconciles the
experimental crypto module with the native Nostr MVP; it is not completion of
the manufacturer/PRNU capture product described in the source roadmap.

## Review corrections

- **P1:** `EnrollmentPolicy.allowed_boot_states` defaults to Verified (0).
  SelfSigned (1) requires explicit configuration and nonempty, independently
  trusted boot-key pins; bootloader lock, firmware evidence, signatures and
  other policy checks remain mandatory. Unknown pins, unlocked devices and
  states Unverified/Failed are rejected. Default policy digests remain stable;
  enabling SelfSigned changes the digest and requires fresh enrollment intake.
  The synthetic regression exercises the GrapheneOS-compatible policy path;
  an actual Pixel 9 Pro chain is still required for device acceptance.
- **P2:** SQLite and filesystem errors produce credential-free JSON and exit 1.
  Malformed evidence/failed checks retain exit 3. Subprocess tests cover missing
  files, unavailable database directories, clean stderr and malformed archives.
- All image components and runtime/test constraints use **Pillow 13.0.0**. The
  PRNU decoder identifier remains `pillow-13.0.0/rgb-green-stored/1`; no prior
  reference, decoder identity or calibration is silently rewritten.

[GrapheneOS attestation policy guidance](https://grapheneos.org/articles/attestation-compatibility-guide)
explains accepting SelfSigned only with an approved Verified Boot key.

## Reconciliation and packaging

The native Compose activity, Nostr contracts, encrypted identity/outbox,
settlement API and Gradle 8.11.1 wrapper remain the main implementations.
Crypto Java/Kotlin primitives build alongside them; the old WebView activity
is not substituted for native capture. The unsigned capture diagnostic helpers
are utilities, not a v3 capture lifecycle. Bouncy Castle 1.87 is dependency-locked.

The distribution includes settlement, upp_protocol, social, server and
fingerprinto, with both bundle CLIs. Runtime Docker images include the crypto
package. Cryptography 46.0.7, pqcrypto 0.4.0 and their dependencies are pinned
in the shared constraints. The duplicate test_bundle module was renamed so
pytest collects both protocol suites together.

The four legacy aiohttp tests now use a configured SPA fixture, loopback-only
sockets and an official serialized calendar response. The real OTS client
performs submission/aggregation. LAN route discovery is isolated in these tests;
an explicit guard regression rejects external socket connections. The default
offline gate now includes these tests rather than excluding them. TypeScript
production compilation is validated separately.

## Executed validation, 2026-11-21

The additional source commit `686a4ce` was reviewed as documentation/tooling only.
Both PNGs were inspected and successfully regenerated from the Mermaid blocks
using Mermaid 12.3.0, Playwright 1.65.2 and the existing local Chromium browser.
Rendering dependencies were installed in a temporary environment, not added to
application runtime dependencies. Browser/font differences can affect the PNG
bytes; the regeneration instructions now state this limitation. At diagram
integration commit `43d7b58`, the runtime directories were identical to the
previously validated integration.

| Check | Result |
|---|---|
| Full offline Python 3.11, Docker network disabled | 237 passed, 134 subtests |
| Full offline Python 3.14 | 237 passed, 134 subtests |
| Independent transaction/recovery regressions | Rollback, scoped recovery, legacy-proof refusal, concurrent publication, revocation replay, migration/history consistency and pre-sign size rejection passed |
| Installed package outside source checkout | All five packages and settlement/Nostr-bundle/FingerprinTO CLI imports passed; Pillow 13.0.0; pip check passed |
| JDK 17 portable vectors | 12 v1/boundary vectors, 4 signed v3 vectors, 4,500 reducer combinations, 160 PRNU guards, 1,028 numeric comparisons and canonical reference import passed |
| Hybrid interoperability | Both ML-DSA-65 and Ed25519 signatures, AES-GCM wrapping and Java/Python verification in both directions passed |
| Native Android | assembleDebug, 20 JVM tests and lintDebug passed; 0 lint errors, 18 warnings (dependency-update/style/resource suggestions) |
| Web | TypeScript/Vite production build and 5 tests passed |
| Real social regtest | Relay publication, PENDING before mining, CONFIRMED after mining, independent bundle CLI and altered-image rejection passed; height 102 |
| Real standalone settlement | PENDING to CONFIRMED, standard proof upgrade and modified-payload rejection passed; height 104 |
| Real v3 regtest | PENDING to verified anchor, standard proof upgrade, standalone CLI and altered-asset rejection passed; height 106; origin stayed none |
| Real legacy image API | Manual and opt-in automatic regtest confirmation passed; each mined 2 blocks, checked independently and rejected altered files/fake proofs; heights 102/104 on a separate chain |
| Restart persistence | After relay/API/worker restart, the existing post remained PUBLISHED and fresh node verification remained CONFIRMED |

Offline calendar/RPC tests use simulated infrastructure and real OTS formats.
The Docker lifecycle tests above use actual Bitcoin Core 30.2 and the pinned
OTS calendar with no calendar/RPC mocks. The v3 demo uses synthetic software
identity and authority fixtures, explicitly not hardware attestation or camera
provenance. Its anchor success never becomes a manufacturer/PRNU pass.

The APK SHA-256 from the local retained debug signing identity is
`8a608e1e0df2441ffef19f387a3e9f591095fe868a13769c94c7f77a0cf36e48`.
Hosted builds use a different debug signing key and need not match this hash.
Logs, XML reports, APK and original real proofs/bundles are retained under the
ignored `social-artifacts/pr9-integration/` directory in the coordinator workspace.

## PhoTO identity and documentation follow-up

PhoTO is the app/project name. The overview, documentation map, app/contract
instructions and exported bundle instructions now use that name. The standalone
settlement reference moved intact to `docs/SETTLEMENT.md`; only its test command
was scoped to settlement tests. Source audit/checkpoint records are explicitly
historical. Experimental origin evidence remains separate from native Nostr
publication. Technical package IDs, keys, settings and wire formats are unchanged.

After the visible-name/documentation changes, local verification passed:
237 Python tests and 134 subtests on Python 3.11 with Docker networking disabled;
20 Android JVM tests, debug APK and lint; web production build and 5 tests.
Local documentation links and code fences were checked. These are new local
checks, separate from the earlier real-chain lifecycles and APK hash above.
No new phone, hardware-attestation or real-camera acceptance was performed.

## Reproduce

```sh
python -m pip install -c requirements-server.lock '.[social,server,test,prnu,hybrid]'
python -m pytest -q -m 'not network and not regtest and not social'
bash scripts/check-crypto-vectors.sh
scripts/social-build-android.sh
cd web && npm ci && npm run build && npm test
```

Use a distinct Compose project and free loopback ports alongside existing stacks:

```sh
export PHOTOANCHOR_PROJECT=photoanchor-pr9 PHOTOANCHOR_IMAGE_PREFIX=photoanchor-pr9
export PHOTOANCHOR_API_PORT=28080 PHOTOANCHOR_RELAY_PORT=27777
export PHOTOANCHOR_PUBLIC_URL=http://127.0.0.1:28080
scripts/social-compose.sh build
scripts/social-compose.sh up -d --wait --wait-timeout 120 bitcoin bootstrap calendar api worker
scripts/social-compose.sh run --rm social-tests
scripts/social-compose.sh run --rm demo
scripts/social-compose.sh run --rm crypto-tests
# Optional JSON-producing variant of the crypto test:
scripts/social-compose.sh run --rm crypto-demo
# Preserve volumes on ordinary shutdown:
scripts/social-compose.sh down
```

`crypto-tests` requires explicit Docker opt-in and is skipped in the ordinary
suite. Run real mining demos sequentially on a given chain. The separate image
API demo uses `IXH26_REGTEST_PROJECT`, `PHOTOANCHOR_IMAGE_PREFIX` and `IXH26_PORT`;
its script restores automatic mining to disabled when it exits. The workflow
for Docker integrations remains separate from the offline CI gate.

## Remaining acceptance gates

- Actual Pixel 9 Pro / GrapheneOS attestation roots, current status evidence,
  release policy and locked-device behavior; two-phone USB acceptance is deferred.
- A real manufacturer capture credential validator, if that evidence is offered.
  A sensor signature is not assumed available on the Pixel.
- Reproducible Android decoding, private reference provisioning, cancellable
  calibration/capture integration and held-out same-model PRNU measurements.
- Distributed authority/status freshness and historical-policy validation beyond
  locally retained signed updates. The module does not establish scene truth.

These remain explicit capability/empirical boundaries of the experimental module.
Passing builds and synthetic signatures do not establish physical photo origin.
