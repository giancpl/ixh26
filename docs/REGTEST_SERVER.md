# Optional regtest mining for the legacy image-byte API

This integrates the useful server behavior from `c0043ad` through the shared
settlement service. It does not change the native Nostr `/v1` API, its worker,
photo protocol, Android client, `feature-mobileApp` or `leomain`.

## Defaults and interfaces

Mainnet remains the network default. Configure calendars explicitly with
`IXH26_CALENDARS`; the server no longer submits automatically to public
calendars. Set `IXH26_NETWORK=regtest` and authorize the local HTTP calendar
explicitly to use regtest. HTTP calendars remain forbidden on mainnet.

`IXH26_AUTO_CONFIRM_REGTEST=1` enables a bounded mining action during stamp or
explicit upgrade. Its default is `0`. Enabling it for mainnet is rejected.
It requires the dedicated `ots` wallet and a synchronized regtest node. It
waits for the calendar transaction, mines exactly two blocks and polls upgrade
and actual Bitcoin verification for up to 120 seconds, subject to RPC/request
timeouts. No continuous miner is started. An unavailable node, calendar or
timeout preserves the existing pending proof and records diagnostics.

`POST /api/verify` accepts multipart `file`, optional `expected_sha256` (or
legacy alias `sha256`) and optional standard `proof`. Limits are 20 MiB for the
file and 1 MiB for the proof. It freshly checks the bytes using the official
OTS parser and local Bitcoin verification. It never upgrades, contacts a
calendar or mines, even when auto mining is enabled. Missing evidence is
`UNVERIFIABLE`; a changed file or malformed proof is `INVALID`.
The result includes network, binding check, payload/proof hashes, diagnostics
and independently checked Bitcoin height/time. No internal proof path is sent.

All proof creation and replacement goes through `SettlementService`: official
nonce/aggregation, no overwrite on stamp, locking and atomic validated upgrade.
The adapter keeps the service's original filename rather than copying the
proof onto `<sha>.ots`. The public `/api/proofs/{sha}.ots` URL still works,
and historical filenames remain readable. Cached JSON never confirms a proof.
The raw-calendar fallback, handcrafted proof bytes and digest-membership
verification fallback have been removed. The OTS package is a required
dependency; missing runtime dependencies fail installation/startup honestly.

These legacy proofs timestamp original image bytes. They do not implement the
Nostr signed-event payload, validate camera origin or prove capture time.
Regtest confirmation refers only to the configured local development chain.

## Docker demo and manual operation

```sh
./scripts/server-regtest-demo.sh
# Isolated beside other stacks:
IXH26_REGTEST_PROJECT=photoanchor-server-hardening \
PHOTOANCHOR_IMAGE_PREFIX=photoanchor-server-hardening \
IXH26_PORT=18887 ./scripts/server-regtest-demo.sh
```

The demo starts a dedicated Compose project, proves pending-before-mining in
manual mode, then explicitly enables automatic mode for a second fresh image.
Both modes independently verify the downloaded standard proof through Core,
reject an altered file and reject a fake proof containing the correct digest.
The script restores automatic mining to `0` on exit and saves evidence in
`social-artifacts/server-regtest/`. Existing stacks and volumes are separate.

For service management, use the same project and port settings each time:

```sh
docker compose -p photoanchor-server-regtest \
  -f compose.regtest.yml -f compose.server-regtest.yml up -d --wait server
docker compose -p photoanchor-server-regtest \
  -f compose.regtest.yml -f compose.server-regtest.yml logs --tail 100 server calendar
# Stops this project, preserving its volumes:
docker compose -p photoanchor-server-regtest \
  -f compose.regtest.yml -f compose.server-regtest.yml down
```

Only API port 8787 is published, on loopback (override with `IXH26_PORT`). Core
RPC and calendar remain internal. This image supplies the API, not a bundled
web frontend. The native app still uses its existing social Compose environment.

Host installation: `pip install -c requirements-server.lock '.[server]'`,
then `python -m server`. Host binding defaults to `127.0.0.1`; the container
explicitly binds `0.0.0.0` behind Compose's loopback-only publication.

## Executed checks, 2026-11-21

- Python 3.11 Docker, network disabled: **118 passed, 4 deselected**, 20.06 s.
- Python 3.14: **118 passed, 4 deselected**, 21.20 s; network-blocking fixture.
- New tests exercise real standard OTS serialization and service operations;
  calendars and RPC are fake only in offline tests. They cover opt-in defaults,
  network/sync checks, digest-membership attacks, cached fake confirmation,
  no mining/calendar access during verify, retries, timeout and write failures.
- Real isolated Docker manual mode: `CONFIRMED`, height 102, block time
  1795219730, image SHA-256
  `7ee64146a80a4d0601c1e818b0aac89b5067bcd71fb58745883ddd8a5864a1b1`.
- Real explicit auto mode: `CONFIRMED`, height 104, block time 1795219741,
  image SHA-256
  `d9a7a64d4e85878ebb6134977623aabf0a1ab0ba8a64f992b2af8442a81763b2`.
- Both real modes mined two blocks, passed independent standard-proof/Core
  verification and rejected changed images and forged proofs. No mock calendar
  or RPC was used in these Docker demos. Phone acceptance remains deferred.
- The final version was rechecked on the same persistent volumes: manual mode
  confirmed at height 106 (time 1795220000), automatic mode at height 108
  (time 1795220011), with the same independent verification and rejection checks.
  Container inspection confirmed `IXH26_AUTO_CONFIRM_REGTEST=0` afterward.
- Installed `settlement`, `upp_protocol`, `server` and the HTTP application
  imported successfully outside the source checkout with networking disabled.
- The 15 adapter tests passed again after adding explicit call counters to
  prove that forbidden calendar/miner requests cannot be hidden by exceptions.
- The opt-in pytest test ran against the final real server/calendar/Core:
  **1 passed**, 3.70 s, without mocks.

Four opt-in live tests are excluded from the offline gate. The unchanged old
`tests/test_web_server.py` remains outside that gate; this delivery does not
claim its historical handcrafted-calendar fixtures are valid integration tests.
The new `regtest` test can be explicitly enabled with `IXH26_REGTEST_TEST=1`
and `IXH26_TEST_MODE=manual|auto` in the server Docker environment. Use
`IXH26_API_URL=http://server:8787` when calling it from a separate tool container.
