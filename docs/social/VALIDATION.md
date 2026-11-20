# Executed validation — 2026-11-20

These results describe actual local execution. They are not a claim of mainnet
anchoring or Android phone acceptance.

## Source and isolation

The approved source baseline is
`5ecef6749c38186b5f256d4d30fb8333f20d290e`. Shared protocol/Android SDK gate:
`ef664b34b6ca5e0a8b7d7dc3183bc003762b0be2`. Integration is delivered on
`integration/nostr-photo-mvp`; the original checkout and remote default branch
are left to their independent work. No force push or default-branch change is
part of this delivery. `settlement/` has no changes from the approved baseline.

## Offline Python and Android

```sh
python -m pytest -q -m 'not network and not regtest and not social'
./scripts/social-compose.sh run --rm --no-deps \
  -e PHOTOANCHOR_DATA_DIR=/tmp/photoanchor-offline-tests \
  --entrypoint python api -m pytest -q -m 'not network and not regtest and not social'
./scripts/social-build-android.sh
```

- Python 3.14: **103 passed, 3 deselected**. Includes the existing 46 settlement
  cases, 35 protocol/bundle cases and 22 backend cases. Sockets are blocked by
  the offline fixtures; calendar/relay/RPC failures are simulated in these cases.
- Python 3.11 in Docker: **103 passed, 3 deselected**, 19.43 seconds.
- Final Android build from the integrated worktree: **BUILD SUCCESSFUL**,
  4 minutes 20 seconds, 53 tasks executed. `assembleDebug`,
  `testDebugUnitTest` and `lintDebug` all passed.
- Android JVM tests: **11 passed**, zero failures/errors/skips. These load the
  official native SDK and verify the shared Python/Kotlin vectors. Lint has
  dependency-update/icon warnings and no errors. Dependency versions are
  intentionally pinned to the approved toolchain.

The final debug APK is `social-artifacts/photoanchor-debug.apk` in the
integration worktree. SHA-256:

```text
707b7739557860cafdb9fe1fde384d95c7abafe97e045cf1b46f733cfc8b8e43
```

`apksigner verify` passed and its certificate digest matches the public
certificate from Docker volume `photoanchor-android-home`:

```text
89edf99a2aad44c838955a819fc2f545e6a54b0c76096f7511f1914ba39f9171
```

This is the APK's debug signing certificate, separate from user Nostr keys.

## Real Docker services

`./scripts/social-demo.sh` completed from fresh social project volumes. It used
real Bitcoin Core 30.2, the pinned official OTS calendar, the pinned relay and
the API/worker. No calendars, relay or RPC were mocked in this demonstration.

The recorded run `social-a67c893663d445f9a00adeeb2c39ecd9` produced:

| Check | Actual result |
|---|---|
| Relay publication | `PUBLISHED`, event read back through the official SDK |
| Before mining | `PENDING`, including independent pending ZIP verification |
| After explicit two-block mining | `CONFIRMED`, network `regtest` |
| Verified Bitcoin block | height `102`, block time `1795196188` |
| Changed JPEG bundle | CLI exit `3`, `INVALID` |
| Original confirmed bundle | CLI exit `0`, signature/hash/payload/proof checked |

Event ID:

```text
91e02ad2ab0461450e3321db8f9f4af1b8e95ab3e4d78714a610abaab19c5d94
```

The opt-in real social pytest also passed: **1 passed, 105 deselected** in
12.19 seconds. The standalone OTS integration was rerun against the dedicated
social node, preserving the other project's running environment:

```sh
./scripts/social-compose.sh run --rm --no-deps \
  -e UPP_REGTEST_TEST=1 -e PHOTOANCHOR_DATA_DIR=/tmp/photoanchor-regtest-tests \
  --entrypoint python api -m pytest -q -m regtest tests/test_regtest_integration.py
```

Result: **1 passed**, 4.24 seconds.

## Actual service restart

The social project's API, worker, relay, calendar and Core were stopped with
`down` (without `--volumes`) and recreated with the isolated `photoanchor-*`
image tags. All services became ready. The original event was fetched from the
relay, its JPEG hashed again and its exported bundle independently verified
against Core. The before/after reports were identical, including:

```text
photo_sha256   6c5ba4a98a0e6b5b8c011bcde83c8e3b3c5c29a172a5f45ee97dd2bddcd96e87
payload_sha256 bf4255e531f9cb37b61b227f86d71150589d234e833a252ec8db55ab9e73d850
proof_sha256   def7a0dc316300d781c961e551dd07e863f136417caab0d496fb669bc8305d64
```

The old block height/time remained valid on the preserved local chain. No
continuous miner was started. Runtime files and artifacts are retained in the
project's dedicated volumes, with exported demonstration artifacts ignored by
Git.

## Checks still requiring the phones

Actual Android camera capture, Android Keystore behavior, USB forwarding,
on-device feed/verification/export and profile persistence across app restarts
have **not** been executed on phones. The user explicitly deferred this step;
ADB listed no devices. Use the unchecked phone acceptance steps in
`CHECKLIST.md` when both phones are available. Native JVM tests and successful
headless service tests do not substitute for this validation.

CI workflows are provided. Local validation above is separate from any hosted
GitHub Actions execution.
