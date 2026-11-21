# PhoTO documentation

Start with the [project overview](../README.md) for PhoTO's purpose and the
meaning of signature, integrity, timestamp and origin checks. This page maps
the implementation and separates current behavior from research and history.

## Current application

The supported demo is the native Android app with a local Nostr relay and the
`social/` backend. The device holds the user's signing key; the backend stores
original media and independently retries relay publication and OTS submission.
Readers obtain posts from the relay. Android checks signatures and photo hashes
locally and requests fresh timestamp verification from the API's Bitcoin node.

| Component | Role | Guide |
|---|---|---|
| `android/` | Local profile, CameraX JPEG, signed Nostr posts, feed, verification and bundle export | [Android](../android/README.md) |
| `contracts/`, `upp_protocol/` | Shared API/fixtures, kind-20 validation, signed-event payload and bundle CLI | [Contracts](../contracts/README.md) |
| `social/` | FastAPI, SQLite/media storage and durable relay/OTS jobs | [Local app and Docker](social/README.md) |
| `settlement/` | Opaque-byte OTS stamp/upgrade/Bitcoin verification; mainnet by default | [Settlement](SETTLEMENT.md) |
| `fingerprinto/`, native v3 workflow | Optional device enrollment/receipts, private server PRNU and independent proof | [App v3 setup](V3-APP-INTEGRATION.md), [crypto specification](crypto/crypto.md) |
| `server/`, `web/` | Separate legacy image-byte API and web UI | [Legacy regtest guide](REGTEST_SERVER.md) |

`compose.social.yml` overlays the standalone regtest environment. The normal
social flow requires explicit operator mining. The legacy API has a separate,
opt-in regtest auto-mining mode. These are development environments.

## Three different timestamp payloads

| Flow | Exact bytes sent to settlement | Meaning of a verified proof |
|---|---|---|
| Native Nostr app | UTF-8 `["upp-nostr-photo/1","<event.id>","<event.pubkey>","<event.sig>"]`, without spaces or final newline | Commits to the signed event, which binds the photo hash and post fields |
| Legacy image API | Original image file bytes | Commits to those image bytes; it does not add a signed Nostr identity |
| Experimental evidence-v3 | The 32 raw bytes of receipt commitment `C` | Commits to the v3 receipt; enrollment, attachments and origin findings require their own verification |

These proofs cannot be substituted for one another. The OTS engine treats each
payload as opaque and hashes it. Receipt JSON is metadata, not a substitute for
the exact payload, standard `.ots` proof and trusted verification inputs.
The proof format does not identify mainnet/regtest; select the expected network.

## Origin research and open gates

The aim is to assess evidence for where a photo came from. A signature proves
use of a key, while PRNU is statistical camera attribution. Neither establishes
that the depicted scene is true. Installation attestation does not authenticate
the pixels, and no sensor signature is assumed available on Pixel/GrapheneOS.

Read the [crypto flow diagrams](diagrams/crypto-flow.md), [implementation audit](implementation/CRYPTO-MODULE-AUDIT.md),
[roadmap](implementation/CRYPTO-V3-ROADMAP.md) and [PRNU method](prnu/PRNU-EXPERIMENT.md).
The [integration review](reviews/LEOMAIN_PR9.md) records subsequent validation
of the source audit's corrections. The [native app integration](V3-APP-INTEGRATION.md)
now connects the v3 workflow; [its software validation](reviews/V3_APP.md) is recorded
separately. A real manufacturer validator, actual device attestation and independently
trusted, measured camera-reference calibration remain open. The hybrid-v2 experiment is
separate from the P-256 v3 profile and from the Nostr app identity.

[Legal positioning](crypto/regulations.md) describes dated research and service limits;
it is not a certification of PhoTO or its timestamps.

## Validation and historical records

The [technical crypto guide](crypto/README.md) explains the threat model and
receipt construction without replacing the product overview or setup guides.
The [three-minute pitch](pitch/pitch.md) and [three crypto slides](pitch/slides.md)
separate implemented software from real-device and calibration gates. The
[challenge handoff](challenge/task.md) and [review notes](.notes/review-notes.md)
are archived source-branch context, not current execution instructions.

- [Native v3 app validation](reviews/V3_APP.md): current integration checks and remaining phone acceptance.
- [PR #13 documentation review](reviews/PLASMASTARK_PR13.md): crypto-guide reorganisation reconciled with the current application.
- [Crypto-module integration validation](reviews/LEOMAIN_PR9.md): earlier source reconciliation and real Docker lifecycles.
- [Two-phone checklist](social/CHECKLIST.md#actual-two-phone-acceptance--deferred-by-the-user): still pending; earlier agent handoffs above it are historical.
- [Consolidation log](CONSOLIDATION.md): staged PR/branch history, not current setup instructions.
- [Earlier MVP validation](social/VALIDATION.md), [crypto checkpoint](implementation/CRYPTO-CHECKPOINT-2026-11-21.md) and [source handoff](implementation/CRYPTO-REVIEW-HANDOFF.md): dated snapshots, not current branch status.

Use **PhoTO** in new product documentation. Keep historical commit/branch names,
code/package identifiers, `PHOTOANCHOR_*` environment variables, APK filenames,
signed events and wire-format domains unchanged for compatibility.
