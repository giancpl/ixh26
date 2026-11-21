# PhoTO local app guide

This demo links a Nostr signing key, an immutable JPEG and Bitcoin timestamp
evidence. It is designed for two USB-connected Android phones and a local Docker
environment. There are no public relays configured and no automatic external
publication. The optional [native v3 workflow](../V3-APP-INTEGRATION.md) adds
hardware-key enrollment and private server PRNU; actual hardware acceptance is
still deferred. Account recovery, iOS, RAW, comments, likes,
followers and a web verifier are outside this MVP.

## What a reader verifies

1. The Android reader uses the official Nostr SDK to check the event ID and
   Schnorr signature. The public key identifies the signing key, not a verified
   human identity. Profile names are editable declarations.
2. The reader downloads the original JPEG and computes its SHA-256 locally.
   The signed kind 20 event binds its URL, hash, MIME, dimensions and caption.
3. The API performs a fresh OTS verification against the configured local node.
   Android checks the receipt's payload binding and explicitly selected network.
   It labels this result **verified by the local node — regtest**. The phone
   does not independently validate the Bitcoin chain.

None of these establishes that a scene is real or that the capture time is
accurate. Bitcoin block time is blockchain temporal evidence, not a precise
camera clock. **Regtest confirmation is only against a local development chain;
it is not a mainnet anchor.** HTTP receipts are untrusted metadata. A standard
`.ots` proof plus the exact payload is the portable cryptographic artifact.

## Start and run the real Docker demonstration

Start with the [PhoTO overview](../../README.md) and [documentation map](../README.md).
Current v3 application checks are recorded in the [app integration review](../reviews/V3_APP.md);
the earlier crypto-module checks are in the [source integration review](../reviews/LEOMAIN_PR9.md).
[CONSOLIDATION.md](../CONSOLIDATION.md) records the staged history; the earlier
validation report and agent handoffs in this directory are historical.

Requirements: Docker Engine with Compose; internet for the first image/dependency
build. Android deployment additionally needs `adb`, two Android 10+ phones,
USB debugging and USB authorization. JDK 17/SDK 35 are supplied in a build image.

```sh
./scripts/social-demo.sh
```

To run beside an existing project, choose distinct volumes, image tags and
loopback ports. The signed media URL must match the chosen API port:

```sh
export PHOTOANCHOR_PROJECT=photoanchor-consolidation
export PHOTOANCHOR_IMAGE_PREFIX=photoanchor-consolidation
export PHOTOANCHOR_API_PORT=18080
export PHOTOANCHOR_RELAY_PORT=17777
export PHOTOANCHOR_PUBLIC_URL=http://127.0.0.1:18080
./scripts/social-demo.sh
```

Keep these variables for subsequent Compose commands. Phone instructions use
the default ports 8080 and 7777; this alternate project is for isolated service
validation. Do not change the signed public URL of an accepted post.

This starts Bitcoin Core 30.2, bootstrap, the official calendar, the pinned
relay, API and persistent worker. From empty project volumes bootstrap creates
the dedicated `ots` wallet and mines spendable regtest coins once. Ordinary
restarts preserve the wallet, calendar and application data.

The automated demo creates two temporary SDK keys (not Android profiles),
uploads a JPEG, publishes the signed post, reads it from the real relay and
checks a pending proof. It then explicitly mines two blocks, waits for the
worker to upgrade, freshly verifies confirmation, checks an exported bundle
with the independent CLI and rejects an altered JPEG. It saves evidence below
`social-artifacts/social-<run-id>/`. This exercises real services without mocked
calendars, relay or RPC; it does not replace the phone acceptance test.
The lifecycle after startup has a 120-second deadline. Do not run demos or
manual mining concurrently on the same project.

To start without creating posts or mining timestamp confirmations:

```sh
./scripts/social-up.sh
./scripts/social-compose.sh ps
./scripts/social-compose.sh logs --tail 100 api worker relay calendar bitcoin
```

Only these host ports are published, both on loopback:

| Component | URL |
|---|---|
| API and original media | `http://127.0.0.1:8080` |
| Nostr relay | `ws://127.0.0.1:7777` |

Bitcoin RPC and the calendar have no host ports and stay on the internal
regtest network. The social bridge permits Docker's loopback publishing; the
application has only the configured local relay/calendar as destinations.
Named project volumes preserve blockchain, calendar, relay database, social
SQLite/media/proofs and demo artifacts. The standalone
`./scripts/regtest-demo.sh` remains separate under project `upp-regtest`.
The overlay also uses `photoanchor-*` image tags for Core, bootstrap and
calendar, so rebuilding the social environment does not replace standalone
settlement image tags used by other work.

## Build and connect Alice and Bob

```sh
./scripts/social-build-android.sh
adb devices -l
./scripts/social-phones.sh
```

The APK is `social-artifacts/photoanchor-debug.apk`. The build runs the Gradle
8.11.1 wrapper with JDK 17, AGP 8.9.1, Kotlin 2.0.21 and SDK/build-tools 35,
including JVM unit tests and Android lint. The official SDK is pinned to
`org.nostrdevkit:nostr-sdk:0.45.1`. See the [Android dependency notes](../../android/README.md) for the
Kotlin-compatible coroutine constraint and desktop native SDK used by tests.
The build retains its debug signing key in Docker volume
`photoanchor-android-home`, so subsequent `adb install -r` updates can preserve
the installed profile. This build key is separate from on-device Nostr keys.

The install script installs on every authorized connected device and runs:

```sh
adb -s ALICE_SERIAL reverse tcp:8080 tcp:8080
adb -s ALICE_SERIAL reverse tcp:7777 tcp:7777
adb -s BOB_SERIAL reverse tcp:8080 tcp:8080
adb -s BOB_SERIAL reverse tcp:7777 tcp:7777
```

Both installations use the same loopback URLs, so the URL in Alice's signed
post works on Bob's phone. Reapply reverse mappings after reconnecting USB or
restarting adb. Cleartext HTTP/WebSocket is enabled only in the debug variant;
this environment is not a production deployment.

Create Alice's profile on one installation and Bob's on the other. They must
have distinct public keys. Capture and publish one JPEG on Alice, refresh Bob's
relay feed and open verification. Signature and integrity are checked locally;
the timestamp initially remains pending. Then, as an explicit operator action:

```sh
./scripts/social-mine.sh
```

The command waits for the calendar transaction and mines two blocks. Within
120 seconds the worker should upgrade the proof; refresh verification to see
the confirmed regtest height/time. Network errors remain unavailable checks,
not invalid signatures or photos. Export a bundle for independent checking.
Restart services/apps and confirm profiles, posts and proofs remain available.
Record actual phone results in [CHECKLIST.md](CHECKLIST.md); successful builds
and headless demos alone are not phone acceptance.

To prepare a changed JPEG for the phone's **Check a JPEG copy** action, download
the original into `social-artifacts/`, append a byte to a separate copy and
transfer that copy to the phone's Downloads directory with `adb push`. The
image may still display normally; the byte hash must fail. Preserve the signed
post and original JPEG while performing this check.

One profile is kept per installation. The private signing key is encrypted with
AES-GCM using an Android Keystore key and excluded from backup/export. It never
goes to the server. Uninstalling/clearing application data loses this identity;
key recovery is intentionally not implemented. Keystore encryption does not
constitute a hardware attestation of the photograph.

## Contracts and persistence

The frozen [OpenAPI schema](../../contracts/openapi.json),
[contract notes](../../contracts/README.md) and shared fixtures define the HTTP
and Python/Kotlin protocol. `kind:0` profiles are signed/published by the device.
Each photo uses `kind:20` with exactly one JPEG `imeta`, general `m` and `x`
tags and a caption/title. No application GPS or device identifier is added.
Previews may be resized in memory; originals are served without recompression.

After validating the event, the application constructs the exact UTF-8 bytes:

```text
["upp-nostr-photo/1","<event.id>","<event.pubkey>","<event.sig>"]
```

There is no whitespace or final newline. Lowercase hex lengths are 64, 64,
128. The settlement receives these as opaque bytes; the photo, its URL and
caption are bound through the signed event ID. There are no `kind:1040` events
and no claim of NIP-03 compatibility.

Android durably queues the already signed event before sending it. HTTP retry
keeps the exact event, URL and caption. The API stores media by hash and accepts
only references to its own archive; it never fetches an event's arbitrary URL.
SQLite persists the post and two independent jobs, relay and settlement.
Workers retry transient failures every five seconds and serialize work for an
event. A relay failure cannot suppress OTS work, and a calendar failure cannot
suppress relay publication. Proofs use settlement's existing lock/atomic write
behavior. There is no background mining.

`publication_status` is `QUEUED`, `PUBLISHED` or `ERROR`. `settlement` stays
`null` until a real settlement result exists. A public receipt retains the
settlement fields while replacing internal `proof_file` with `proof_url`.
`POST /v1/posts/{id}/verify` actually calls the verifier again, rather than
returning cached confirmation. Errors have `{"error":{"code":"…","message":"…"}}`
and suppress credentials and raw upstream exceptions.

## Inspect and independently verify a bundle

API media upload is raw `image/jpeg`, at most 20 MiB. Post requests are
`{"event": <signed event>}`, at most 16 KiB. Read a known post and its standard
proof or ZIP through:

```sh
curl http://127.0.0.1:8080/v1/posts/EVENT_ID
curl -X POST http://127.0.0.1:8080/v1/posts/EVENT_ID/verify
curl -o bundle.zip http://127.0.0.1:8080/v1/posts/EVENT_ID/bundle
```

Pending proofs can be exported too. Before proof creation, `/proof` returns
404 `proof_not_ready` and `/bundle` returns 409. A bundle contains `photo.jpg`,
`event.json`, `payload.bin`, `proof.ots` and `README.txt`; any receipt is only
optional metadata. Set `BUNDLE` to a ZIP below your exported artifact directory:

```sh
BUNDLE=social-REPLACE_WITH_RUN_ID/bundle.zip
./scripts/social-compose.sh run --rm --no-deps \
  -v "$PWD/social-artifacts:/inspection:ro,z" --entrypoint python api \
  -m upp_protocol.verify_bundle "/inspection/$BUNDLE" \
  --network regtest --bitcoin-conf /config/client.conf
```

The CLI independently verifies ID/signature and JPEG hash, reconstructs and
compares payload bytes, and reads the standard `.ots` through settlement. It
does not trust receipt JSON or need a calendar for verification. Exit codes:
0 confirmed, 2 pending, 3 invalid, 4 unverifiable, 1 local/usage error. Regtest
proofs require the matching retained chain. Upgrades also require resolving
their internal URI `http://calendar:14788` from this Docker environment.

## Tests and configuration

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -c requirements-server.lock -e '.[social,server,test,prnu,hybrid]'
python -m pytest -q -m 'not network and not regtest and not social'
./scripts/social-build-android.sh
./scripts/social-compose.sh run --rm social-tests
docker compose -f compose.regtest.yml run --rm integration
```

The ordinary Python suite blocks external sockets; marked local HTTP tests
allow loopback only. Protocol vectors use real SDK
signatures and genuine serialized OTS proofs. Infrastructure failures use
calendar/relay/RPC adapters in unit tests; the Docker integration tests use
real services and are skipped by default. Kotlin JVM tests use the official
desktop native binding for signature compatibility on the same fixtures.
CI runs Python, debug APK, JVM tests and lint; real Docker integrations are a
separate manually triggered workflow. CI configuration is not evidence that
a hosted workflow or phone validation has actually run.

Compose sets `PHOTOANCHOR_DATA_DIR`, `PHOTOANCHOR_PUBLIC_URL`,
`PHOTOANCHOR_RELAY_URL`, `PHOTOANCHOR_NETWORK`, `PHOTOANCHOR_CALENDARS`,
`PHOTOANCHOR_BITCOIN_CONF` and `PHOTOANCHOR_TIMEOUT`. Defaults and validation
are documented in the service configuration. Regtest calendars are explicit;
there is no fallback to public calendars. Core uses its local cookie from the
read-only Bitcoin volume. See the [settlement guide](../SETTLEMENT.md) for mainnet/pruned
node configuration; `.ots` does not identify the Bitcoin network.

```sh
# Ordinary shutdown, keep all data:
./scripts/social-compose.sh down
# Explicit destructive reset of this project's volumes only:
./scripts/social-compose.sh --profile tools down --volumes
```

Use a distinct `PHOTOANCHOR_PROJECT` for a separate chain/archive; host ports
still need to be free. Never reset volumes when retaining proofs that need
that local chain. Relay data, media, posts and keys are not a production backup
or security policy. The unauthenticated loopback API is a development service.

References: [official SDK](https://doc.nostrdevkit.org/install.html),
[NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md),
[NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md),
[NIP-03 warning](https://github.com/nostr-protocol/nips/blob/master/03.md).
