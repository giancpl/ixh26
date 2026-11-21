# FingerprinTO | Android capture and source evidence

**Implementation audit:** [CRYPTO-MODULE-AUDIT.md](CRYPTO-MODULE-AUDIT.md)
maps this target to the source, corrections and still-missing implementation.
The full Android/manufacturer flow is not yet complete. Tests of the latest
audit changes are deferred to the separate validation branch. Regulatory
relevance and qualified-service limits are in [regulations.md](regulations.md).

**Current implementation target:** the P-256 evidence profile proposed below (wire version 3). The user-facing question is “What evidence supports this photo’s origin?” A manufacturer can authenticate a capture claim. Photo-response non-uniformity (PRNU) can support statistical attribution to an enrolled camera. Neither establishes the truth of the depicted event. Photographing a screen is still a physical capture.

## <u>Changed: receipt issuance and the three evidence levels</u>

Run **both** origin checks on the same immutable file, even when the manufacturer check succeeds. Keep the two results visible in Details. The following reducer is shared by the Android app and the independent verifier. Evaluate rows from top to bottom.

| Condition | Italian UI label | User action |
|---|---|---|
| A definitive origin check failure or conflicting evidence, or an invalid receipt signature, binding or enrollment | **Verifica fallita**; show the specific failed check | Block issuance on capture and block trust/acceptance on import. Keep diagnostic details. |
| Manufacturer capture evidence passes the configured trust and asset-binding checks | **Verifica del produttore** | Show issuer and exact authenticated claim. Offer personal trust choice. |
| Manufacturer evidence is absent or indeterminate, and PRNU passes against a trusted reference for the claimed camera | **Verifica PRNU** | Show sensor compatibility, reference provenance and calibration results. Offer personal trust choice. |
| Neither check passes and neither has definitively failed | **Nessuna verifica** | Explain the missing or inconclusive evidence. Offer personal trust choice. |

A signed receipt requires an enrolled installation key. Pending enrollment can display local findings but cannot produce an authenticated receipt. For imported receipts, pending signature/enrollment checks keep the decision pending. Treat a missing mandatory signed attachment or a digest mismatch as a failure. Anchor status is displayed separately: absent or pending anchoring leaves the origin evidence interpretable; a false claimed anchor is a failed check on the bundle and blocks acceptance. Provide “Mi fido” and “Non mi fido” only after all required checks complete. Persist a user's optional choice locally against C, separately from verification results. Never upgrade a level, modify the receipt, or let that choice override failure. An origin failure concerns this claim; it is not a declaration that the scene is fake.

**Manufacturer outcomes.** Set pass only for a trusted capture signer, valid signature, hard binding to J and accepted capture semantics. A broken signature/binding or a claim contradicted by the authenticated provenance fails. Absence, unsupported format, unavailable trust data and an issuer for which the verifier has no policy give no positive evidence; name the reason. A signer explicitly rejected by the configured policy fails. Never downgrade an invalid recognized credential by retrying in absent mode. Inspect the original asset for embedded evidence regardless of the claimed placement.

## <u>New: estimate the camera reference on Android</u>

A single query photo yields a noise residual. Estimating a reusable reference requires separate known-source images. Enroll each physical camera and capture mode independently. Keep front, wide and telephoto, image sizes, crops and processing modes separate. If a logical camera switches lenses or applies an unknown geometry, mark PRNU unavailable for that route.

1. In the app's calibration flow capture a series of evenly illuminated, slightly defocused, low-texture frames with the target lens. Move the phone slightly between frames, avoid clipping, and lock the tested geometry. For a prototype start with 30 accepted frames, then use held-out genuine captures to determine whether more are needed. Never add the photo under test to its own reference. Enrollment through an untrusted import proves no physical identity.
2. Use the same versioned decoder, channel, orientation and fixed sensor-coordinate region for enrollment and query. A practical initial JPEG profile uses the decoded green channel in stored orientation and a fixed central crop at native scale. Reject unsupported rotation/crop/size combinations. Pin decoder, color conversion, denoiser and parameters, normalization, numeric precision and mask rules in a versioned configuration. For an initial experiment use a wavelet denoiser; freeze its actual implementation and settings before calibrating thresholds. An algorithm or camera-pipeline change requires revalidation.
3. For each frame let I be its aligned intensity array and W = I − denoise(I) its noise residual. Let m be the per-pixel validity mask excluding dark, saturated and otherwise unusable samples. Accumulate numerator and denominator in floating point, element by element, one frame at a time on a worker thread. Estimate the multiplicative pattern as

       K[p] = sum_i(m_i[p] * I_i[p] * W_i[p]) / sum_i(m_i[p] * I_i[p]^2)

   Mark poorly supported or zero-denominator pixels invalid. Suppress shared row/column artifacts using the fixed preprocessing configuration. Store the resulting reference K, its mask, geometry, capture-mode identifier, algorithm version, enrollment provenance and calibration-policy identifier in a versioned reference record. Persist atomically only after calibration completes. Hash its exact serialized bytes as refHash. Keep reference material access-controlled; make it available to an authorized independent verifier.
4. The verifier must independently trust the association between refHash, installation and physical camera, for example through witnessed provisioning and a signed enrollment record. A user-created reference may support a local exploratory score. Display its provenance and withhold the verified PRNU level until enrollment satisfies the verifier's policy. Re-enroll after camera replacement or incompatible processing changes.

## <u>New: compare each capture and calibrate the decision</u>

From the finalized original J derive query intensity I and residual W with the same preprocessing. Compare W with the predicted sensor component I × K over valid overlapping pixels. For a fixed-geometry prototype use a normalized correlation surface over shifts with the expected aligned shift fixed at zero. Let r0 be the correlation there. Signed peak-to-correlation energy is sign(r0) × r0² divided by the mean squared off-peak correlations outside the configured exclusion window. Pin shift grid, mask handling, exclusion window and minimum valid area. A change to alignment search changes score statistics and requires recalibration. Zero off-peak energy or insufficient usable area gives an inconclusive result.

| PRNU result | Decision |
|---|---|
| Adequate quality, supported geometry, trusted reference and score at or above the calibrated acceptance threshold | Match |
| Adequate quality and score in a separately validated rejection region for the claimed sensor | Mismatch: block even if a manufacturer credential passes. Explain that the claimed sensor attribution failed statistically. |
| Intermediate score, poor quality, untrusted/missing reference, unsupported processing or unavailable calibration | Inconclusive or unavailable; show reason. A low score alone on an unsuitable image is not a failure. |

Use disjoint enrollment, threshold-development and final test images. Include ordinary scenes from the enrolled device and from other units **of the same model**, captured through the same app route. Estimate false-match, false-rejection and inconclusive rates on held-out images, broken down by device/mode; report sample counts. Do not ship a universal PCE threshold or translate a score into a probability of truth. Enable pass and mismatch independently only where their error rates meet a documented deployment policy. Until then display experimental scores as inconclusive. PRNU can be copied and does not date a capture. [PRNU estimation](https://ws2.binghamton.edu/fridrich/Research/EI6505-25.pdf) · [Brightness and PCE](https://doi.org/10.1111/1556-4029.15673) · [Smartphone camera processing](https://iris.polito.it/retrieve/d00f5446-2562-479a-b723-1e432511eed0/bhat_ICIP2022_OA.pdf)

## <u>Changed: bind the evidence to the signed receipt</u>

Use a fresh P-256 profile because the previous receipt assumed a credential. Reserve wire version 3 for this proposal after checking the repo's version registry. The separately specified ML-DSA-65 + Ed25519 experiment retains its own key-protection boundary and wire version 2 ([hybrid-v2.md](hybrid-v2.md)). No cross-profile fallback. The appendix specifies the inherited deterministic CBOR, D, Node, installation enrollment, F_I, F_D, signature bytes, limits and anchor semantics.

For v3, replace the previous photo branch and root with the following. Codes and digest fields below are CBOR integers and raw byte strings respectively. The literal null denotes CBOR null.

```text
E = [placement, detachedHash, manufacturerResult, prnuResult,
     refHash, policyHash, reportHash]
F_P = D("photo/evidence-v3", F_I, SHA256(J), E)
T = Node(Node(F_I, F_P), F_D)
R = D("fingerprinto/evidence-v3", T)
sigma = Sign(sk_I, R)
C = D("receipt", R, sigma)
receipt.cbor = [3, pk_I, chain, packageName, versionCode, signerDigest,
                manufacturer, model, localTime, sessionChallenge, E, sigma]
```

- placement: 0 embedded, 1 detached, 2 absent. detachedHash is SHA256(A) only for 1, otherwise null. Enforce the selected placement and supply exact detached bytes when applicable. Refuse ambiguous evidence selection.
- Both result codes: 0 unavailable/absent, 1 inconclusive, 2 pass, 3 fail. A failed attempt may generate a diagnostic record but cannot issue an acceptable receipt. The report carries specific reason codes. The displayed level is derived, never trusted from a signed label.
- refHash is the 32-byte reference digest whenever a reference was used, otherwise null. policyHash and reportHash are SHA-256 of the exact supplied policy and diagnostic report files. Bundle those two files. For interoperability use UTF-8 JSON without duplicate keys, persisted once and hashed as raw bytes. Preserve their original bytes across export and retries. The report records each check's status and reason, capture mode/geometry, method version, reference and policy identifiers, quality measurements, score and thresholds when available. Serialize finite scores with round-trip precision; use null when unavailable. Bind the exact scoring configuration and thresholds through the policy. Reject report/receipt disagreement.
- The independent verifier checks digests, reconstructs R, verifies the signature and independently evaluates both checks using its own trusted policy and enrollment association. A supplied policy is evidence of what the signer used, never a trust anchor. If the external reference cannot be retrieved, show the signed claim separately and mark the verifier's PRNU result unavailable. Never display a PRNU pass based solely on the app's report. Record evaluation time and distinguish subsequent policy changes from corruption of the original bundle.
- Apply the inherited bounds to v3 with the new seven-item E array. All non-null hashes are exactly 32 bytes. Limit each JSON attachment to 1 MiB and count both within the aggregate limit. Check attachment lengths and digests before parsing. Preserve the original J, the camera credential when present, and the signed receipt. Keep the existing commitment and backend-specific OTS/XRPL conventions.

## <u>Codex implementation delta and acceptance tests</u>

1. Find the real Android capture completion callback, persistence layer, verifier, upload/anchor queue and detail screen. Snapshot one finalized J. Replace the previous unconditional stop on **missing** manufacturer evidence with the reducer above. Keep invalid evidence blocking. Run manufacturer and PRNU checks independently; a successful first check must never skip the second.
2. Add a calibration screen per physical lens/mode, quality feedback, a cancellable background estimator, reference storage and the independent enrollment association. Implement the scorer with the same versioned algorithm on Android and the verifier. Do not ship a placeholder matcher or assign match from an EXIF camera identifier.
3. Add v3 encode/decode and strict dispatch, preserving v1 stored records, its four legacy vectors and the separate hybrid experiment. Export the evidence files, independently recompute reports where inputs are available, and update persistence and the upload queue to reuse the same receipt on retry.
4. Implement the three labels, separate failed/pending states, detailed reasons and the user's local decision. For each pair of manufacturer/PRNU outcomes test priority, including pass/pass, pass/mismatch, invalid/match, absent/match and absent/inconclusive. Test that failure cannot be accepted and a user's preference never changes C.
5. Add shared v3 golden vectors covering each issued level. Tamper separately with J, detached evidence, reference, report, policy and E. Test wrong lens, low light, reference loss and missing optional credentials. Capture on A and register the unchanged file against B's enrolled sensor. Measure attribution errors on real devices. Existing v1 vectors cover only the inherited primitive operations. Treat calibration performance and manufacturer support on the actual app route as unverified until measured.

---

## Historical appendix | P-256 v1 specification and fixtures

The material below is the legacy profile for existing receipts and regression tests. Its mandatory manufacturer gate, receipt schema, domain label and origin outcomes apply **only to v1**. The sections above govern new v3 captures.

## FingerprinTO | Android cryptographic manual

This document retains the proposed v1 P-256 profile. The separately versioned
ML-DSA-65 + Ed25519 experiment and its hardware-protection boundary are specified
in [hybrid-v2.md](hybrid-v2.md); v1 and v2 must not be interpreted interchangeably.

Under the proposed protocol, PhoTO signs a receipt for **the exact file returned by capture, after verifying its camera-origin credential**. Three principals contribute different evidence. The camera credential issuer authenticates a capture claim about an asset. The installation key signs PhoTO's record of receiving that asset. XRP Ledger (XRPL) records a commitment to the signed receipt. A verifier needs the original evidence as well as the ledger entry.

Sections 1–4 describe the protocol. The final implementer note fixes the previously unspecified byte formats and Android integration choices for a proposed v1 profile. The formulas and field names retain the original draft's meanings.

### 1. Installation identity

At first enrollment, PhoTO generates a signing key pair in Android Keystore. The private key `sk_I` stays there. The public value `pk_I` is the DER SubjectPublicKeyInfo of its public key. A verifier checks the key's hardware backing and the app identity at key generation using Android key attestation.

```text
ID_I = SHA256(pk_I)
F_I  = D("install", ID_I, packageName, versionCode, signerDigest)
```

Here `D(label, ...fields)` hashes the deterministic CBOR array containing the label followed by each field. `SHA256(x)` hashes the raw bytes of x. Every hash result is 32 bytes.

| Input | Meaning |
|---|---|
| ID_I | Public identifier of this installation key. Receipts under the same key are linkable. The key pair supplies both an identifier and proof of possession, so there is no installation nonce. |
| packageName | Android application package name as installed. |
| versionCode | Installed build's numeric version code. |
| signerDigest | SHA-256 of the DER certificate of the current APK signer. v1 supports one current signer. |

`F_I` binds the key identifier to the app's declared identity at receipt creation. An ordinary app update can preserve `ID_I` and change `F_I`. Attestation describes the app at **key generation**. A newer version code in a signed receipt remains an app declaration. Neither the version code nor the signer digest is a hash of the APK contents.

### 2. Photo and origin credential

`J` is the complete, finalized encoded file delivered to PhoTO, including metadata. `A` is an issuer's origin credential for this asset. Its format defines the authenticated byte ranges, signer and capture assertions. First validate that binding, the signer's authority and the meaning of the claim. Only then compute the photo fingerprint.

| Credential placement | Photo fingerprint |
|---|---|
| Embedded in J | `F_P = D("photo", F_I, SHA256(J))` |
| Delivered separately | `F_P = D("photo", F_I, SHA256(J), SHA256(A))` |

For a detached credential, `A` means its exact delivered bytes. For an embedded credential, the whole-file digest already covers it. The receipt also commits to bytes outside the issuer's authenticated ranges. A later metadata rewrite can therefore invalidate the receipt even if the issuer still accepts the asset. Identical inputs give the same `F_P`. There is no per-photo nonce.

### 3. Device and session

For each row in the following order, compute `L_i = D("field", name, value)`, indexing from zero. The name is the exact case-sensitive text in the first column.

| name | value and interpretation |
|---|---|
| ID_I | The same 32 bytes derived in section 1. |
| manufacturer | Android's declared manufacturer string. |
| model | Android's declared model string. These two strings describe a class of devices. |
| localTime | Wall-clock time when PhoTO receives the completed file, in Unix epoch milliseconds. The device clock can be changed. |
| sessionChallenge | Optional unpredictable bytes issued by a verifier before capture. Encode absence as CBOR null and keep the leaf. |

```text
Node(left, right) = D("node", left, right)
F_D = Node(Node(Node(L_0, L_1), Node(L_2, L_3)), L_4)
```

This is a binary Merkle tree with unpaired children promoted unchanged at each level. A receipt containing an outstanding challenge can demonstrate a response to that challenge. Dating the capture itself would require the capture component to authenticate that same challenge with the asset.

### 4. Receipt and verification claim

```text
T     = Node(Node(F_I, F_P), F_D)
R     = D("fingerprinto/v1", T)
sigma = Sign(sk_I, R)
C     = D("receipt", R, sigma)
```

`T` is the ordered Merkle root. `R` binds T to the exact protocol and version. The tree preserves three independently reproducible fingerprints. v1 requires full disclosure and defines no selective-disclosure proof. `sigma` is the installation signature. `C` commits to the particular signed receipt and is the 32-byte value published on XRPL. Verification requires the following independent checks.

1. Establish trust in the installation key and enrollment identity. Compare the receipt's app claims with release policy and distinguish enrollment-time evidence from current declarations.
2. Validate the credential against the supplied original file and capture policy.
3. Recompute every digest from the supplied evidence. Verify the signature on the reconstructed root.
4. Recompute the commitment from the received signature bytes. Verify its inclusion in a successful transaction on the expected validated ledger.

A successful result establishes that the attested installation key signed for this precise asset carrying accepted capture evidence. If phone A captures a genuine photo and phone B registers it, B's signature is consistent with every formula above. Independent attribution to B's sensor would require evidence linking the capture, session and installation to the same physical unit. Ledger closure dates the commitment to the precision of the ledger clock. It does not establish shutter time or the truth of the depicted scene.

**---**

### Implementer note | Proposed v1 profile

#### Integration points in the existing app

The repository review reports that Android already captures a file, computes SHA-256 and uploads it to the server. The existing timestamping path uses Bitcoin/OpenTimestamps (OTS). The review found no attested enrollment, camera-credential validation, CBOR receipts or XRPL integration. Locate the capture callback and upload path in the checkout. Preserve the semantics of existing records.

Nostr publication uses a separate key. Even a signed Nostr event referencing C would show only that the event author published that receipt. v1 defines no proof of common ownership between the installation and publication keys.

#### First milestone: validate one real capture off-device

1. Record handset, OS build, camera provider and version, PhoTO build, capture entry point and mode. Specify the authorized issuer and which signed assertion qualifies as original capture.
2. Capture through the normal PhoTO UI. Export the completed file and any detached evidence unchanged. Compare the SHA-256 digests at capture and on the independent host. Optionally collect a native-camera sample on the same handset as a diagnostic control.
3. On the independent host record credential placement, signer, signature result, asset-binding result, exact authenticated capture assertion and its policy outcome. Archive the original inputs, validator version, trust policy and report for reproduction.
4. Change a byte in an authenticated asset range while keeping the container parseable. Confirm rejection for a binding mismatch. The positive case requires all checks on the original **PhoTO output** to pass. A missing credential, unavailable policy or positive native-camera sample alone leaves this route unproven.

No qualifying sample accompanies this manual. Establish feasibility for the tested app route first, freeze receipt encoding against shared vectors second, and integrate anchoring third.

#### Exact byte contract

Use RFC 8949 §4.2.1 core deterministic encoding with definite-length arrays and shortest integer and length encodings. Protocol text is valid UTF-8 as supplied, without Unicode normalization, case conversion or trimming. Binary inputs are CBOR byte strings. Use CBOR integers for version and time, and simple value null for an absent challenge. Reject invalid UTF-8, wrong types or arity, indefinite lengths, tags, floats, non-minimal encodings and trailing bytes. At export and import use the following positional schema; array entries are individual items, never flattened subarrays.

```text
receipt.cbor = [
  1, pk_I, chain, packageName, versionCode, signerDigest,
  manufacturer, model, localTime, sessionChallenge, placement, sigma
]
chain     = [cert_0, ..., cert_n]    # DER X.509 byte strings, leaf first
placement = 0 | 1                    # embedded | detached (CBOR unsigned int)
```

Use a nonempty certificate chain, a nonnegative version code fitting a signed 64-bit integer, signed 64-bit epoch milliseconds, and a 32-byte session challenge when present. `pk_I`, `signerDigest` and `sigma` are byte strings. Package, manufacturer and model are text. Keep the original asset as a separate binary file and, for placement 1, include the detached credential as a separate binary file. Placement 0 requires extracting evidence from the asset. Reject missing or conflicting evidence and ambiguous credential selection. Preserve an optional anchor locator alongside the bundle, containing the expected network identifier and transaction hash. The locator is resolved and checked independently. Validating challenge freshness also requires the verifier's issuance record.

**Parser limits (proposed v1).** Measure strings in encoded UTF-8 bytes. Enforce these inclusive limits on both export and import. Reject an oversized input before allocating from an untrusted length. Stream the asset and enforce aggregate limits during decompression or archive extraction.

| Input | Constraint |
|---|---|
| packageName, manufacturer, model | 1–255 bytes each |
| signerDigest | Exactly 32 bytes |
| pk_I | Exactly 91 bytes: one strict DER SPKI with id-ecPublicKey and named curve prime256v1, zero unused bits and an uncompressed 65-byte point. Decode and validate a finite on-curve P-256 point. Reject trailing data. |
| chain | 1–8 certificates, each a single fully consumed DER X.509 object of at most 16 KiB |
| sigma | 8–72 bytes, with the existing strict DER and scalar checks |
| receipt.cbor | At most 256 KiB, with exactly the arrays and nesting in the schema above |
| J and detached A | At most 128 MiB and 16 MiB respectively |
| Whole bundle | At most 160 MiB total uncompressed, including optional anchor evidence and any auxiliary files |

A size-limit failure is a profile rejection. Report it separately from a cryptographic mismatch. [EC public key encoding](https://www.rfc-editor.org/rfc/rfc5480)

Derived digests need no wire fields. Exporters may display hex for diagnostics only. The receipt version selects this exact profile; reject unknown versions. Wire version 1 selects the exact UTF-8 label `fingerprinto/v1` inside R. Reject earlier-draft signatures over bare T and never fall back after verification fails. Verify the chain and ledger locator independently. [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949#section-4.2.1)

#### Enrollment and signing

1. Use a stable app-private Keystore alias. Provision once under a lock. On API 24+ obtain a fresh 32-byte challenge from the enrollment verifier, separate from the optional capture challenge. Have the verifier record issuance, expiry and consumption. If enrollment is offline or pending, preserve that state and resume before issuing verified receipts.
2. Use `KeyPairGenerator.getInstance("EC", "AndroidKeyStore")` and `KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)` with `ECGenParameterSpec("secp256r1")`, `DIGEST_SHA256` and `setAttestationChallenge(enrollmentChallenge)`. Request StrongBox only if supported and required by policy. A TEE is acceptable when policy allows it. Absence of an acceptable hardware attestation leaves enrollment unsupported. Older API levels cannot perform this enrollment flow.
3. Retrieve `KeyStore.getCertificateChain(alias)`, encode each certificate with `getEncoded()`, and use the signing public key's `getEncoded()` for SPKI. Send enrollment evidence to an independent verifier over an authenticated channel. Retain the accepted enrollment record keyed by `ID_I`, including the issued challenge, chain, enrollment time and policy used. A later verifier needs this record or a trusted enrollment decision to substantiate challenge freshness.
4. The enrollment verifier builds a valid path to configured Android attestation roots, applies Android's revocation checks and parses the authoritative key-attestation extension nearest the trusted root following Android's chain rules. Bind the attested certificate's SPKI to the exact receipt signing key; reject appended attacker-controlled leaves. Check the challenge, attestation and KeyMint/Keymaster security levels (TrustedEnvironment or StrongBox under policy), hardware-enforced generated EC P-256 signing key and SHA-256 authorization, and expected boot state under deployment policy. Check the attestation application ID against the expected package, allowed signing certificate digests and allowed package set for the UID. Android supplies the application identity through the OS. Record the attested version separately. Maintain roots and revocation data independently of the bundle.
5. Read `context.packageName` and its `PackageInfo`. On API 28+ use `GET_SIGNING_CERTIFICATES`, `longVersionCode` and `signingInfo.apkContentsSigners`. On API 24–27 use the legacy package-signatures flag and version code, and accept only a single signer the release policy recognizes. Hash the current DER signer certificate once. Treat rotation as an explicit release-policy transition; do not pick a historical signer to force a match. Reject unsupported signer sets. Use `Build.MANUFACTURER`, `Build.MODEL` and one `System.currentTimeMillis()` sample at capture completion.
6. Sign using `Signature.getInstance("SHA256withECDSA")`, `initSign(privateKey)`, `update(R)`, then `sign()`. The message is exactly the 32 raw bytes of R. JCA performs the signature's SHA-256 internally. Verify with the same algorithm and message; for a prehashed API the prehash is SHA256(R). Require a strict DER SEQUENCE of exactly two minimally encoded positive INTEGERs r,s in the P-256 scalar range. Persist the DER bytes returned by signing. Re-encoding, normalizing s or signing twice can change C.
7. Key loss or invalidation requires a new enrollment and identifier. Keep prior evidence available for old receipts. Exclude active enrollment state from backup or reconcile it against the live alias/SPKI after restore. Never sign using a replacement key under the old ID.

[Android Keystore](https://developer.android.com/privacy-and-security/keystore) · [Key attestation verification and trust anchors](https://developer.android.com/privacy-and-security/security-key-attestation) · [Attestation fields](https://source.android.com/docs/security/features/keystore/attestation) · [SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo)

#### Capture and credential validation

For CameraX, prefer the existing full-resolution file output path using `ImageCapture.takePicture(OutputFileOptions, executor, OnImageSavedCallback)`. On successful completion, snapshot the finalized bytes in app-private storage before any EXIF edit, metadata stripping, bitmap conversion, thumbnail creation or re-encoding. If the app delegates capture, pass a full-resolution output URI and read that completed output through `ContentResolver`. An intent thumbnail cannot serve as J. Bind the callback and any detached credential to the same pending capture. Stream SHA-256 over the immutable snapshot and validate the credential on that very snapshot. On cancellation, incomplete writes or permission failure, discard the incomplete capture.

CameraX documents an image result, with no general promise of a manufacturer-signed capture credential. Google documents Content Credentials for supported Pixel Camera photo modes on Pixel 10 or later, subject to OS, connectivity and update conditions. This is specific to Pixel Camera. Check the actual output of each intended app route on target hardware.

For C2PA, use a maintained validator supporting the asset format and manifest version. Supply the original asset and, for detached evidence, the exact external manifest. Require a valid active manifest signature, an accepted asset hard binding and an authorized capture signer. Configure which signed actions, digital source types and provenance histories the issuer profile accepts as original photographic capture. An edited asset with a genuine camera image as an ingredient cannot pass on that fact alone. Preserve validator status codes and policy version for diagnosis. For a vendor-specific format, require the equivalent documented checks. The draft supplies no issuer allowlist or validated capture sample. Until deployment supplies these, report origin unverifiable.

| Validation outcome | App action |
|---|---|
| No credential, unsupported profile, missing trust configuration, or validation temporarily unavailable | Report origin unverifiable, with the specific reason. Stop before signing. |
| Recognized credential malformed, binding or signature invalid, signer untrusted under configured policy, or claim fails capture policy | Reject the credential, record the reason and stop before signing. |
| Complete credential and capture policy checks pass | Compute the appropriate photo branch and continue. |

[CameraX file capture](https://developer.android.com/media/camera/camerax/take-photo) · [Pixel Camera requirements](https://support.google.com/pixelcamera/answer/16507662?hl=en) · [C2PA specification](https://spec.c2pa.org/specifications/specifications/2.2/specs/C2PA_Specification.html)

#### Persistence, challenge and anchoring

Associate each pending capture with its own local operation ID. Snapshot its input fields once. Persist the receipt, exact signature and evidence consistently before placing C in an anchor queue. A process restart or network retry must reuse that saved receipt. If the verifier requires a session challenge, compare the signed bytes to its outstanding value, check expiry and atomically consume it on acceptance. Keep repeated verification of a stored receipt separate from accepting it as a fresh response.

XRPL is a proposed backend requiring a submitter and configured network. Existing OTS evidence over the uploaded photo digest cannot establish an anchor for C. For the Bitcoin/OTS receipt backend, timestamp a file named `commitment.bin` containing exactly the 32 raw bytes of C. Its detached proof is `commitment.bin.ots`. Reconstruct the file from the signed bundle for verification. The OTS file-hash algorithm must be SHA-256 and the starting digest must equal SHA256(C). With a file API pass the raw file; with a digest API pass SHA256(C) exactly once as the precomputed digest. Hex text, a newline, or interpreting C itself as the precomputed file digest produces a different proof contract. Verify the proof through to a confirmed Bitcoin attestation on the expected network; a calendar promise alone is pending. [OpenTimestamps file verification](https://github.com/opentimestamps/opentimestamps-client)

 Identify backend and network in the result. For deployments without an established payload convention, v1 proposes one matching Memo: MemoType is hex of UTF-8 `fingerprinto/v1`, MemoData is the hex encoding of the 32 raw bytes C. Configure the submitter, funding, transaction type and any required publisher identity outside the protocol. Publish only the commitment and type. Persist the transaction hash and network for reconciliation before retrying an uncertain submission.

A trusted XRPL node on the expected network must report the identified transaction as validated, with metadata result `tesSUCCESS`. Check the decoded memo type and exact 32-byte commitment, and the publisher account when policy requires it. Return separate results for signed, anchor pending, anchor failed and anchor verified. A submission acknowledgment, matching transaction hash alone or unvalidated ledger is insufficient. Record the validated ledger index and close time for display. [XRPL transaction memos](https://xrpl.org/docs/references/protocol/transactions/common-fields#memos-field) · [Ledger close times](https://xrpl.org/docs/concepts/ledgers/ledger-close-times)

#### Independent verification results and evaluation time

Return one result per row. Use an indeterminate outcome and a reason when required evidence is unavailable. Continue independent checks where inputs allow them.

| Result | Verification and outcomes |
|---|---|
| Origin | Independently check credential signature, signer authority, asset binding and capture semantics. Report accepted, rejected or indeterminate. |
| Installation signature | Reconstruct R and verify the exact signature under the supplied key. Report valid, invalid or indeterminate. A valid signature establishes use of the key; it cannot attest which checks PhoTO executed. |
| Enrollment | Verify the attestation and enrollment record against hardware and application identity policy. Report accepted, rejected or indeterminate. |
| Anchor | Resolve the backend and network, then independently verify inclusion of C. Report absent, pending, verified, failed or indeterminate. |

Record evaluation time, policy identifier and digest, trust-anchor set, revocation evidence and its validity interval, validator version and reason codes. A current assessment applies the verifier's current trust policy and applicable status evidence. To report a past acceptance decision, retain an authenticated, time-evidenced decision identifying the exact bundle, policy and outcomes. For independent historical re-evaluation also archive the applicable policy, trust anchors, chains, signed status evidence and trustworthy evidence of the evaluation time. Missing historical evidence yields an indeterminate historical assessment. Device time and ledger closure cannot reconstruct past revocation or trust policy. Display current and historical assessments separately.

#### Encoding check

For the absent challenge leaf, the deterministic CBOR preimage of `D("field", "sessionChallenge", null)` is `83656669656c647073657373696f6e4368616c6c656e6765f6`. The expected SHA-256 digest is `08af3a4d5c4355de83f11a0a38a2bdda38fcbeb17a2b2899efeb1e17c456d580`. Hash the decoded bytes.

For a synthetic T of 32 zero bytes, the preimage of R is `826f66696e6765727072696e746f2f763158200000000000000000000000000000000000000000000000000000000000000000` and R is `e48a020f88749bf33817bc525f835609183afdbea8433a3ea3f091e1ec6f7bae`. Verify that a signature for this R fails on bare T and on an R computed with another protocol label.

#### Acceptance checks

- Compare Android and independent verifier outputs for both credential placements, null and present challenges, and the unpaired-leaf rule. Check DER signature interoperability using the original signature bytes.
- Modify one original byte, detached credential byte, app field, session field and signature in separate tests. Every altered bundle must fail the affected verification step or anchor check.
- Exercise valid capture, absent credential, invalid asset binding, trusted edit-only provenance, untrusted issuer, software-only key and wrong enrollment challenge. Use a real accepted capture sample for the positive end-to-end test.
- On a supported handset, compare native camera output with the app's actual capture output. Record device, OS, capture app and mode, credential presence and validator result. A successful native-camera sample does not establish app-route support.
- Test app update with the same key, key loss, two simultaneous captures, interrupted export and XRPL submission timeout. Test wrong network, wrong memo, failed transaction and pending validation independently.

#### Complete synthetic interoperability vectors

The JSON block carries four standalone sets of file bytes plus shared decoded fields. Decode hex to bytes. Each case supplies the original JPEG J, the detached A when applicable, the entire receipt.cbor, and every expected tree and receipt intermediate. In the embedded cases the JPEG APP15 payload is the synthetic credential. N01 and N23 combine device leaves, N0123 combines those nodes, and N_IP combines F_I and F_P. The fixture reuses its self-signed certificate as the synthetic APK signer certificate. The enrollment chain has no Android attestation and the synthetic credential has no authorized issuer. Expected results are a valid installation signature, no accepted origin or enrollment, and an absent anchor. OTS_SHA256_C is the input digest for a detached OTS proof. No ledger proof is supplied. Verify each supplied DER signature verbatim; production ECDSA signers may generate different valid signatures for the same R.

```json
{
  "shared": {
    "pk_I": "3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
    "chain": [
      "3082012a3081d1a003020102020101300a06082a8648ce3d040302301f311d301b06035504030c1446696e6765727072696e544f2066697874757265301e170d3235303130313030303030305a170d3335303130313030303030305a301f311d301b06035504030c1446696e6765727072696e544f20666978747572653059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5300a06082a8648ce3d04030203480030450221008450e45257b3881b84a77c2a2d1362ed24ccf70671fd66de295dce87839bef50022060b79c3b67d6f71856651322b7122e9b15d1ffa67e4d7067001fadd8e211ee70"
    ],
    "packageName": "org.example.photo",
    "versionCode": 1,
    "signerDigest": "522fa6bce1010969a561f727653994887495ef6a0547c642cf0e73856a9d8a25",
    "manufacturer": "Fixture",
    "model": "P256",
    "localTime": 1700000000123
  },
  "cases": [
    {
      "placement": 0,
      "sessionChallenge": null,
      "J": "ffd8ffef001e66696e6765727072696e746f2d746573742d63726564656e7469616cffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffdb0043010909090c0b0c180d0d1832211c213232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232ffc00011080001000103012200021101031101ffc4001500010100000000000000000000000000000008ffc40014100100000000000000000000000000000000ffc40014010100000000000000000000000000000000ffc40014110100000000000000000000000000000000ffda000c03010002110311003f009fc007ffd9",
      "A": null,
      "receipt_cbor": "8c01585b3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f58159012e3082012a3081d1a003020102020101300a06082a8648ce3d040302301f311d301b06035504030c1446696e6765727072696e544f2066697874757265301e170d3235303130313030303030305a170d3335303130313030303030305a301f311d301b06035504030c1446696e6765727072696e544f20666978747572653059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5300a06082a8648ce3d04030203480030450221008450e45257b3881b84a77c2a2d1362ed24ccf70671fd66de295dce87839bef50022060b79c3b67d6f71856651322b7122e9b15d1ffa67e4d7067001fadd8e211ee70716f72672e6578616d706c652e70686f746f015820522fa6bce1010969a561f727653994887495ef6a0547c642cf0e73856a9d8a25674669787475726564503235361b0000018bcfe5687bf60058483046022100c14b532cbdad2c969793f9e8cf08a8884da4828bb7c31d8b3fee397f457bb6b8022100c763d0a758d94e51227d4d6004fa21c3a2339b6de2ff4098f4eee9236aff96e5",
      "expected": {
        "SHA256_J": "25b84a49af248c2587fd7707957b42ca36fb845df32c8c10a6042a8d87080b1e",
        "ID_I": "5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3",
        "F_I": "732e34931b55c3edebe3cad3a305ba94a58c7b6bc67c17bcc679a46564b2aa0a",
        "F_P": "fa7c079041c142523100956c04f6d593aa037253504972bc41365e6d4e105074",
        "L0": "32cd1d27cd5c2c6b76dd597831e31ec80b18b9c12a18b6ca4859e69f6816f129",
        "L1": "16d6abe696903385c1d855169f2d533a16cf74673e986beb018419c9b0fa2647",
        "L2": "34a8ad3015953f4a10e920a3328210afe0e1d9939e8ba75b40345b8216121f69",
        "L3": "0f0928b599f558ce9959ba9b55d63d8c30f1a79379d8fa03ef84e1bf39a88b7e",
        "L4": "08af3a4d5c4355de83f11a0a38a2bdda38fcbeb17a2b2899efeb1e17c456d580",
        "N01": "c9923d4d95b619c912cfc9f49b61a6404b0c5292a2ec28d6e26504606bd1d20a",
        "N23": "c9fedb53a9e8dc0e0dfc406b90c7dc167f1649eb1d6fe12785d5f415131f990f",
        "N0123": "ca30119a7f95fe9bfc9cd05e0e465c9dc6ba891bb13845e622209f6268412bfd",
        "F_D": "00b8927dec6e5cfdcc4bf1622eb58a2399c9dbb0a59c084652b5a348e96dbb7f",
        "N_IP": "27daba709d327c4fb9c748d0535912df8bf987eeb0c00ec2cde296ff44c1ceca",
        "T": "48a07f95d2000bf8c2bd44f228b9355602d803a8d577f566e8c9c2bf57ca8954",
        "R": "cbd3e4799e5038d14932d58be5285e4348cd9148949525e0807ce8901e262fb5",
        "sigma": "3046022100c14b532cbdad2c969793f9e8cf08a8884da4828bb7c31d8b3fee397f457bb6b8022100c763d0a758d94e51227d4d6004fa21c3a2339b6de2ff4098f4eee9236aff96e5",
        "C": "10080bd71b5510c1411942c40961419d8499624a4196dc2be1a587f670006f93",
        "OTS_SHA256_C": "a3cc1b873f708524465d9cd5c3edf8bcd32c3504d29b63c0d7202599dede5a8c"
      }
    },
    {
      "placement": 0,
      "sessionChallenge": "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
      "J": "ffd8ffef001e66696e6765727072696e746f2d746573742d63726564656e7469616cffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffdb0043010909090c0b0c180d0d1832211c213232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232ffc00011080001000103012200021101031101ffc4001500010100000000000000000000000000000008ffc40014100100000000000000000000000000000000ffc40014010100000000000000000000000000000000ffc40014110100000000000000000000000000000000ffda000c03010002110311003f009fc007ffd9",
      "A": null,
      "receipt_cbor": "8c01585b3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f58159012e3082012a3081d1a003020102020101300a06082a8648ce3d040302301f311d301b06035504030c1446696e6765727072696e544f2066697874757265301e170d3235303130313030303030305a170d3335303130313030303030305a301f311d301b06035504030c1446696e6765727072696e544f20666978747572653059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5300a06082a8648ce3d04030203480030450221008450e45257b3881b84a77c2a2d1362ed24ccf70671fd66de295dce87839bef50022060b79c3b67d6f71856651322b7122e9b15d1ffa67e4d7067001fadd8e211ee70716f72672e6578616d706c652e70686f746f015820522fa6bce1010969a561f727653994887495ef6a0547c642cf0e73856a9d8a25674669787475726564503235361b0000018bcfe5687b5820000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f005847304502202649438820fda112dfc242e20bf058d8bc1702e17f1510a0e27b796860776394022100e7a39f1c51e88b461416a41ae2cb6531e4ed1703e539ccfc3e60f63082da0389",
      "expected": {
        "SHA256_J": "25b84a49af248c2587fd7707957b42ca36fb845df32c8c10a6042a8d87080b1e",
        "ID_I": "5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3",
        "F_I": "732e34931b55c3edebe3cad3a305ba94a58c7b6bc67c17bcc679a46564b2aa0a",
        "F_P": "fa7c079041c142523100956c04f6d593aa037253504972bc41365e6d4e105074",
        "L0": "32cd1d27cd5c2c6b76dd597831e31ec80b18b9c12a18b6ca4859e69f6816f129",
        "L1": "16d6abe696903385c1d855169f2d533a16cf74673e986beb018419c9b0fa2647",
        "L2": "34a8ad3015953f4a10e920a3328210afe0e1d9939e8ba75b40345b8216121f69",
        "L3": "0f0928b599f558ce9959ba9b55d63d8c30f1a79379d8fa03ef84e1bf39a88b7e",
        "L4": "9523c968b0dcd89b6b18212a4dc11b1491c0b63602f8dd5624741f682a7221ae",
        "N01": "c9923d4d95b619c912cfc9f49b61a6404b0c5292a2ec28d6e26504606bd1d20a",
        "N23": "c9fedb53a9e8dc0e0dfc406b90c7dc167f1649eb1d6fe12785d5f415131f990f",
        "N0123": "ca30119a7f95fe9bfc9cd05e0e465c9dc6ba891bb13845e622209f6268412bfd",
        "F_D": "22b07c122732d14ae6c064aa2dac68a0b8a180e6cd909e3871f3a28c292e5167",
        "N_IP": "27daba709d327c4fb9c748d0535912df8bf987eeb0c00ec2cde296ff44c1ceca",
        "T": "7a9b244bd81691d1ffa8a9be458459a093980589f03191e0eea3c983d980728c",
        "R": "c360e296ed127d1f6ff869dbe2cff4c4cbf3d4259bb371f624b9c406eb8cb75c",
        "sigma": "304502202649438820fda112dfc242e20bf058d8bc1702e17f1510a0e27b796860776394022100e7a39f1c51e88b461416a41ae2cb6531e4ed1703e539ccfc3e60f63082da0389",
        "C": "c24997cadc672fb26feb4117a55ca4f736c1a32d3ac8307aae7f470eaff07271",
        "OTS_SHA256_C": "a39afd706b7ddaa604228135605eb4ab5a30f225528adcdbe4b7d379ac40ffff"
      }
    },
    {
      "placement": 1,
      "sessionChallenge": null,
      "J": "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffdb0043010909090c0b0c180d0d1832211c213232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232ffc00011080001000103012200021101031101ffc4001500010100000000000000000000000000000008ffc40014100100000000000000000000000000000000ffc40014010100000000000000000000000000000000ffc40014110100000000000000000000000000000000ffda000c03010002110311003f009fc007ffd9",
      "A": "66696e6765727072696e746f2d746573742d63726564656e7469616c",
      "receipt_cbor": "8c01585b3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f58159012e3082012a3081d1a003020102020101300a06082a8648ce3d040302301f311d301b06035504030c1446696e6765727072696e544f2066697874757265301e170d3235303130313030303030305a170d3335303130313030303030305a301f311d301b06035504030c1446696e6765727072696e544f20666978747572653059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5300a06082a8648ce3d04030203480030450221008450e45257b3881b84a77c2a2d1362ed24ccf70671fd66de295dce87839bef50022060b79c3b67d6f71856651322b7122e9b15d1ffa67e4d7067001fadd8e211ee70716f72672e6578616d706c652e70686f746f015820522fa6bce1010969a561f727653994887495ef6a0547c642cf0e73856a9d8a25674669787475726564503235361b0000018bcfe5687bf60158483046022100898291ddd41692c96f6dfb0a0c9711e38792ea7aa5f75c9c5f52c825d9095934022100abb272a9b7459fe72c90352588be65b6f88cbb4a732c5bfe85654f5ed9585636",
      "expected": {
        "SHA256_J": "89337bd509d7c77686ba336d18dfdc68f45ec191df2fecc55b6743417bd32c7c",
        "ID_I": "5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3",
        "F_I": "732e34931b55c3edebe3cad3a305ba94a58c7b6bc67c17bcc679a46564b2aa0a",
        "F_P": "230bfa9d335a31a3cf557b80381aeb7a099392229820112c91b5e769f376af7e",
        "L0": "32cd1d27cd5c2c6b76dd597831e31ec80b18b9c12a18b6ca4859e69f6816f129",
        "L1": "16d6abe696903385c1d855169f2d533a16cf74673e986beb018419c9b0fa2647",
        "L2": "34a8ad3015953f4a10e920a3328210afe0e1d9939e8ba75b40345b8216121f69",
        "L3": "0f0928b599f558ce9959ba9b55d63d8c30f1a79379d8fa03ef84e1bf39a88b7e",
        "L4": "08af3a4d5c4355de83f11a0a38a2bdda38fcbeb17a2b2899efeb1e17c456d580",
        "N01": "c9923d4d95b619c912cfc9f49b61a6404b0c5292a2ec28d6e26504606bd1d20a",
        "N23": "c9fedb53a9e8dc0e0dfc406b90c7dc167f1649eb1d6fe12785d5f415131f990f",
        "N0123": "ca30119a7f95fe9bfc9cd05e0e465c9dc6ba891bb13845e622209f6268412bfd",
        "F_D": "00b8927dec6e5cfdcc4bf1622eb58a2399c9dbb0a59c084652b5a348e96dbb7f",
        "N_IP": "448e8a61fd950432cde60ba026398925a4b41133756414171ec5515a4d73ac46",
        "T": "cfe1a3e2465aa7551ac166a0cbeb5ed659c3d751893d039e344a7dec11c66ad8",
        "R": "84a551f1d519c27b6412a23f5b2584fa0e55fc0a88b459cf4161978793fdbbc8",
        "sigma": "3046022100898291ddd41692c96f6dfb0a0c9711e38792ea7aa5f75c9c5f52c825d9095934022100abb272a9b7459fe72c90352588be65b6f88cbb4a732c5bfe85654f5ed9585636",
        "C": "f680a5ccb52f51f8941177a54390a19492b9a2480a614199cbdabb85d021ac87",
        "OTS_SHA256_C": "4eca63d857c50f98447083c2a5e9da5f98801a5275b1819751d8cb27b5be85f4",
        "SHA256_A": "e3b0996ee6a04ec5799f6f556264f9e87bd1710ac812cc4ee484d89a692b91e5"
      }
    },
    {
      "placement": 1,
      "sessionChallenge": "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
      "J": "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffdb0043010909090c0b0c180d0d1832211c213232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232323232ffc00011080001000103012200021101031101ffc4001500010100000000000000000000000000000008ffc40014100100000000000000000000000000000000ffc40014010100000000000000000000000000000000ffc40014110100000000000000000000000000000000ffda000c03010002110311003f009fc007ffd9",
      "A": "66696e6765727072696e746f2d746573742d63726564656e7469616c",
      "receipt_cbor": "8c01585b3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f58159012e3082012a3081d1a003020102020101300a06082a8648ce3d040302301f311d301b06035504030c1446696e6765727072696e544f2066697874757265301e170d3235303130313030303030305a170d3335303130313030303030305a301f311d301b06035504030c1446696e6765727072696e544f20666978747572653059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5300a06082a8648ce3d04030203480030450221008450e45257b3881b84a77c2a2d1362ed24ccf70671fd66de295dce87839bef50022060b79c3b67d6f71856651322b7122e9b15d1ffa67e4d7067001fadd8e211ee70716f72672e6578616d706c652e70686f746f015820522fa6bce1010969a561f727653994887495ef6a0547c642cf0e73856a9d8a25674669787475726564503235361b0000018bcfe5687b5820000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f0158483046022100d789e123159418af0a14f41475314d51d6adf42b41b0ef9504d14bc0c574fac4022100e1f9657323bc6a7e15eb62175d597d211baf4f46d4f2780c67f67d3ed9accf3c",
      "expected": {
        "SHA256_J": "89337bd509d7c77686ba336d18dfdc68f45ec191df2fecc55b6743417bd32c7c",
        "ID_I": "5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3",
        "F_I": "732e34931b55c3edebe3cad3a305ba94a58c7b6bc67c17bcc679a46564b2aa0a",
        "F_P": "230bfa9d335a31a3cf557b80381aeb7a099392229820112c91b5e769f376af7e",
        "L0": "32cd1d27cd5c2c6b76dd597831e31ec80b18b9c12a18b6ca4859e69f6816f129",
        "L1": "16d6abe696903385c1d855169f2d533a16cf74673e986beb018419c9b0fa2647",
        "L2": "34a8ad3015953f4a10e920a3328210afe0e1d9939e8ba75b40345b8216121f69",
        "L3": "0f0928b599f558ce9959ba9b55d63d8c30f1a79379d8fa03ef84e1bf39a88b7e",
        "L4": "9523c968b0dcd89b6b18212a4dc11b1491c0b63602f8dd5624741f682a7221ae",
        "N01": "c9923d4d95b619c912cfc9f49b61a6404b0c5292a2ec28d6e26504606bd1d20a",
        "N23": "c9fedb53a9e8dc0e0dfc406b90c7dc167f1649eb1d6fe12785d5f415131f990f",
        "N0123": "ca30119a7f95fe9bfc9cd05e0e465c9dc6ba891bb13845e622209f6268412bfd",
        "F_D": "22b07c122732d14ae6c064aa2dac68a0b8a180e6cd909e3871f3a28c292e5167",
        "N_IP": "448e8a61fd950432cde60ba026398925a4b41133756414171ec5515a4d73ac46",
        "T": "819bdd798ffe67cb6621562fab0d40166a9a6edb5907531ab436051dc3256936",
        "R": "b43b461c1655107f5ef29d537b019dcf22e6d7dfdbe5555cd7d9fe9ca35e7173",
        "sigma": "3046022100d789e123159418af0a14f41475314d51d6adf42b41b0ef9504d14bc0c574fac4022100e1f9657323bc6a7e15eb62175d597d211baf4f46d4f2780c67f67d3ed9accf3c",
        "C": "f22f40d3eb3c43c4e6782256647cbf95ce815de760bb368bf52c7c1b2a11dd56",
        "OTS_SHA256_C": "a396cda66b7818551457a40e25562d8cfa13f8c5f7d084fc95ba2b4ebf90f726",
        "SHA256_A": "e3b0996ee6a04ec5799f6f556264f9e87bd1710ac812cc4ee484d89a692b91e5"
      }
    }
  ]
}
```
