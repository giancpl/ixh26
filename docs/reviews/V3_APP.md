# PhoTO native evidence-v3 integration review

Branch: `integration/photo-v3-app`. Baseline includes the PhoTO identity changes
from PR #11. No source branch or default-branch history is rewritten.

## Implemented boundary

Native Android now calls authenticated enrollment, calibration, preparation and
receipt APIs. It preserves original JPEGs, offers a distinct cleaned-copy path,
validates the pinned authority/proposal, rebuilds R and signs through the existing
hardware-only Keystore/journal. Kind 20 includes C before Schnorr signing.

The backend keeps user keys out, isolates private frames/reference sessions,
retains signed authority records and immutable receipts, and fairly schedules
leased relay/event-OTS/enrollment/analysis/v3-OTS work. The API lacks the
operator authority private-key mount. Root/policy/revocation configuration is
explicit; absent configuration allows ordinary Nostr but never hardware grants.

Local phone checks cover ID/Schnorr, original JPEG hash and v3 P-256/attachments/C.
Origin and enrollment are labelled server/authority results. Independent Bitcoin
checks remain delegated to the configured node, explicitly regtest. Both proof
formats, existing event payloads and legacy v1/v2 vectors retain their contracts.

## Validation performed

The complete offline suite passed **251 tests and 134 subtests** on both Python
3.11 (Docker networking disabled) and Python 3.14 (external sockets forbidden
by the test fixture). Six opt-in integrations were deselected. The checks use actual SDK
signatures, synthetic ECDSA attestation chains, official CBOR/OTS formats and
Pillow decoding. Infrastructure is simulated only where existing unit tests
require calendar/RPC boundaries. New cases cover auth/replay/ownership, receipt
substitution, revoked enrollment, expired challenges, DB-write recovery,
lease fencing, private calibration splits and EXIF decoder identity.

The final Android integration build passed debug APK, **24 JVM tests** and lint
with **zero errors and 18 warnings**. Shared app
vectors verify authority/receipt signatures, attachment binding and Nostr C;
malformed/duplicate-key JSON and altered inputs are rejected.

After the final API changes, all 71 API/protocol tests passed again. Installed
packages, dependency consistency and both CLIs were checked outside the source
checkout with Docker networking disabled. The previous Nostr-only real Docker
integration and the previous crypto/regtest integration also passed,
independently of the v3 lifecycle.

Reproduction commands:

```sh
python -m pytest -q -m 'not network and not regtest and not social'
./scripts/social-build-android.sh
bash scripts/check-crypto-vectors.sh
./scripts/social-up.sh
./scripts/social-compose.sh run --rm social-tests
./scripts/social-compose.sh run --rm v3-app-tests
```

Portable JDK 17 checks passed all prior profiles: 12 v1/boundary vectors, four
signed v3 vectors, 4,500 reducer combinations, 160 PRNU guards, 1,028 numeric
comparisons, canonical reference import and hybrid verification both directions.

### Actual Docker lifecycle

The isolated `photoanchor-v3` environment ran the real pinned relay, official
OTS calendar and Bitcoin Core 30.2. An isolated loopback HTTP app used explicitly
synthetic PKI/software identity; its trust was never installed into the ordinary
API/worker or APK. No relay/calendar/RPC mocks were used in this lifecycle.

The final opt-in application integration passed in 19.14 seconds. Recorded event:
`c41a38f2d896bcc7d718d01ab1c3d4844ecc6c2afb17ab9af9f5747ea80a6b5c`.
Its receipt commitment is
`b5b64934355e8d46bef4bb97bc73d22fa2a4a48b47a47adfb5e49a3237bd1ab7`.

- Enrollment/preparation, private calibration and device-signature submission passed.
- Relay publication reached PUBLISHED; both independent proofs were PENDING before mining.
- Explicit mining confirmed v3 at height 106/time 1795246792 and Nostr at height 108/time 1795246794.
- Separate bundle exports and combined independent CLI verification passed.
- Altered photo verification returned INVALID; persistent records reopened and reverified.
- Origin remained `none`; synthetic calibration was not approved and no physical-camera claim followed.

The lifecycle mined two batches of two blocks because the calendar submissions
were distinct. There is no background miner. Proofs/JSON/bundles are retained
under the isolated project's artifacts volume and were exported locally to
`social-artifacts/v3-app-integration/`. That ignored directory contains test-only
synthetic trust material for reproduction; it is not production configuration.

## Delivery checklist

- [x] Shared contracts and public Python/Kotlin vectors.
- [x] Native enrollment, calibration, original capture, remote preparation and journalled signing.
- [x] Private authenticated API, persistent work, publication and two separate proofs.
- [x] Local signature/integrity checks and fresh server/node verification.
- [x] Separate portable exports and independent combined CLI verification.
- [x] Offline Python suites, portable crypto checks, APK/JVM/lint and real Docker lifecycle.
- [x] Real Pixel 9 Pro/GrapheneOS enrollment, camera capture and v3 signing
  ([2026-11-21 device report](PIXEL9_PRO_2026-11-21.md)).
- [ ] Held-out physical PRNU calibration and operator-approved association/regions.
- [x] One-Pixel process-death/restart and APK-update persistence checks.
- [ ] Two-phone USB acceptance.

## Remaining device acceptance

The initial delivery deferred all device acceptance. Actual Android
Keystore/attestation, USB deployment/camera UI and both OTS proofs have since
been exercised on one Pixel 9 Pro; see the dated report above. Physical-camera
route stability, held-out same-model PRNU measurements and the second handset
remain outstanding.
No real manufacturer credential validator or sensor signature is supplied.
A passing software build or synthetic authority grant is not hardware evidence.

Operator setup, reproduction and security boundaries:
[V3-APP-INTEGRATION.md](../V3-APP-INTEGRATION.md).
