# PRNU experimental profile and stop/go evaluation

Visual overview: [PRNU, hashes and signature](../diagrams/crypto-flow.md), with diagrams of
the reference/PCE path and the v3 signed commitment. The diagram is in Italian.

Method `fingerprinto/prnu-haar-green/1` is an intentionally small numeric
experiment, not an implementation of a published camera attribution accuracy
claim. Its simple wavelet may fail on the actual camera pipeline. Measure that
before interpreting a result as sensor attribution. Never fit the final test set.

## Single-Pixel collection and automatic demo

PhoTO's guide collects **30 reference + 10 development + 10 held-out** original
JPEGs privately. These are experimental collection targets, not established
accuracy requirements. Starting a new guide keeps previous sessions and
references; the earlier one-frame device smoke test is not reused. Counters
come from server-accepted digests and survive restart. Retrying the last upload
at a phase boundary preserves its role rather than adding it to another split.

For actual measurements, use the fixed rear route, flash off, unchanged zoom,
dimensions and orientation. Begin reference collection with evenly lit plain
surfaces, avoid clipping/dark crops and change subject detail between captures.
Keep development and final scenes independent. Do not use a screen, re-encode,
rotate, crop or resize images to make the route fit. The app/server preserve
original bytes and reject unsupported routes or unusable crop residuals before
counting the frame. CameraX/phone processing can still suppress the useful
signal; no claim is made that the current JPEG pipeline is adequate.

The **demo series** choice permits operator automation of real CameraX captures
without pretending that the scene was changed. Although its file digests and
software groups are disjoint, same-scene captures are statistically dependent
and may produce scene-driven correlations. They demonstrate software execution,
not an independent calibration or a false-match rate. An external original-image
dataset could later supply negatives without connecting a second handset, but
its provenance and geometry must be validated; never resize it to satisfy input.

After explicitly starting **New guided calibration → Start demo series** from
a fresh capture in the installed debug app, leave Create open. Use an authorized
device serial and only the own Compose API container:

```sh
# Read-only inspection of the active, explicitly labelled demo session.
python scripts/pixel-prnu-demo.py --serial "$PIXEL_SERIAL"
# Actual private camera capture, then queued server analysis. No publication,
# mining, key export, synthetic image generation or calibration approval.
python scripts/pixel-prnu-demo.py --serial "$PIXEL_SERIAL" --collect --evaluate \
  --timeout 900 --output social-artifacts/pixel-prnu-calibration/demo.json
```

The script reads only the active demo ID from debug app state and its own local
operator database, and taps only PhoTO's shutter/private-store/evaluation controls.
It stops on rejected quality/route, unavailable services, changed session or an
unexpected foreground app. `--limit N` collects at most N additional frames for
an interrupted run. Originals, private session and accepted counts are retained
on failure; it never lowers checks to force a result. The guide does not
automatically approve a reference, physical association or threshold. With only
positive Pixel samples, false-match performance remains unknown.

## Explicit Pixel sRGB JPEG route

Real Pixel 9 Pro test JPEGs contain a 596-byte `sRGB IEC61966-2.1` ICC record.
Existing non-ICC decoder profiles continue to reject it. A **separate explicit
profile** `pillow-13.0.0/rgb-green-stored-exif-N-icc-HASH/1` pins that exact record
to SHA-256 `33c34ecfeb998459e0075ba26a0f1ea5042e20e2419c5962e495eba3208576f2`
and one stored EXIF orientation N. The non-identifying ICC metadata is shared
in `contracts/fixtures/prnu-decoder.json`; Python/Kotlin check identical vectors.
Other/missing ICC records are rejected. The numeric input remains the stored
RGB green channel: **no ICC transform, EXIF transpose or JPEG rewrite** occurs.
This is an explicit experimental decoder route, not a manufacturer credential.
Older references and decoder meanings are never migrated or re-estimated.

## Frozen method 1

- Even central crop, native scale, stored coordinates; dimensions 16..512.
  Prefer 256x256 for a first trial. Separate physical camera, mode and decoder
  identifiers. Unsupported switching, resizing or orientation is unavailable.
- Python JPEG adapter: Pillow 13.0.0, RGB JPEG only, green channel, orientation
  absent/1, no ICC conversion, explicit source dimensions, maximum 32 Mi pixels.
  No transpose, resize or metadata rewrite. Its decoder ID is
  `pillow-13.0.0/rgb-green-stored/1`. Package pin does not identify the entire
  build: record Pillow codec feature/build details in the deployment policy.
- Java operates on decoded green arrays. There is deliberately no assertion
  that Android Bitmap decoding equals Pillow. A native Android decoder route
  needs its own ID and calibration, plus shared decoded-image fixtures.
- Binary64 arithmetic throughout, row-major accumulation. One-level orthonormal
  Haar 2x2 blocks, details soft-thresholded at 3.0 intensity units, exact inverse;
  W = I - denoised(I). This is real denoising, not a placeholder random score.
  Python floating-point reductions use explicit ordered addition, matching the
  intended Java order rather than depending on built-in `sum` behaviour across
  Python versions. The audit correction awaits revalidation on the test branch;
  existing published reference bytes/refHash must not be regenerated.
  The language change is documented in
  [Python 3.12 release notes](https://docs.python.org/3/whatsnew/3.12.html#other-language-changes).
- Mask original intensities outside inclusive [8,247]. Subtract each valid
  row's mean, then each valid column's mean, once; invalid values become zero.
- Streaming reference: sum(I*W)/sum(I^2), valid support at least max(2,ceil(.8*N))
  frames; at least 75% valid pixels per accepted frame/reference. Repeat the
  same row/column suppression on K. Require distinct original-image SHA-256s;
  reject any query included in the reference. Two is an estimator lower bound,
  not a suggested enrollment size: begin physical trials with 30 accepted frames.
- Compare W to I*K. For shifts dy,dx in [-3,3], use the common interior
  [3,h-3)x[3,w-3), no wrap. Require query/reference masks at both positions.
  Every shift needs 75% overlap. Subtract overlap means and normalise correlation.
  Expected alignment is fixed at zero; never choose the largest peak.
- PCE = sign(r0)*r0^2/mean(r^2), off-peak samples excluding the central 3x3
  window. Zero energy/insufficient area gives no score. Quality constants here
  are trial settings, never camera-accuracy thresholds.

## Reference and calibration records

Reference schema `fingerprinto/reference/1` is canonical sorted-key compact
UTF-8 JSON containing method, geometry, enrollment file digests, base64
little-endian binary64 pattern and byte mask (0/1). Exact record bytes define
refHash. Java's `Reference.record` emits the same canonical schema, with preserved
frame order, binary64 byte representation and mask; its actual emitted record
is imported/validated by Python in the JVM check. Numeric estimates across
runtimes are compared within tolerance, not assumed bit-identical: hash the
actual published record, never a re-estimated replacement.
Publish without overwriting via `save_reference`; keep its directory
access controlled and distribute it only to authorised verifiers. A matching
hash proves byte identity, not trusted sensor provenance. The authority's
association must bind this hash, installation, physical camera and geometry.

`evaluate_samples` checks disjoint enrollment/development/final image IDs and
reports per-device/mode false-match, false-reject and inconclusive counts and
rates. The caller must provide correctly labelled same-route captures, including
other physical units of the same model. No score is a probability. Counts alone
do not establish an error bound, nor does zero observed errors prove zero risk.
Acceptance/rejection regions must be approved independently in verifier-owned
policy. Evaluation JSON explicitly does not authorise decisions.

## What is actually tested

Synthetic planted-pattern/noise arrays check reproducibility, independent-query
scoring, masking, constant/nonfinite inputs, duplicate/query reuse, record
round trips and data-set separation. Python and Java compare reference values
and scores numerically at relative/absolute tolerance 1e-10. This is numerical
interoperability, not a real-device attribution experiment. JPEG decoding is
tested separately and optionally requires the `prnu` extra. Actual Pixel capture,
private upload and a labelled automatic demo are exercised separately from
these synthetic vectors. Same-scene demo samples do not close the independent
empirical stop/go gate.

Decoder API: [Pillow Image documentation](https://pillow.readthedocs.io/en/stable/reference/Image.html).
The general estimator/detection motivation is documented in
[Chen, Fridrich and Goljan](https://ws2.binghamton.edu/fridrich/Research/EI6505-25.pdf);
the concrete trial settings above are ours and are not attributed to that paper.
