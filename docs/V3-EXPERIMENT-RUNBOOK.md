# Run the v3 exercise

This branch supplies offline cryptographic mechanisms and a reproducible PRNU
experiment. It does not establish that the actual phone/app route works. Use
the following stop/go sequence before calling the exercise solved.

## 1. Install and identify the exact route

```sh
python -m pip install -e '.[prnu]'
python -m fingerprinto v3 --help
```

Use the real physical camera identifier and a mode that does not switch lenses.
Keep original JPEGs, not social-network/downloaded/re-encoded copies. Start with
30 acceptable enrollment images, and reserve separate development and final
test sets. Include other units of the same model. Keep images/references private
under the ignored `data/` directory; do not commit real reference material.

An example geometry file (replace identifiers and source dimensions with actual
observations, not EXIF assertions of physical identity):

```json
{"camera":"REPLACE-with-physical-camera-ID","mode":"REPLACE-with-fixed-app-route",
 "width":256,"height":256,"decoder":"pillow-13.0.0/rgb-green-stored/1",
 "source_width":4000,"source_height":3000}
```

The adapter rejects unsupported source size/orientation/color routes. Do not
silently resize or rotate an image to make it pass. Android decoding remains
a different route until explicitly validated; the shared Java numeric core is
tested on identical decoded arrays only.

## 2. Estimate and measure, without enabling decisions

```sh
python -m fingerprinto v3 prnu-enroll --geometry data/geometry.json --out data/reference.json data/frame01.jpg data/frame02.jpg
python -m fingerprinto v3 prnu-score --reference data/reference.json --asset data/heldout.jpg
```

Supply the complete enrollment list (the short command above shows argument
syntax, not a recommended two-frame calibration). The reference is published
atomically without overwriting. Output includes its exact-byte SHA-256 and
explicitly says it is not independently trusted/calibrated. Score output remains
`inconclusive` and exits 2 even when a numeric score is large.

Collect a split file for `prnu-evaluate`:

```json
{"enrollment":["original-image-sha256-hex"],
 "development":["different-original-image-sha256-hex"],
 "samples":[{"asset":"final-original-image-sha256-hex","device":"physical-unit-A",
             "mode":"fixed-route","genuine":true,"score":null}]}
```

Replace every placeholder with actual 64-character digests. Set score to the
measured value, or null for unusable captures. Select candidate thresholds using
the development set only, then evaluate once on the final set:

```sh
python -m fingerprinto v3 prnu-evaluate --data data/split.json
```

Optional `--accept` and `--reject` evaluate independently chosen candidate
regions; no default thresholds exist. The output reports counts/rates by physical
unit and mode and never authorises decisions. If reference/query separation,
quality, or same-model impostor performance is inadequate, stop and record that
this route does not yet solve the exercise. Do not tune the final test set.

## 3. Provision trust, then issue and verify

The independent provisioner uses `enrollment_v3.intake` for attestation and fresh
proof of possession, durably retains its decision, and publishes an authority
record with `retain_accepted`. It separately witnesses physical-camera reference
enrollment and approves calibration regions. Null thresholds withhold decisions.
`trust_v3.sign_record` is an authority administrative primitive; do not give the
phone the authority key or let a supplied bundle create its own trust database.

The application calls `workflow_v3.issue_v3` with its immutable capture,
verifier-owned trust dependencies, stable operation ID and installation-key
signing callback. Both origin checks run before signing; retries reuse the
same receipt. Preserve all returned bytes. The current Python implementation is
an offline integration API, not an Android capture-screen implementation.

```sh
python -m fingerprinto v3 trust-import --record data/enrollment.json --trust-db data/trust.db --authority-spki data/provisioner.der
python -m fingerprinto v3 pack --receipt data/receipt.cbor --asset data/original.jpg --policy data/policy.json --report data/report.json --out data/evidence.zip
python -m fingerprinto v3 verify data/evidence.zip --trust-db data/trust.db --authority-spki data/provisioner.der --reference-dir data/references
```

For detached credentials add `--credential`. Reference lookup uses
`<refHash-lowercase-hex>.json` in the verifier-selected directory. No manufacturer
validator is bundled for a real issuer: the default command withholds that pass.
Programmatic deployment adapters must perform the full capture/issuer validation.

CLI codes: 0 operation succeeded or all mandatory verification checks complete;
2 pending verification or experimental score; 3 invalid evidence/failed check;
1 missing dependency, unavailable storage/I/O or operational support. A complete `none` level can exit 0:
read the reported level, never interpret process success as proof of provenance.

## 4. Optional Bitcoin anchoring

On the supported POSIX settlement environment, `v3 verify-ots` additionally
checks a bundled proof with an explicit `--network`/trusted Bitcoin node.
`v3 stamp` is an explicit network submission requiring completed current
enrollment/evidence checks, `--network` and `--out`. It submits the raw 32-byte C
file; OTS hashes it once as SHA256(C). It reuses the existing settlement retry
mechanism. Pending anchoring does not upgrade origin, and a disproven proof
blocks acceptance. XRPL is outside this branch.

## Remaining external acceptance evidence

- A real manufacturer credential/capture route and authorised trust policy,
  if manufacturer verification is to be offered.
- Witnessed reference enrollment, disjoint real data and approved acceptance
  and rejection regions, independently per mode and physical sensor.
- Android decoder interoperability/route calibration, worker cancellation and
  the actual capture/calibration lifecycle. App UI/build and SDK acceptance are
  deferred; the current JVM checks do not execute Android hardware.
- Validate the new atomic intake-decision retention, scoped recovery and
  idempotent authority-publication paths; deployed authority key custody/status
  distribution and current-policy checks remain operational requirements.

These are open acceptance gates, not successful results inferred from fixtures.
