# PRNU experimental profile and stop/go evaluation

Method `fingerprinto/prnu-haar-green/1` is an intentionally small numeric
experiment, not an implementation of a published camera attribution accuracy
claim. Its simple wavelet may fail on the actual camera pipeline. Measure that
before investing in the calibration UI. Never fit the final test set.

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
refHash. Publish without overwriting via `save_reference`; keep its directory
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
tested separately and optionally requires the `prnu` extra. No physical camera
samples have been supplied; the empirical stop/go gate is still open.

Decoder API: [Pillow Image documentation](https://pillow.readthedocs.io/en/stable/reference/Image.html).
The general estimator/detection motivation is documented in
[Chen, Fridrich and Goljan](https://ws2.binghamton.edu/fridrich/Research/EI6505-25.pdf);
the concrete trial settings above are ours and are not attributed to that paper.
