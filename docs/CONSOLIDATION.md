# Project consolidation

Work happens on `integration/project-consolidation`, in an isolated worktree,
starting from `main` commit `4f5f28ad9689e217653a50322bc939390b4ded7b`.
Existing feature branches, the original checkout and the default branch must
not be switched, rewritten or deleted by this consolidation.

## Migration order

Each stage must be independently reviewable. Importing the old MVP wholesale
would conflict with the newer Android application and leave incompatible
publication and timestamp contracts in the same product.

| Stage | Scope | Status |
|---|---|---|
| 1 | Shared contracts, genuine signed fixtures, protocol validation, bundle CLI, optional packaging and offline gate | Integrated; validation recorded below |
| 2 | Add the target `/v1` application backend; durable independent relay and OTS jobs | Integrated; legacy `/api` consumers remain separate |
| 3 | Move photo consumers to kind 20 and the signed-event OTS payload | Pending |
| 4 | Consolidate Docker services; explicit operator mining, automated headless demo | Integrated; real isolated demo passed |
| 5 | Reconcile Android with the native Compose client; selectively preserve mobile UI improvements | Pending |
| 6 | Complete CI, fresh-volume regtest demo and the deferred two-phone acceptance | Pending |

The agreed target is a native Android client, the official Nostr SDK, one
application API, a dedicated relay and the existing opaque-byte settlement
package. The current web application remains available while its consumers
are migrated; its legacy contracts are not silently reinterpreted as the new
ones. Do not re-sign or rewrite already published events or replace their OTS
payloads. Historical image proofs and new event-payload proofs certify different
byte sequences and need an explicit format boundary in consumers.

## Branch inventory at the start

| Branch | Tip | Role |
|---|---|---|
| `main` | `4f5f28a` | Current web/server and Android baseline; settlement included |
| `feature-mobileApp` | `c2a5bd4` | Four commits beyond main: WebView bridge, mobile UX, icons |
| `feat/regtest-auto-settlement` | `c0043ad` | Local-only web integration with automatic regtest mining |
| `feat/nostr-contracts` | `42e6264` | Protocol, fixtures and bundle verifier |
| `feat/social-api` | `aa90d5b` | FastAPI backend and durable workers |
| `feat/android-nostr` | `da53dbd` | Native Compose/Nostr client |
| `integration/nostr-photo-mvp` | `68ff268` | Complete earlier MVP with recorded Docker and Android validation |
| `feat/social-infra` | `68ff268` | Same integrated tip; not an infrastructure-only delivery |
| `validation/nostr-photo-mvp` | `68ff268` | Local alias of the integrated tip |
| `feature/bitcoin-settlement-regtest` | `1052ed6` | Historical local branch, already contained in main |
| `leomain` | `eb6c293` | Newly discovered: independent `fingerprinto` crypto/capture module and tests; review separately |

All worktrees were clean during the audit. Contract, backend and Android source
directories in their specialist branches match the earlier integrated MVP.
The settlement package also matches main. Branch deletion and stash cleanup
are not part of the current stage.

## Compatibility boundaries

| Concern | Existing web/mobile | Target PhotoAnchor MVP |
|---|---|---|
| Photo event | Nostr kind 1 | Nostr kind 20 with the frozen JPEG tags |
| Timestamp payload | Original image bytes | Exact `upp-nostr-photo/1` envelope containing event ID, pubkey and signature |
| HTTP API | `server/`, `/api/*` | Frozen `/v1/*` schema in `contracts/openapi.json` |
| Relay | Embedded aiohttp implementation | Dedicated pinned relay with native SDK clients |
| Android | Existing camera app; WebView bridge on mobile branch | Native Compose application with Keystore-backed identity |
| Mining | Automatic on local regtest feature branch | Explicit operator action; automation only as an optional demo mode |

The embedded relay currently checks the canonical event ID and signature
format, but not the Schnorr signature cryptographically. Its existing relay
test even uses a placeholder signature. This is a known migration issue;
the new protocol validator rejects such signatures using the official SDK.
Adding the validator library does not itself change the legacy relay.

Merge simulations found four conflicting paths between main (or the mobile
branch) and the earlier MVP: `.gitignore`, Android Gradle, manifest and activity.
The automatic regtest branch adds conflicts in `.dockerignore`, `pyproject.toml`
and `tests/conftest.py`. A clean textual merge of other paths is not evidence
of matching product semantics.

## Stage 1 delivery and checks

Imported source commits: `8b9425f`, `00cb5cb`, `42e6264`. Only `contracts/`,
`upp_protocol/` and `tests/protocol/` come from those commits. Packaging exposes
the optional `protocol` extra and `photoanchor-bundle` console command. The
settlement package, existing applications, Android and regtest environment are
unchanged by this stage.

```sh
python -m pip install '.[protocol,test]'
python -m pytest -q --ignore=tests/test_web_server.py -m 'not network and not regtest'
python -m upp_protocol.verify_bundle --help
photoanchor-bundle --help
```

The offline gate includes all existing settlement tests and the imported
protocol tests. It blocks network connections through the existing pytest
fixture. The old socket-based web tests are explicitly outside this gate;
they must be addressed when migrating the backend, rather than labelled as
passing offline. Likewise this stage does not revalidate the old Android APK
or imply that the `/v1` endpoints are already served.

Executed on 2026-11-20:

| Check | Result |
|---|---|
| Python 3.11, existing Docker image, `--network none`, read-only source mount | 81 passed, 2 deselected (11.55 s) |
| Python 3.14, existing test venv, socket-blocking fixture | 81 passed, 2 deselected (15.10 s) |
| Build wheel, install into a clean target directory, import outside checkout | Passed in a Docker container with networking disabled; both module paths checked |
| Installed `upp-settlement`, `photoanchor-bundle`, module CLI help | Passed |
| `git diff --check` | Passed |
| Diff against main for settlement, server, web, Android, regtest Compose and shared pytest fixture | Empty |

The two deselected tests are the opt-in live calendar and regtest integrations.
The 81 passing checks comprise 46 settlement and 35 protocol tests. Wheel
installation used `--no-deps` with dependencies already in the container, so
this validates packaging and entry points, not a fresh network dependency
download. The new GitHub Actions gate is configured for Python 3.11 and 3.14;
hosted execution is separate from the local checks reported here.

Prior MVP reports on other branches are historical evidence, not validation
of this new branch. Physical two-phone acceptance remains deferred at the
user's request. No new Docker lifecycle or Android build was needed for this
protocol-only stage, whose application source diff is empty.

## Stage 2: target application backend

Stage 1 was merged through PR [#2](https://github.com/giancpl/ixh26/pull/2),
after both Python versions passed the hosted CI checks. Subsequent integration
branches start from the resulting remote main, without changing local worktrees
owned by other tasks.

The `social/` backend and its 22 tests come from source commits `0deb864` and
`aa90d5b`. It serves the frozen `/v1` contract, persists original JPEG bytes,
signed events and two independent jobs per post in SQLite. The worker uses the
official SDK to publish the existing signature, and the unchanged settlement
service to stamp, upgrade and freshly verify its opaque event payload.
Lease ownership, heartbeat and per-event locks protect retries and restarts.
Calendar failure does not stop relay publication; no user key enters the API.

```sh
python -m pip install -c requirements-social.lock '.[social,test]'
export PHOTOANCHOR_DATA_DIR=/path/to/persistent/social-data
export PHOTOANCHOR_PUBLIC_URL=http://127.0.0.1:8080
export PHOTOANCHOR_RELAY_URL=ws://127.0.0.1:7777
# Explicit regtest settings; start the real node/calendar separately:
export PHOTOANCHOR_NETWORK=regtest
export PHOTOANCHOR_CALENDARS=http://calendar:14788
export PHOTOANCHOR_BITCOIN_CONF=/path/to/bitcoin.conf
python -m uvicorn social.api:app --host 127.0.0.1 --port 8080
# In a second shell with the same environment:
python -m social.worker
```

No relay or calendar is contacted while accepting a post. A separate worker
must be running for publication and OTS progress. Mainnet remains the default;
the application has no implicit calendar submission destination. The optional
`social` extra and fully pinned constraints keep these dependencies outside
the standalone settlement installation. Automatic regtest mining is not part
of this worker.

The existing `server/` and web UI retain their historical `/api` behavior until
their consumers are migrated. They do not share a database or proof directory
with `social/`. Deploy the target backend for the native MVP, and do not present
legacy image-only timestamps as signed-event timestamps. Docker orchestration,
Android reconciliation and legacy UI migration have separate integration gates.

## Stage 4: isolated Docker orchestration

The backend was merged through PR [#3](https://github.com/giancpl/ixh26/pull/3)
after hosted checks passed on both Python versions. The complete offline suite
also passed locally on Python 3.14: 103 passed, 2 deselected (22.10 s).

The social overlay, dedicated pinned relay, build images, demo and deployment
scripts are imported from the earlier MVP. The old standalone Compose workflow
remains available. Integration containers explicitly select their test files so
optional legacy web or protocol dependencies do not affect standalone OTS.
No process continuously mines blocks. `social-demo.sh` performs explicit mining
as part of its bounded headless test; `social-mine.sh` is the operator command.

Image prefix, Compose project and loopback ports are configurable independently.
The current validation used `photoanchor-consolidation` for both project and
image prefix, API port 18080 and relay port 17777. Existing `photoanchor` and
`upp-regtest` containers, images and volumes were left intact.

Executed real demo on newly created project volumes, 2026-11-20:

- Event: `913c2b15effcf4eebdf3536551a846f51a896afd031839b82e8214050513e86e`.
- Relay publication: `PUBLISHED`; verification before mining: `PENDING`.
- Fresh node verification after mining: `CONFIRMED`, network `regtest`,
  block 102, timestamp 1795206007.
- Independent exported bundle verification passed; altered JPEG was `INVALID`.
- Evidence directory: `social-artifacts/social-0b1f8ea382004ef1ba9b3b4d177ec6bf/`.
- No Android device was involved. This does not replace phone acceptance.

The real standalone OTS integration passed (1 test, 8.05 s), as did the real
social integration (1 test, 10.35 s). The rebuilt social image also passed all
103 offline checks with networking disabled (3 live tests deselected, 23.08 s).
An actual Compose `down` without volume deletion followed by `up --wait`
preserved the event, relay history, original JPEG and confirmed proof. Fresh
node and independent bundle checks before/after restart returned identical
photo, payload and proof hashes; reports are in `.local/restart-{before,after}.json`.

Historical Android validation in `docs/social/VALIDATION.md` remains clearly
labelled as belonging to the earlier MVP. The current Android reconciliation
has a separate PR and build gate.

## Compatible web UX recovery

The `feature-mobileApp` source is being recovered selectively on
`integration/consolidate-web-ux`. It improves the responsive layout, capture
flow, profile/settings views, messaging and pixel icons in `web/`, with its
own production-build and unit-test CI job. Navigation preferences are public
UI state; the existing encrypted vault and password unlock remain unchanged.

The source branch's persisted raw credentials, passwordless automatic profile
replacement and disabled session locking are deliberately excluded. Its
Android WebView bridge and generated web bundles are also excluded: the native
client is the chosen Android app, and built frontend assets stay build outputs.
The source branch is retained; it is not declared fully merged or deleted.

Executed locally: `npm ci`, `npm run build` (TypeScript and Vite) and `npm test`:
5 passed. The tests include encrypted backup/signing interoperability and
preservation of identity after a wrong password. The real PBKDF2 work factor
is retained; those tests have a 30-second allowance. No credential is written
to plain localStorage or IndexedDB by the recovered code.

This is a legacy UI compatibility PR, not a web implementation of the `/v1`
contract. Web photos still use kind 1 and image-byte timestamps; the native
client uses kind 20 and signed-event payloads. The `server/` API is unchanged
by this UX recovery. Browser/device acceptance is not inferred from the build.
