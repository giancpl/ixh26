# Independent Android enrollment intake

`fingerprinto.enrollment` verifies a **fresh enrollment** on the independent
host. It is separate from historical receipt verification and from camera-origin
validation. It uses cryptography's certificate signature verification; it never
downloads roots or endpoints supplied by a certificate, receipt or phone.

## Verifier-owned inputs

Configure `EnrollmentPolicy` with an identifier, pinned DER root certificates,
the exact permitted UID package set and minimum enrollment-time version for
each, and allowed APK signing-certificate SHA-256 digests. The default hardware
levels are TEE and StrongBox; restrict to `{2}` to require StrongBox. Optional
minimum OS/patch values and a boot-key allowlist are included in the policy
digest. Roots must come from the deployment's independently maintained Android
trust configuration, not from the incoming chain. No production roots are
bundled or implicitly trusted.

Create `EnrollmentRequests` on a verifier-controlled SQLite path. Issue a
32-byte random challenge scoped to `policy.digest`, with a bounded lifetime,
then send it through the authenticated enrollment channel. The database is
persistent state: never accept a phone-supplied DB or enable callers to set
their own trusted evaluation time. Configure file permissions and service
authentication in deployment.

Supply a `RevocationSnapshot` collected through an authenticated channel by the
verifier's trusted status service. Include **both revoked and suspended**
certificate serials, observation time, validity deadline and the hash of the
archived source bytes. The object is trusted verifier configuration, not a
proof that authenticates arbitrary JSON. The module does not fetch/authenticate
Android status responses yet. Do not construct it from receipt/bundle metadata.
Unavailable or stale status yields `indeterminate`; it cannot accept enrollment.

```python
from datetime import datetime, timezone
from fingerprinto.enrollment import EnrollmentPolicy, enroll_receipt
from fingerprinto.enrollment_requests import EnrollmentRequests

policy = EnrollmentPolicy(
    identifier="deployment-policy-1",
    roots=tuple(trusted_root_der_certificates),
    packages=(("com.example.photoanchor", minimum_approved_version),),
    signer_digests=frozenset(approved_apk_signer_sha256_bytes),
    minimum_os_patch=minimum_approved_yyyymm,
)
requests = EnrollmentRequests("verifier-enrollment.sqlite")
challenge = requests.issue(policy.digest, now=datetime.now(timezone.utc))
# Deliver challenge to provisioning, collect the chain and a receipt whose
# signature proves possession. Authenticate the enrollment service separately.
result = enroll_receipt(
    receipt_bytes, original_asset_bytes, detached_credential_bytes_or_none,
    policy=policy, requests=requests, revocations=trusted_status_snapshot,
    now=datetime.now(timezone.utc),
)
print(result.to_dict())
```

`verify_enrollment(public_key, chain, ...)` performs attestation intake alone;
it does not establish key possession. Prefer `enroll_receipt` for intake with
the existing signature check. Origin remains indeterminate until independently
validated. Successful enrollment never authorizes an image-origin claim.

## Checks and supported profile

The module checks the exact P-256 SPKI, an ordered leaf-first chain of 2–8 DER
certificates, an exact pinned root, certificate validity at verifier time,
issuer signatures, CA/keyCertSign constraints and path length. Following the
EC-only requirement, RSA issuer chains are unsupported; EC issuer curves are
P-256/P-384/P-521. Deployments need independently trusted EC attestation roots
and compatible device chains; do not substitute an incoming root. Certificate
signature hashes must be SHA-256/384/512. No AIA or general path building occurs.
Unsupported critical extensions, name/policy constraints, EKU and self-issued
intermediates yield indeterminate, rather than bypassing unsupported constraints.

The authoritative key-attestation extension is selected nearest the root. The
v1 profile requires it on the signing leaf and binds its SPKI exactly to pk_I;
appended attacker certificates are rejected. If a provisioning extension is
present, it must be on the immediately preceding certificate toward the root.

The bounded DER reader handles KeyDescription versions 3, 4 and KeyMint
100/200/300/400/500 with their corresponding Keymaster/KeyMint versions. Earlier
and unknown profiles are indeterminate. Interpreted fields require strict DER
types, minimal encodings, unique ordered explicit tags and canonical SETs.
Unused authorization fields remain opaque and cannot satisfy policy conditions.

Both security levels must meet policy. Generated EC P-256, 256-bit size,
sign-only purpose and SHA-256-only digest authorizations must be hardware
enforced. Conflicting software claims are rejected. Verified boot must be
locked and Verified, with boot key/hash present; configured boot-key and OS
policy are checked. The software-enforced application ID must have the exact
UID package set, acceptable versions and a single approved signer digest.
These are enrollment-time app claims, not current APK integrity measurements.

All chain serials are checked against the current trusted status snapshot.
Only after all checks does a single SQLite compare-and-consume bind the live
challenge to policy digest and installation ID. Expired, unknown, future-issued
or already-consumed challenges fail. Concurrent submissions can accept once.
Invalid receipts fail proof of possession before consuming anything.

## Retention and limits

Archive the result, original chain, policy, status evidence and trusted evaluation
time on acceptance. The challenge DB retains policy scope, consumption time and
installation ID; it is not a complete authenticated historical enrollment report.
Crash recovery of the returned report and a signed portable enrollment decision
are not implemented. Do not re-run fresh intake to verify old receipts: the
challenge has been consumed. Historical assessment must use retained trusted
enrollment evidence and the appropriate policy/status history.

Tests generate synthetic certificates and ASN.1 attestations under test-only
roots. They establish implementation behavior, not compatibility with a physical
Android handset. No real attestation or production revocation feed was used.

Protocol references: [Android attestation schema](https://source.android.com/docs/security/features/keystore/attestation),
[Android chain verification rules](https://developer.android.com/privacy-and-security/security-key-attestation),
[cryptography X.509 API](https://cryptography.io/en/latest/x509/reference/).

## Explicit GrapheneOS boot policy

The default remains `allowed_boot_states=frozenset({0})` (Verified). To accept
an approved GrapheneOS release on a locked device, configure both
`allowed_boot_states=frozenset({0, 1})` and a nonempty `allowed_boot_keys`
containing the independently authenticated binary Verified Boot key fingerprints.
SelfSigned (1) is never accepted without key pins. Unlocked devices, Unverified
and Failed states remain rejected. Do not learn a trusted key from the submitted
attestation. Obtain current keys and authenticate updates following the
[official GrapheneOS guide](https://grapheneos.org/articles/attestation-compatibility-guide).

Changing the permitted states changes the policy digest and requires a new
challenge. Existing default Verified-only policy digests are preserved. These
checks attest boot/key properties at enrollment, not a protected sensor pipeline
or the absence of every runtime compromise. Actual Pixel 9 Pro attestation
validation remains a device-acceptance gate.
