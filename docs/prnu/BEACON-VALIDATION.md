<!-- AGENT CONTEXT: Execution provenance for the beacon experiment only. These synthetic checks do not validate physical presence, real camera decoding or all inherited code. -->

# Beacon execution record

Date: 21 November 2026. Branch: `leomaster`. Tool assistance: OpenAI Codex;
no independent reviewer/device results are claimed.

Tested code commit: `8fb3b3ca2902e98ff1113463e220252caeb843de`.
Subsequent documentation commits do not change the tested executable code.
Environment: Windows, project `.venv-fingerprinto`, Python 3.11.9,
cryptography 46.0.7, Pillow 13.0.0. The installed versions were inspected.

Executed after the code commit, all passed:

| Command (project Python interpreter) | Result |
|---|---|
| `python -B -m unittest discover -s tests/fingerprinto -p test_beacon.py` | 10 tests |
| `python -B -m unittest discover -s tests/fingerprinto -p test_receipt_v3.py` | 3 tests |
| `python -B -m unittest discover -s tests/fingerprinto -p test_evidence_v3.py` | 6 tests |

Beacon checks cover the golden payload/root, issuance, token/image verification,
CLI round trip, non-overwriting PNG creation, malformed framing/CBOR, signature
and signed-field alteration, unknown keys, wrong installation/session, stale or
future slot, supplied anchor-bound inconsistency, blank/unsupported images,
optical payload tampering and intentionally undetected splicing/same-session reuse.
Synthetic aligned JPEGs at qualities 95/70/40 decode; a strong Gaussian blur
does not pass. This is not a robustness measurement on a physical display.

The first development test attempt had a fixture-construction error (trying to
encode a CBOR boolean with the restricted encoder). The malformed input was
changed to literal invalid CBOR bytes; the final recorded runs above passed.
No implementation failure is hidden by reporting only those final results.

`python -B -m fingerprinto.beacon --help` and Git whitespace checks also passed.
The published fixture uses a publicly known test scalar and deterministic ECDSA;
private production keys or personal images were not added.

Not executed: complete historical test suites, Android/JVM builds, SDK installs,
real beacon/camera tests, perspective/moiré/reflection campaigns, real-chain OTS
verification, deployment trust/revocation or one-use session services. This module
does not implement those services. The standalone token report does not alter
any v3 origin level. See [profile and attack matrix](BEACON-EXPERIMENT.md).
