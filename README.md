# PhoTO

**Share a photo. Check its signature, original bytes and timestamp evidence.**

PhoTO is an Android photo-sharing prototype built on Nostr. A person creates a
local profile, captures a JPEG and publishes a signed post; another person can
check who signed it, whether the downloaded file matches the post, and the
available Bitcoin/OpenTimestamps evidence.

The project explores how to support claims about a photo's origin. The current
social app provides signed publication and byte integrity. Camera-origin
verification is a separate experiment, with device integration and real-camera
validation still required.

## What the checks mean

| Check | What it establishes | Who checks it |
|---|---|---|
| Nostr signature and event ID | The event was signed by the indicated key; caption, URL and image hash are bound to it | Android, locally |
| JPEG integrity | The downloaded bytes match the SHA-256 in the signed event | Android, locally |
| OTS timestamp | The expected payload matches a standard proof verified against the selected Bitcoin chain | Local API + Bitcoin Core; exported bundles can be checked independently with the CLI |
| Camera origin | Manufacturer credentials or statistical PRNU evidence, under an explicit trust policy | Experimental `fingerprinto` module; not connected to native photo publication |

A public key identifies a signing key. Profile names, captions and declared
capture times remain author claims. A valid signature does not establish a
person's identity or the truth of a scene. The Android Keystore protects keys;
it does not provide a signature from the camera sensor.

The local demo uses **regtest**. Its `CONFIRMED` means verified against the local
development blockchain, not anchored on Bitcoin mainnet. Block time is temporal
evidence, not a precise capture time. Publication, integrity, timestamp and
origin results must remain separate.

## Run the local demo

Requirements: Docker Engine and Compose; internet for the initial build.
Run from the repository root:

```sh
./scripts/social-demo.sh
```

This runs the real relay, API/worker, Bitcoin Core and OTS calendar. It uploads a
photo, publishes a signed post, checks `PENDING`, explicitly mines two blocks,
checks `CONFIRMED`, exports a bundle and rejects an altered JPEG. It uses
temporary software identities, not phones or hardware attestation.

For Android deployment and the Alice/Bob flow on two USB-connected phones:

```sh
./scripts/social-up.sh
./scripts/social-build-android.sh
./scripts/social-phones.sh
# After publishing a photo and observing a pending timestamp:
./scripts/social-mine.sh
```

The [local app guide](docs/social/README.md) covers ports, USB forwarding,
configuration, logs, persistence and shutdown. No public relay is preconfigured.
Phone acceptance is still deferred; builds and headless demos do not replace it.

## Where to start

- [Documentation map and architecture](docs/README.md): components, proof formats and current scope.
- [Android app](android/README.md): identity, capture, feed, verification and build.
- [Protocol and API contracts](contracts/README.md): signed events, exact OTS payload and independent bundle verification.
- [Settlement guide](docs/SETTLEMENT.md): Python API, CLI, mainnet/pruned Core, regtest, statuses and exit codes.
- [Crypto experiment](docs/crypto.md): P-256 v3, enrollment and PRNU; [integration review](docs/reviews/LEOMAIN_PR9.md) records executed checks and remaining gates.

## Development and validation

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -c requirements-server.lock -e '.[social,server,test,prnu,hybrid]'
python -m pytest -q -m 'not network and not regtest and not social'
```

Python tests, shared Python/JVM crypto vectors, Android builds/unit tests/lint,
web checks and real Docker lifecycles have been executed; see the
[validation report](docs/reviews/LEOMAIN_PR9.md) for versions, counts and limits.
Actual Pixel 9 Pro/GrapheneOS attestation, two-phone acceptance, a real
manufacturer validator and measured PRNU calibration remain open.

**Naming:** PhoTO is the app and project name. PhotoAnchor and BitPin are earlier
product/UI names retained in historical reports. `fingerprinto` is the crypto
module, `settlement` the timestamp engine and `upp_protocol` the Nostr adapter.
Existing package names, commands (`photoanchor-bundle`, `upp-settlement`),
`PHOTOANCHOR_*` settings, application ID and protocol identifiers remain stable.
