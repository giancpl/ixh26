# FingerprinTO

**A signed, verifiable evidence format for photo origin, with explicit levels of assurance.**

FingerprinTO defines a signed receipt binding a photo's exact bytes to an installation identity, the recorded outcomes of two independent origin checks (manufacturer capture credential and PRNU sensor compatibility), and a timestamp commitment. Installation enrollment and origin findings are independently verified; anchoring has its own pending/verified status. It is designed to say precisely *what is and is not established* for a given photo, rather than to output a single "authentic" bit.

> **Scope statement.** This project does **not** provide a cryptographic proof that a photo was acquired by a given physical sensor on an arbitrary device. We argue in [§2](#2-the-impossibility-boundary) that no scheme can, without a trusted observation point inside the capture path. The implemented core provides a byte-level hash/signature construction, a reducer that keeps distinct evidence classes distinct, and an explicitly statistical PRNU experiment. This is a prototype, not a proved end-to-end camera-origin system. See [§9](#9-status-what-exists-and-what-does-not) for what is implemented and what is not.

*Challenge context:* IXH26 Bari, crypto track (photo-origin validation, generalisable across devices, with decentralised anchoring).

---

## Contents

1. [Problem and claims](#1-problem-and-claims)
2. [The impossibility boundary](#2-the-impossibility-boundary)
3. [Threat model](#3-threat-model)
4. [Construction](#4-construction)
5. [Evidence levels and the shared reducer](#5-evidence-levels-and-the-shared-reducer)
6. [The PRNU layer](#6-the-prnu-layer)
7. [Trust, enrollment and anchoring](#7-trust-enrollment-and-anchoring)
8. [Hybrid post-quantum experiment](#8-hybrid-post-quantum-experiment-v2)
9. [Status](#9-status-what-exists-and-what-does-not)
10. [Test vectors and validation scope](#10-test-vectors-and-validation-scope)
11. [Repository and usage](#11-repository-and-usage)
12. [Disclosure](#12-disclosure)

---

## 1. Problem and claims

We separate five properties that are routinely conflated:

| # | Property | Established by | Strength |
|---|---|---|---|
| P1 | **Byte integrity**: this file is the one that was signed | SHA-256 binding in `F_P`, signature over `R` | Cryptographic, conditional on signature unforgeability, hash collision resistance and correct key handling |
| P2 | **Key possession / installation identity** | Android Keystore attestation + proof of possession | Cryptographic, *conditional on* the hardware-attestation trust chain |
| P3 | **Capture credential** (vendor says "I captured this") | Trusted capture credential with signature, accepted capture semantics and hard asset binding; a generic provenance signature is insufficient | Cryptographic, *conditional on* the vendor's capture path and issuer policy |
| P4 | **Sensor compatibility** (noise residual matches an enrolled reference) | PRNU correlation | **Statistical**, copyable, not a proof |
| P5 | **Commitment inclusion in a timestamped history** | Verified Bitcoin/OpenTimestamps proof | Conditional on Bitcoin consensus and timestamp interpretation; not a precise wall-clock or shutter-time guarantee |

Not claimed: truth of the depicted scene (a photograph of a screen is an authentic capture of synthetic content), shutter time, or detection of AI-generated content.

## 2. The impossibility boundary

**Claim (model-level, not a universal impossibility theorem).** Let the client (OS + app) be adversarial and let the verifier observe only `(J, receipt, bundle)`. Suppose the client can invoke the enrolled signing key `sk_I`, without needing to extract it from hardware. If no independently authenticated capture observation binds `J` to the sensor, two histories can present the same transcript: a physical capture of `J`, and injection of those same bytes before signing. A verifier observing only that transcript cannot distinguish the histories. The installation signature authenticates a statement about chosen bytes, not the process that produced them. This does **not** let an attacker forge a manufacturer credential or pass every independent origin check.

Consequently, any strong origin guarantee needs an observation point the adversary does not control, located **before** the bytes become attacker-chosen:

- a vendor-attested capture path with an authenticated binding from capture to asset; or
- another trusted measurement that binds the physical acquisition to the verified bytes.

Fresh light/flash or multi-sensor challenges may raise the cost of forgery, but
without trusted observation they remain experimental evidence, not a substitute
cryptographic guarantee.

Neither a blockchain anchor nor additional software signatures can substitute: an anchor commits to whatever was submitted, including a false initial statement. A quorum of registrars does not make a false enrollment true.

**Design consequence.** The format is device-agnostic, the *assurance level* is not. A capture with a supported, independently validated credential may reach the manufacturer level; other captures may support statistical PRNU evidence or no origin finding. Capabilities depend on the particular lens, mode and capture route, not just the device model. The receipt records evidence; the verifier derives its own level.

## 3. Threat model

| Adversary | Can do | What stops it | What does not |
|---|---|---|---|
| Post-signature editor | Modify `J`, report, policy, `E`, or committed metadata | Digest/Merkle/signature reconstruction fails | – |
| Compromised app/OS, legitimate enrolled key | Sign any file; claim any PRNU outcome | Verifier re-evaluates both checks with its own trusted policy and reference; vendor credential binds to bytes | **Pre-signature injection of a file that passes the checks** (see §2) |
| Phone A captures, phone B registers | B signs A's genuine photo | A trusted capture-to-device association, or PRNU against B's trusted reference; neither is supplied by B's signature | Signature alone is valid |
| PRNU forger | Estimate a fingerprint from public images, transfer it to another image | No demonstrated defence here; spoofing countermeasures and adversarial evaluation remain research work | An adaptive attacker may defeat PRNU entirely |
| Compromised authority (enrollment, trust store, issuer list) | Enroll false devices, accept bad issuers | No protection against an authority already trusted for that assertion; independent provisioning and governance are required | Quorum/ledger does not correct a false attestation |
| Replay | Reuse a challenge or old receipt | One-use challenges at enrollment; capture-session freshness requires verifier-side challenge tracking | A challenge embedded *after* capture does not prove capture freshness |
| Server outage / vendor disappears | Remove trust data | Retained signed authority history exists; a portable long-term trust archive remains proposed | Missing evidence yields *indeterminate*, never *pass* |

## 4. Construction

### 4.1 Notation

`D(label, x₁, …, xₙ) = SHA-256(CBOR([label, x₁, …, xₙ]))` with RFC 8949 §4.2.1 deterministic encoding (definite lengths, shortest integers; no tags, floats, or indefinite items; trailing bytes rejected). `Node(a,b) = D("node", a, b)`. Labels serve as domain separation between leaves, nodes, photo fingerprints and the root.

### 4.2 Installation identity

```
ID_I = SHA256(pk_I)                                   pk_I: DER SPKI, P-256 (91 bytes)
F_I  = D("install", ID_I, packageName, versionCode, signerDigest)
```

The Android key-management implementation requires a non-exportable, hardware-backed Keystore key (TEE/StrongBox per policy); execution on a real phone is not yet validated. `F_I` is an app-declared identity at receipt time; neither `versionCode` nor `signerDigest` is a hash of the APK contents.

### 4.3 Device and session subtree

Device manufacturer/model are signed client declarations, not authenticated sensor identity.
Five leaves `Lᵢ = D("field", name, value)`: `ID_I`, `manufacturer`, `model`, `localTime` (ms, device clock, untrusted), `sessionChallenge` (32 bytes or CBOR `null`, leaf kept when absent).

```
F_D = Node(Node(Node(L0, L1), Node(L2, L3)), L4)       # unpaired node promoted unchanged
```

### 4.4 Photo fingerprint and root (profile v3)

```
E   = [placement, detachedHash, manufacturerResult, prnuResult,
       refHash, policyHash, reportHash]
F_P = D("photo/evidence-v3", F_I, SHA256(J), E)
T   = Node(Node(F_I, F_P), F_D)
R   = D("fingerprinto/evidence-v3", T)
σ   = ECDSA-P256-SHA256(sk_I, R)                       # DER, persisted verbatim
C   = D("receipt", R, σ)                               # 32-byte public commitment
```

- `placement`: 0 embedded / 1 detached / 2 absent credential. `detachedHash = SHA256(A)` only for 1.
- Result codes (both checks): 0 unavailable, 1 inconclusive, 2 pass, 3 fail. A failed attempt yields a diagnostic record, never an acceptable receipt.
- `policyHash`, `reportHash`: SHA-256 of the exact bytes of two bundled UTF-8 JSON files (≤ 1 MiB each, no duplicate keys). Bytes are persisted once and reused on every export/retry.
- `refHash`: digest of the serialized PRNU reference record when one was used.
- The displayed assurance level is **derived by the verifier**, never read from a signed label.

The wire representation is a fixed 12-item CBOR array:

```text
receipt.cbor = [3, pk_I, chain, packageName, versionCode, signerDigest,
                manufacturer, model, localTime, sessionChallenge, E, sigma]
```

The certificate chain is validation material, not a substitute for a trusted
root: enrollment must bind its attested public key to `pk_I`. The tree commits
to the installation identity and declarations, not the chain bytes themselves.
See the [wire/attachment specification](docs/crypto/EVIDENCE-V3-FORMATS.md).

Profile **v1** (legacy, mandatory manufacturer gate) uses `F_P = D("photo", F_I, SHA256(J)[, SHA256(A)])` and `R = D("fingerprinto/v1", T)`. v1 and v3 are separated by wire version and by label; there is no cross-profile fallback, and signatures over bare `T` are rejected.

### 4.5 Engineering notes that affect security

- **Signature malleability and `C`.** ECDSA signatures are not unique. `C` commits to the *specific* DER bytes returned by the signer; re-encoding, low-`s` normalisation or re-signing changes `C`. A durably stored receipt is reused byte-for-byte on retry. A crash before journal commit can cause another signing invocation, but transport must occur only after durable commit; this is not an exactly-once guarantee for hardware signing.
- **`C` is a commitment, not a hiding one.** It is a hash of public-ish structured data. Do not assume unlinkability or anonymity: receipts under one key are linkable by `ID_I`.
- **Strict parsing** is part of the spec: exact arity and types, canonical DER for `σ` (8–72 bytes, minimal positive INTEGERs in range), on-curve validation of `pk_I`, size limits enforced before allocation (receipt ≤ 256 KiB, `J` ≤ 128 MiB, bundle ≤ 160 MiB). Size failures are *profile rejections*, reported separately from cryptographic mismatches.

## 5. Evidence levels and the shared reducer

Both origin checks assess the same immutable `J`, even when the first passes; an unavailable validator or reference yields an explicit unavailable/inconclusive finding. Python orchestration and Java primitives implement the same reducer; wiring it into the Android capture flow remains unfinished. After mandatory checks are resolved, the completed outcomes reduce as follows:

| Condition | Level |
|---|---|
| Definitive origin failure, conflicting evidence, or invalid signature/binding/enrollment | **Failed** (blocks issuance; blocks acceptance on import) |
| Manufacturer credential passes trust and hard binding to `J` | **Manufacturer-verified** |
| Manufacturer absent/indeterminate **and** PRNU passes against a trusted reference | **PRNU-verified** |
| Neither passes, neither definitively fails | **No verification** |

Pending mandatory checks block issuance/acceptance; a pending OTS calendar promise is a separate anchor state and does not by itself invalidate origin findings. An unresolved claimed anchor keeps assessment pending. Even the no-origin-verification level requires valid enrollment, byte binding and installation signature.

Properties of the reducer: missing evidence never becomes a pass; an invalid recognised credential is never retried in "absent" mode; a PRNU *mismatch* blocks even when a manufacturer credential passes; a personal "trust / don't trust" choice is stored locally against `C` and can neither upgrade a level nor change the receipt. An origin failure concerns the claim, not the scene. These checks bind and evaluate evidence; they do not supply a formal reduction for sensor-origin soundness.

## 6. The PRNU layer

PRNU gives **statistical compatibility with an enrolled reference**; it is not a signature of the sensor and not a cryptographic guarantee.

**Reference estimation (per physical lens and mode).** From frames with aligned intensity `Iᵢ`, residual `Wᵢ = Iᵢ − denoise(Iᵢ)` and validity mask `mᵢ`:

```
K[p] = Σᵢ mᵢ[p]·Iᵢ[p]·Wᵢ[p]  /  Σᵢ mᵢ[p]·Iᵢ[p]²
```

Poorly supported or zero-denominator pixels are masked. Row/column artefacts are suppressed by a pinned preprocessing configuration. The reference record (K, mask, geometry/mode/decoder identifiers, method version and enrollment-image digests) is serialised once and hashed as `refHash`. Physical-camera provenance and approved calibration regions are carried by the separately authenticated reference association and verifier policy. Decoder, colour conversion, denoiser and its parameters, normalisation, numeric precision and mask rules are all versioned; changing any of them requires recalibration.

**Detection statistic.** For a query, compare `W` with the predicted component `I·K` over valid overlapping pixels. With the expected shift fixed at zero, let `r₀` be the normalised correlation at that shift:

```
SPCE = sign(r₀) · r₀²  /  mean( r(s)² : s outside the exclusion window )
```

Zero off-peak energy or insufficient valid area gives *inconclusive*.

**Decision regions.** Match (≥ calibrated acceptance threshold), Mismatch (inside a *separately validated* rejection region), Inconclusive otherwise. Match and mismatch are enabled independently, only where measured error rates meet an explicit deployment policy; until then experimental scores are displayed as inconclusive. No universal threshold is shipped, and scores are never converted into a probability of truth. The implemented experimental method uses a fixed native-scale green-channel crop, a one-level Haar denoiser and versioned masking/geometry rules. Python has a pinned JPEG decoder route; Java currently consumes decoded arrays, not an equivalent Android JPEG pipeline. See the [PRNU experimental profile](docs/prnu/PRNU-EXPERIMENT.md).

**Known limits.** PRNU can be estimated from public images and transferred; recompression degrades it; it does not date a capture; thresholds measured on innocent images say nothing about an adaptive adversary. Triangle-test style countermeasures have their own assumptions and are not treated as a complete answer.

## 7. Trust, enrollment and anchoring

**Enrollment.** The key is generated in the Keystore with a fresh verifier-issued 32-byte attestation challenge. The verifier validates the chain to configured Android attestation roots (with revocation), binds the attested leaf's SPKI to the receipt key, checks security level, key purpose/digest, boot state and the attested application id (package, signer digests). Attestation describes the app **at key generation**; later receipts carry app declarations that remain unattested. Key loss requires a new key and `ID_I`; a replacement key is never used under an old id.

**Residual authorities (honest accounting).** Bitcoin/OTS decentralises *publication evidence*. It does **not** decentralise: the attestation roots, the configured issuer allow-list and capture-semantics policy, the enrollment decision, the association camera ↔ reference, or PRNU thresholds. These sit in a configured authority (`TrustStore`) outside the bundle. The code supplies signed authority records and sequence/status checks; deployment governance for enrollment, rotation, revocation, disputed provisioning and unavailable authorities remains an open item (§9).

**Anchoring.** For the Bitcoin/OTS backend we timestamp a file `commitment.bin` containing exactly the 32 raw bytes of `C`; the OTS start digest must equal `SHA256(C)` (not hex, not a newline, not `C` itself as a precomputed digest). A calendar promise alone is *pending*; only a confirmed Bitcoin attestation on the expected network is *verified*. Anchor status is reported separately from origin evidence: an absent/pending anchor leaves origin findings interpretable, a *false* claimed anchor fails the bundle. An XRPL memo convention is specified in the docs but XRPL is **out of scope** for the current branch.

**Verification and time.** The verifier recomputes every digest, reconstructs `R`, verifies `σ`, and independently evaluates both origin checks against *its own* trusted policy and enrollment association. A supplied policy is evidence of what the signer used, never a trust anchor, and an app report alone never yields a PRNU pass. Current lookup and retained signed authority history are distinct. Full historical verification is a proposed extension: archive the relevant issuer chains, manifests, trust policies and authenticated status evidence, then anchor their byte identities. Past trust cannot be reconstructed from device time or a ledger timestamp alone; missing historical evidence must not be treated as a historical pass. Anchoring a certificate does not establish issuer trust or absence of key compromise. `localTime` is a device-clock claim; the anchor dates the commitment, not the exposure; a challenge embedded after capture proves a response to the challenge, not freshness of the capture.

## 8. Hybrid post-quantum experiment (v2)

A separate wire version (v2) combines **ML-DSA-65 AND Ed25519** (both must verify). Key protection boundary: AES-GCM hardware-protected wrapping of the software keys at rest, with binding to the installation's P-256 identity; the hybrid keys are in RAM during signing and are **not** hardware-native. RSA is deliberately excluded. A hybrid signature does not make the whole trust chain post-quantum: enrollment attestation, authorities and the surrounding protocols remain classical dependencies. v1/v2/v3 are never interchangeable.

## 9. Status: what exists and what does not

**Specified and implemented in the module (per project documentation):** deterministic CBOR codecs and commitments for v1/v3; P-256 signing; attestation-intake restricted to EC issuers with proof of possession; authority records; receipt journal; bundle/CLI; Bitcoin/OTS adapters; shared reducer; local trust preferences; Python/Java PRNU experiment; hybrid v2 experiment.

**External optical-token extension (`leomaster`):** P-256 beacon tokens bound to
verifier-owned session challenges and time slots, a visible grid codec and an
independent CLI are implemented. Ten beacon tests and nine targeted v3
regressions passed at code commit `8fb3b3c`; see the
[execution record](docs/prnu/BEACON-VALIDATION.md). The result validates a software
token, not physical presence: splicing, relay and same-session reuse remain
undetected, and reports always keep `physicalOriginVerified=false`. No v3 wire,
origin level or automatic report/issuance integration was added. The optical
baseline lacks localisation, error correction and perspective correction;
real display/camera tests, trusted clock/release, key governance and one-use
session services remain external or unfinished. See the
[profile and attack matrix](docs/prnu/BEACON-EXPERIMENT.md).

**Not complete:**

- a real manufacturer-credential validator (currently a callback; the default only recognises candidates and placement conflicts, it does not authenticate them);
- the Android decode/calibration path and the capture → both checks → v3 signature wiring (the current Android export is an unsigned diagnostic);
- any measured results on a real sensor, on native capture, or against forged PRNU.

**Unverified / pending:** the latest hardening commit (`84f3fae`: pre-signature preflight, ordered PRNU sums, atomic decision retention, recovery, idempotent publication, signed history) post-dates the historical test runs and has not been executed on the validation branch; earlier green counts must not be attributed to it. Python 3.11/3.12 and JVM PRNU-sum ordering equivalence is likewise unverified.

**Explicit non-claims:** no demonstrated sensor-level attestation on the target Android capture route; the Keystore key is not a sensor key; no result on adaptive PRNU forgery; no Android build/device test; no certified or qualified-service status.

## 10. Test vectors and validation scope

The repository contains both legacy and current synthetic fixtures:

| Profile | Fixtures | What they exercise |
|---|---|---|
| v1 | [Four legacy vectors](tests/fingerprinto/fixtures/v1.json) and the [specification appendix](docs/crypto/crypto.md#historical-appendix--p-256-v1-specification-and-fixtures) | Embedded/detached credentials and absent/present session challenge; intermediate digests, DER signature, commitment and OTS start digest |
| v3 | [Four signed vectors](tests/fingerprinto/fixtures/v3.json), [Python checks](tests/fingerprinto/test_receipt_v3.py) and [JVM interoperability checks](android/tests/check_receipt_v3.py) | v3 byte encoding, roots, attachment hashes, claimed result combinations, signatures and transport |

These fixtures are synthetic: their certificates, credentials and claimed pass
codes do not establish trusted enrollment, sensor attribution or a real anchor.
The inherited `sigma` signs the 32 bytes of `R` through ECDSA with SHA-256;
it is not a signature over bare `T` or a prehashed-ECDSA API invocation.

Historical test results and the later, unexecuted hardening changes are recorded
in the [review handoff](docs/implementation/CRYPTO-REVIEW-HANDOFF.md#validation-provenance-and-reviewer-priorities).
This README update involved source and documentation checks only; it did not
rerun those tests, recompute the fixtures independently or build the Android app.

## 11. Repository and usage

| Area | Entry point |
|---|---|
| Protocol and byte contracts | [Cryptographic specification](docs/crypto/crypto.md), [v3 formats](docs/crypto/EVIDENCE-V3-FORMATS.md) |
| Receipt and independent verifier | [`fingerprinto/`](fingerprinto/), especially [`receipt_v3.py`](fingerprinto/receipt_v3.py) and [`workflow_v3.py`](fingerprinto/workflow_v3.py) |
| Hardware key lifecycle and Java primitives | [Android module guide](android/README.md) |
| Trust records and enrollment | [Trust/workflow](docs/crypto/V3-TRUST-AND-WORKFLOW.md), [restricted attestation intake](docs/crypto/enrollment-verifier.md) |
| PRNU method and evaluation gate | [Experimental profile](docs/prnu/PRNU-EXPERIMENT.md) |
| External optical token experiment | [Beacon profile and limitations](docs/prnu/BEACON-EXPERIMENT.md): signed session token, not proof of physical presence; no v3 level upgrade |
| Hash/signature and PRNU diagrams | [Flow diagrams and PNGs](docs/diagrams/crypto-flow.md) |
| Implementation coverage and validation tasks | [Audit](docs/implementation/CRYPTO-MODULE-AUDIT.md), [roadmap](docs/implementation/CRYPTO-V3-ROADMAP.md), [experiment runbook](docs/implementation/V3-EXPERIMENT-RUNBOOK.md) |
| Bitcoin/OTS subsystem | [Installation, CLI, regtest and RPC guide](docs/implementation/BITCOIN-SETTLEMENT.md) |
| Pitch material | [Spoken pitch](docs/pitch/pitch.md), [slide content](docs/pitch/slides.md), [Lagrange PDF](docs/pitch/fingerprinto-pitch.pdf), [Overleaf sources](docs/pitch/fingerprinto-pitch-overleaf.zip) |

The crypto module and the timestamping service are separate components: the
latter timestamps opaque bytes and does not authenticate photo provenance.
The receipt module prepares `C`; settlement handles the OTS proof lifecycle.
After installation following the linked guides, CLI entry points are:

```bash
python -m fingerprinto v3 --help
python -m settlement --help
```

Use the runbook for offline verification inputs and the settlement guide for
networked operations. No Android build is needed to inspect the Python byte
construction. Regulatory relevance is discussed in
[regulations](docs/crypto/regulations.md); this is not a claim of certification
or a qualified timestamp service.

## 12. Disclosure

This README adapts the draft supplied by the team, written with Claude
(Anthropic), and was revised with OpenAI Codex against repository sources and
documentation. The specification, handoff and analysis documents also used OpenAI Codex
assistance. Team-wide disclosure of tools, assisted components and
human review actually performed remains to be completed against the event's
requirements.
