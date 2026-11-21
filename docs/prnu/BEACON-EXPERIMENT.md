<!-- AGENT CONTEXT: Implemented external-beacon experiment, not sensor attestation. Read the limitations beside the result semantics. Do not upgrade v3 origin levels from token validity or treat a software-decoded optical code as proof of physical presence. -->

# External optical beacon: experimental profile 1

This implements the separable, defensible part of the [watermark proposal](watermark.md):
an external device emits a signed, session-bound visible code. The verifier
decodes it from the submitted original bytes, checks a separately pinned beacon
key and checks the session/slot. It does **not** insert a watermark in the photo.

## Correction to the proposal

A valid code does not prove proximity to the beacon or entry through the lens.
Splicing, real-time relay and recorded-code reuse within the accepted session/time
window are **not detected**. A pre-generated scene without the fresh token fails
to supply evidence, but pasting a fresh token onto that same scene passes token
verification. The tests explicitly demonstrate this. No cryptographic origin
guarantee or time-of-exposure bound follows.

Under an honest beacon clock, no early release, an uncompromised key and an
unpredictable verifier challenge, token-bearing content cannot be assembled
before token release. This is a conditional statement about those bytes, not
the age of the scene or the physical capture. An independently justified anchor
upper bound can delimit that content window; Bitcoin block timestamps are not
silently treated as exact UTC upper bounds. The module does not validate OTS.

## Transcript

`D` is the existing SHA-256 of restricted deterministic CBOR, with new domains:

```text
beaconId = SHA256(pk_B)                    # strict P-256 DER SPKI
binding = D("beacon-session/1", challenge, installationId)
slot = floor(beacon_time / 10)             # Unix seconds; external clock
payload = ["fingerprinto/beacon/1", beaconId, slot, binding]
R_B = D("beacon-code/1", payload)
packet = CBOR([payload, ECDSA-P256-SHA256(sk_B, R_B)])
```

The signing API hashes the 32-byte root once, as with installation signatures.
Canonical DER is required; both high/low s are accepted. No RSA, client timestamp
hash or public-key hash is substituted for the fresh challenge. The verifier
must generate 32 unpredictable bytes (e.g. `secrets.token_bytes(32)`), retain them
with the expected installation and issue time, and communicate that session to
the external beacon **before** capture. This extra channel is an explicit cost.
This module accepts the challenge as an input; it neither issues sessions nor
provides an atomic one-use/session registry. Same-session reuse is possible.

The signing key belongs to the external beacon, not the photographing phone.
Trust is a verifier-owned mapping of beaconId to pinned SPKI. Caller governance
must check validity, revocation and provisioning before including a key. There
is no chain discovery, public key registry, ledger key registration or historical
revocation implementation here. Anyone controlling the beacon can sign future
slots: software cannot enforce honest release or a trustworthy clock.

## Optical encoding and boundaries

Decoder `fingerprinto/optical-grid64-pillow13/1` uses Pillow 13.0.0. It is a
deliberately small baseline rather than QR: no new barcode dependencies, exact
binary framing and reproducible negative tests, at the cost of **no error
correction, automatic localisation, orientation or perspective correction**.
It is not ready for handheld photography. QR with a pinned decoder and measured
physical robustness is a subsequent experiment, not a supported feature.

A 64x64 row-major monochrome grid encodes 512 bytes, MSB first: ASCII `FPB1`, a
two-byte big-endian length, at most 400 packet bytes and zero padding. Four white
cells surround each edge. The renderer uses 8 pixels/cell by default (576x576).
The verifier takes the whole tile or an operator-specified square ROI from the
original PNG/RGB JPEG. It samples cell centres, rejects intermediate luminance
80 < Y < 175 and reads dark values as 1. Misalignment/corruption can fail or yield
no detected code, never an origin pass. The quiet zone is framing space, not an
authenticated physical marker. Asset bytes are hashed, never rewritten.

Bounds: 16 MiB image bytes, 16 Mi pixels before load, ROI 288..2048 square pixels;
non-default orientation/ICC and unsupported colour modes are refused. No scaling
or colour-normalisation interoperability is asserted for native Android decoding.

## Report semantics and evidence classes

| Component | Class | What verification establishes |
|---|---|---|
| Signature, binding, slot and asset hash | S | Software token consistency under verifier-owned key/session/time inputs |
| Claimed beacon light in the scene | H, conditional and unverified | Physical emission would require a trusted external device; decoding pixels does not authenticate that path |
| Sensor origin / proximity / scene truth | Not established | No pass is issued for these properties |

`status=pass` means **signed token valid**, with `evidenceClass=S` and
`physicalOriginVerified=false`, not a third origin level. Unknown beacon,
signature/session tampering, future/stale slot or slot after the justified anchor
bound: fail. No recognised code: unavailable. Unsupported/corrupt image or low
contrast: inconclusive. Configuration errors are errors, not passes. The default
maximum age is 120 seconds from slot start; it is a trial setting, not a measured
security bound. `now` and any anchor bound are verifier inputs, never client claims.
An old photograph with a fresh pasted code remains undetected.

Every decoded report includes explicit splicing/relay/replay limitations. A
conditional content window is omitted unless the caller supplies a separately
justified upper bound. The issuer and decoder cannot prove physical presence.

## Offline CLI

Install `python -m pip install -e '.[beacon]'`. Use a separately provisioned
P-256 beacon PEM private key and verifier-pinned DER SPKI. Never deploy the
publicly known scalar-1 key used by the synthetic fixture.

```bash
# On the external beacon; arguments are supplied by the session/clock controller.
python -m fingerprinto.beacon issue --private-key beacon-private.pem --time 1000 \
  --challenge <64-hex-digits> --installation-id <64-hex-digits> --out code.png
# On the independent verifier; ROI is optional x y width height, including quiet zone.
python -m fingerprinto.beacon verify original.jpg --public-key pinned-beacon.der \
  --time 1005 --challenge <64-hex-digits> --installation-id <64-hex-digits> \
  --roi 100 50 576 576
```

Times above are synthetic examples, not production clock values. Output PNGs
are created exclusively to avoid overwriting evidence. Exit codes: 0 token
pass/code created; 2 unavailable/inconclusive; 3 token failure; 1 input/config error.

## Compatibility and attack matrix

Receipt v3, its seven-field E, version domains and reducer are unchanged. This
is a separate report and CLI. A caller may explicitly include it in the existing
report JSON *before* freezing/signing that report, but that integration is not
implemented here. A new origin level would need a justified new wire proposal;
we do not propose one because the token supplies no additional origin guarantee.

| Attack / test | Expected outcome |
|---|---|
| Scene without code | Unavailable; no optical evidence |
| Changed signed payload/slot/signature | Fail |
| Unregistered/replaced key | Fail |
| Wrong fresh session or installation | Fail |
| Future/stale slot | Fail |
| Slot after supplied independently verified anchor bound | Fail |
| Paste fresh code into pre-generated scene | Token pass; physical origin false |
| Relay / reuse same code within same session and age window | Token pass; not detected |
| Old recorded code outside age window | Fail; this does not prevent fresh relay |

## Work and validation record

Implemented with OpenAI Codex assistance on `leomaster`; the source proposal
was supplied with Claude assistance. No independent review or human/device
validation is inferred from that attribution. Synthetic vectors are in
`tests/fingerprinto/fixtures/beacon-v1.json`; expected outcomes are recorded
there and in this matrix. The golden packet uses deterministic ECDSA and a
public test key; production issuance uses the library signing API. Fixtures
prove neither enrollment nor physical acquisition.

Run from the repository root:

```bash
python -B -m unittest discover -s tests/fingerprinto -p test_beacon.py -v
python -B -m unittest discover -s tests/fingerprinto -p test_receipt_v3.py
python -B -m unittest discover -s tests/fingerprinto -p test_evidence_v3.py
```

The commit-specific validation record is in `BEACON-VALIDATION.md`. Synthetic
JPEG/blur checks are simulations, not optical/device measurements. Perspective,
moiré, rotation, scale changes and reflections are not evaluated. No Android
build/SDK, native capture wiring, backend, XRPL or physical beacon deployment
was performed. Future Android integration would hand unchanged capture bytes
to an independent verifier; it must never overlay the code on the phone and
call the result physical evidence.
