# PhoTO native evidence-v3 integration

PhoTO now connects its native Nostr app to the evidence-v3 mechanisms. Device
enrollment, local-server analysis, device signing, publication and the two OTS
proofs are separate operations with separate results. This is a local debug
integration. Real Pixel 9 Pro/GrapheneOS enrollment, capture and v3 signing were
tested on [2026-11-21](reviews/PIXEL9_PRO_2026-11-21.md); empirical PRNU and
two-phone acceptance remain outstanding. No sensor signature or manufacturer
validator is invented.

## Start, configure trust and build

From the repository root, Docker Engine/Compose are sufficient for services:

```sh
./scripts/social-up.sh
./scripts/social-export-trust.sh
PHOTOANCHOR_AUTHORITY_SPKI_FILE="$PWD/social-artifacts/authority.der" ./scripts/social-build-android.sh
./scripts/social-phones.sh
```

Ordinary restarts preserve the authority key, public configuration, enrollment
intake/history, private frames/references, original media, receipts and proofs.
The API cannot access the authority private-key volume. The worker/provisioner
can use it; no user's private signing key is sent to either process.

The exported DER is a **public** authority pin. The build script embeds it only
when supplied explicitly; a debug build without a pin still supports Nostr but
shows device enrollment unavailable. Do not obtain this pin from an untrusted
HTTP receipt. Changing authority requires explicit provisioning and preserving
old trust material for old receipts, not silently trusting a new server key.

Initial setup intentionally has **no trusted Android policy or revocation
snapshot**. The operator must prepare authenticated inputs under the ignored
`social-artifacts/operator/` directory. No synthetic roots are preinstalled.
A policy JSON has these fields (replace placeholders with independently trusted
material; these are not working credentials):

```json
{
  "id": "local-pixel-policy/1",
  "roots": ["BASE64_DER_TRUSTED_EC_ATTESTATION_ROOT"],
  "packages": {"com.example.photoanchor": 1},
  "signers": ["64_LOWERCASE_HEX_APK_CERTIFICATE_SHA256"],
  "boot_states": [0, 1],
  "boot_keys": ["APPROVED_GRAPHENEOS_VERIFIED_BOOT_KEY_HEX"],
  "minimum_os_version": 0,
  "minimum_os_patch": 0
}
```

Set minimum OS/patch values according to the operator's actual release policy.
`SelfSigned` requires explicit approved boot keys; unlocked/unknown states do
not gain acceptance. Follow the [official GrapheneOS attestation guide](https://grapheneos.org/articles/attestation-compatibility-guide).
The existing restricted EC certificate verifier may report unsupported real
chains; investigate them on the actual handset rather than weakening checks.

The revocation input is an operator-authenticated snapshot with
`blocked_serials` (integer certificate serials), ISO-8601 `observed_at` and
`valid_until`. Retain how the source was authenticated. Never synthesize an empty
list as a substitute for fetching/checking the actual relevant status source.
Missing/stale status does not accept enrollment. Configure using the operator
container (policy and revocations are mounted read-only from `/operator`):

```sh
./scripts/social-compose.sh run --rm provision python -m social.provision configure \
  --policy /operator/policy.json --revocations-file /operator/revocations.json
```

The worker also mounts this operator directory read-only so it can re-read the
current revocation snapshot at intake/recovery. The HTTP API has no such mount.

Enrollment checks hardware attestation at intake. The local authority grants a
30-day enrollment record, subject to signed status updates/revocation and expiry;
it is not a fresh boot/app attestation for every photo. Policy changes requiring
new intake need explicit key retirement/re-enrollment. Preserve historical records.

No API accepts roots, thresholds, trusted booleans or authority keys from phones.
New private routes use SDK-verified NIP-98 authorization and isolate records by
Nostr public key. See the [shared contract](../contracts/EVIDENCE-V3.md).

## Phone flow

1. Create the normal Nostr profile. In **Profile → Device identity**, enroll or
   retry. Android obtains a challenge, creates the P-256 hardware key, proves
   possession and retains the authority-signed enrollment result. The Nostr
   key remains independent. Rejected, expired and unavailable are explicit.
2. Capture an original JPEG. Its exact bytes remain unchanged. Review the
   privacy notice before uploading an original; no GPS is added by the app.
   Previews alone are resized/oriented. The ordinary publication action uses
   a separate cleaned copy and carries no original v3 claim.
3. To exercise PRNU, start a private session from a fixed-route capture. Store
   separate enrollment, development and held-out genuine frames, then import
   held-out other-camera JPEGs through Profile. Frames are private and are not
   posts. Thirty enrollment frames are a starting suggestion, not a guarantee.
4. Evaluate and refresh scores. The server uses Pillow 13, a fixed green-channel
   crop and a decoder identity specific to stored EXIF orientation. It rejects
   unsupported color/geometry routes instead of modifying input bytes.
5. Publish an original with v3 evidence. While preparation is queued, retry
   after a few seconds. Android verifies the pinned authority's proposal,
   reconstructs R from the original and attachments, checks its key/app identity
   and signs through Keystore. Retry reuses the exact journalled receipt and
   signed Nostr event. An expired preparation can be explicitly renewed.
6. The kind-20 event includes C before Nostr signing. Both post and receipt
   commitments have their own standard OTS proof. Read them as independent
   checks. Without device enrollment/analysis, publish the cleaned Nostr-only
   copy instead; it does not inherit the original's receipt.

Unfinished captures/captions, proposals, receipts and signed outbox entries
persist. Retiring an installation key requires confirmation and affects only
that P-256 key; old receipts and the Nostr profile remain. Local retirement is
not an automatic global revocation announcement. A new key requires enrollment
and new physical-camera associations. Key/account recovery remains outside
this prototype.

## Approving PRNU measurements

The server computes real experimental residual/reference/scores. It does not
automatically approve a sensor association or choose thresholds. An operator
must independently establish the physical-camera association, inspect disjoint
held-out genuine/other-camera data and decide acceptance/rejection regions.
Both regions are independently optional. No universal threshold is shipped.

```sh
./scripts/social-compose.sh run --rm provision python -m social.provision approve-calibration \
  CALIBRATION_ID --accept MEASURED_ACCEPT_THRESHOLD --reject MEASURED_REJECT_THRESHOLD \
  --confirm-physical-association
```

This is an administrative trust action, not proof that witnessing/calibration
happened. Without approved regions, scores do not yield a verified PRNU pass or
mismatch. Actual same-model false-match/false-reject measurements remain a
separate acceptance gate. A genuine camera can photograph a screen; PRNU,
installation attestation and timestamping do not establish scene truth.

## Mining, verification and export

The worker never mines. For an event with two submissions, explicitly run:

```sh
./scripts/social-mine.sh --event EVENT_ID --timeout 120
```

This waits for submissions and mines batches of two blocks only during this
bounded command. Two calendar batches can require more than two total blocks.
The old command without `--event` retains the standalone two-block behavior.
Every confirmation is explicitly regtest, with actual active-chain height/time.

The detail screen checks Nostr ID/signature, JPEG hash and v3 signature/attachments
on the phone. Enrollment/origin are labelled local-authority/server results;
Bitcoin proof checks are labelled local-node regtest results. Fresh v3 checks
remain possible even if the Nostr proof has not been created. Network failures
are unavailable results; altered signatures/bytes are invalid. JSON snapshots
never replace proof verification.

Export the Nostr ZIP and the separate standard v3 ZIP. The latter includes the
exact original/receipt/attachments/proof, never private PRNU references or keys.
For a Python installation, install `.[protocol,prnu]` with the repository's
constraints before combined verification. It needs explicitly supplied
trust/reference material and does not connect to the API or calendars:

```sh
photoanchor-bundle nostr.zip --evidence-bundle evidence-v3.zip \
  --network regtest --bitcoin-conf /path/to/bitcoin.conf \
  --trust-db /path/to/trust.sqlite3 --authority-spki /path/to/authority.der \
  --reference-dir /path/to/private/references
```

Without `--evidence-bundle`, the CLI checks the original Nostr contract only and
marks a present v3 commitment as not checked. The v3 result distinguishes signed
claims from recomputed findings. Explicit combined verification cannot exit as
fully confirmed when required trust or the second proof is unavailable.

## Reproducible software demo and tests

```sh
./scripts/v3-app-demo.sh
# Already built and running: skip rebuild/startup for a live presentation.
./scripts/v3-app-demo.sh --reuse
python -m pytest -q -m 'not network and not regtest and not social'
./scripts/social-build-android.sh
bash scripts/check-crypto-vectors.sh
```

The v3 demo starts an isolated loopback HTTP app inside its tools container with
**synthetic test PKI and software installation keys**. It never installs that
trust into the ordinary API/worker or APK. It uses the real Nostr relay, official
OTS calendar and Bitcoin Core; checks enrollment/preparation/signature/post,
both pending proofs, explicit mining, both confirmations, combined CLI export,
altered-photo rejection and persistent record reopening. Origin stays `none`;
synthetic calibration is not approved. Artifacts are under
`social-artifacts/v3-app-*/` after the wrapper copies them out.
The terminal displays each completed stage, from enrollment/calibration to
pending proofs, bounded mining, confirmation, independent export verification
and altered-photo rejection. `demo-flow.json` retains those milestones.
This is a complete software demonstration with synthetic camera samples, not
a simulated successful physical-camera attribution. It does not operate the
phone or replace its real private calibration session.

Ordinary shutdown preserves data: `./scripts/social-compose.sh down`.
Explicit `./scripts/social-compose.sh --profile tools down --volumes` deletes
this project's authority, blockchain, private enrollment/reference data and
proofs. Keep all required trust/chain material together for later verification.

The software suite and build alone do not validate hardware Keystore behavior,
USB/camera UI, real attestation roots or physical-camera attribution. The
[single-Pixel device report](reviews/PIXEL9_PRO_2026-11-21.md) records the real
checks actually performed. Run the remaining two-phone acceptance and held-out
Pixel/GrapheneOS measurements before claiming the corresponding guarantees.
