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
| `fingerprinto/`, portable Android crypto primitives | Experimental installation receipts, enrollment and PRNU evidence | [Crypto specification](crypto.md), [runbook](V3-EXPERIMENT-RUNBOOK.md) |
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

Read the [crypto flow diagrams](crypto-flow.md), [implementation audit](CRYPTO-MODULE-AUDIT.md),
[roadmap](CRYPTO-V3-ROADMAP.md) and [PRNU method](PRNU-EXPERIMENT.md).
The [integration review](reviews/LEOMAIN_PR9.md) records subsequent validation
of the source audit's corrections. Native v3 capture integration, a real
manufacturer validator, actual device attestation and independently trusted,
measured camera-reference calibration remain open. The hybrid-v2 experiment is
separate from the P-256 v3 profile and from the Nostr app identity.

[Legal positioning](regulations.md) describes dated research and service limits;
it is not a certification of PhoTO or its timestamps.

## Validation and historical records

- [Latest integrated validation](reviews/LEOMAIN_PR9.md): automated checks, real Docker lifecycles and remaining acceptance gates.
- [Two-phone checklist](social/CHECKLIST.md#actual-two-phone-acceptance--deferred-by-the-user): still pending; earlier agent handoffs above it are historical.
- [Consolidation log](CONSOLIDATION.md): staged PR/branch history, not current setup instructions.
- [Earlier MVP validation](social/VALIDATION.md), [crypto checkpoint](CRYPTO-CHECKPOINT-2026-11-21.md) and [source handoff](CRYPTO-REVIEW-HANDOFF.md): dated snapshots, not current branch status.

Use **PhoTO** in new product documentation. Keep historical commit/branch names,
code/package identifiers, `PHOTOANCHOR_*` environment variables, APK filenames,
signed events and wire-format domains unchanged for compatibility.
