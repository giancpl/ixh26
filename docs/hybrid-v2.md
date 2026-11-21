# Experimental hybrid v2 crypto module

This implements the requested ML-DSA + EdDSA experiment as **ML-DSA-65 AND
Ed25519**. Both signatures must pass; there is no single-signature fallback.
It is a new version and transcript, not a change to the frozen v1 vectors.
No RSA device keys are generated or used. Enrollment paths are now EC-only;
RSA issuer chains return an unsupported outcome rather than being trusted.

## Hardware protection boundary

The architecture approved for this branch has three distinct parts:

1. A non-exportable P-256 installation key in hardware Android Keystore, with
   Android attestation and an externally issued enrollment challenge.
2. Software ML-DSA-65 and Ed25519 signing keys, encrypted at rest by an AES-256
   GCM key generated inside hardware Android Keystore.
3. A P-256 signature binding the full hybrid public-key descriptor to the
   installation key. The independent enrollment registry must retain the exact
   accepted hybrid device ID, not merely the hardware SPKI.

`InstallationKeys` now rejects software-only local keys through KeyInfo on
generation/load/signing. `HardwareHybridVault` requires hardware backing for its
AES key as well. StrongBox can be required explicitly; no downgrade on failure
is performed. Both local state directories are excluded from Android backup.

The hybrid private keys **enter application RAM during signing**. The AES key
and hardware installation private key do not. This is not native ML-DSA or
Ed25519 execution in StrongBox. The module erases owned temporary key buffers
where possible; JVM/native-library copies and garbage collection prevent a
guarantee that every secret copy is zeroized. Neither the P-256 attestation nor
its binding signature remotely proves AES wrapping or PQ-key memory residency.
Those are properties of the local implementation that require device testing.

Hardware P-256 identity/attestation remains a classical dependency. An ML-DSA
component does not make the whole enrollment system post-quantum. Pinning the
accepted hybrid ID prevents substituting a different key pair during later
receipt verification. Do not take `expected_device_id` from the receipt itself.

## Exact public and signature encodings

All arrays use the same restricted deterministic CBOR rules as v1. Binary
fields are byte strings. The suite string is exactly:

```text
suite = "fingerprinto/v2/ml-dsa-65+ed25519"
public = [2, suite, hardware_spki, ed25519_public, ml_dsa_65_public]
device_id = D("fingerprinto/hybrid-device/v2", CBOR(public))
binding_root = D("fingerprinto/device-binding/v2", suite, CBOR(public))
binding_signature = SHA256withECDSA(hardware_private_key, binding_root)
message = CBOR(["fingerprinto/hybrid-signature/v2", suite, device_id, R])
signature = [2, suite, ed25519_signature, ml_dsa_65_signature]
```

Ed25519 signs `message` directly, using pure Ed25519 (not Ed25519ph). ML-DSA-65
signs the same message using pure FIPS 204 ML-DSA with empty context (not
HashML-DSA). Public-key lengths are 32 and 1952 bytes; signature lengths are
64 and 3309 bytes. Each algorithm commits to the identity of **both** keys
through device_id, preventing component mixing. Binding signatures use strict
DER and are preserved byte for byte.

## Receipt construction

`HybridReceipt` in Python and `HybridReceiptV2` in Java share this profile:

```text
F_I = D("fingerprinto/v2/install", device_id, packageName, versionCode, signerDigest)
F_P = D("fingerprinto/v2/photo", F_I, SHA256(J))                    # embedded
F_P = D("fingerprinto/v2/photo", F_I, SHA256(J), SHA256(A))         # detached
L_i = D("fingerprinto/v2/field", name_i, value_i)
Node(a,b) = D("fingerprinto/v2/node", a, b)
F_D = Node(Node(Node(L_0,L_1),Node(L_2,L_3)),L_4)
T = Node(Node(F_I,F_P),F_D)
R = D("fingerprinto/v2", suite, T)
C = D("fingerprinto/hybrid-receipt/v2", CBOR(public), R,
      CBOR(signature), binding_signature)
```

Device fields retain v1 order and meanings: ID_I (now device_id), manufacturer,
model, localTime, sessionChallenge. SHA-256 commitment limitations still apply;
the ML-DSA parameter set is not a claim of matching security strength for every
other component. No photo origin, capture time or truth guarantee is added.

```text
receipt_v2.cbor = [
  2, suite, CBOR(public), chain, packageName, versionCode, signerDigest,
  manufacturer, model, localTime, sessionChallenge, placement,
  binding_signature, CBOR(signature)
]
```

CBOR(public) and CBOR(signature) here are individual byte strings containing
the encoded arrays. Field limits/placement rules are inherited from v1. The
hardware SPKI is exactly the v1 P-256 SPKI format. Chains are enrollment evidence,
not automatically accepted by receipt signature verification.

Both v1 and v2 decoders reject the other profile. Select the expected profile
from trusted enrollment/application policy; do not downgrade after failure.
The existing v1 commands remain v1. Explicit hybrid bundle/CLI/OTS entry points
use C with the same 32-byte commitment-file contract. Do not submit a photo
digest as the v2 receipt commitment.

## Bundle, CLI and Bitcoin transport

Use the hybrid extra on Windows for offline tools and a POSIX environment for
Bitcoin/OTS. Commands require an existing signed v2 receipt; they never generate
device secrets. Pack/inspect make no trust decision. Keep `device_id` below in
trusted configuration copied from the accepted enrollment registry, not from the
bundle's inspection output.

```sh
python -m fingerprinto pack-hybrid --receipt receipt_v2.cbor --asset original.jpg --out evidence-v2.zip
# Add --credential credential.bin for detached origin evidence.
python -m fingerprinto inspect-hybrid evidence-v2.zip
python -m fingerprinto verify-hybrid evidence-v2.zip --expected-device-id <trusted-64-hex-id>
python -m fingerprinto stamp-hybrid evidence-v2.zip --expected-device-id <trusted-64-hex-id> --network mainnet --out anchors
# Later: retrieve attestations with the existing opaque-proof upgrader.
python -m settlement upgrade anchors/<C-hex>/commitment.bin.ots --network mainnet
# Re-pack to a NEW destination with --ots-proof anchors/<C-hex>/commitment.bin.ots.
python -m fingerprinto verify-hybrid-ots evidence-v2-with-proof.zip --expected-device-id <trusted-64-hex-id> --network mainnet --bitcoin-conf /absolute/path/bitcoin.conf
```

The ZIP member layout and limits match v1. Library callers choose
`EvidenceBundle(..., profile="hybrid-v2")` and
`import_bundle(path, profile="hybrid-v2")` explicitly. Import reconstructs C and
checks `commitment.bin`; it does not accept origin or enrollment. Structurally
valid but invalid component signatures can be transported for diagnosis and
then fail verification. Invalid hardware binding is rejected by the v2 decoder.

Use `verify_bundle_hybrid` / `verify_bundle_hybrid_ots` with a required
`expected_device_id`; their structured outputs keep signature, origin, enrollment
and anchor outcomes separate. A key pin and hardware binding do not substitute
for retained Android enrollment evidence. That outcome remains indeterminate
until a trusted enrollment-decision integration is configured. Origin also
remains indeterminate without a configured validator and policy.

`stamp_hybrid_receipt` verifies both signatures and expected identity before
publishing C or calling calendars. It reuses settlement's idempotence and locks;
success is PENDING, never cryptographic confirmation. The proof is always bound
to SHA256(C). Verification does not upgrade, write files or contact calendars.
Network choice comes from the verifier's configuration, never anchor.json.

Pack/inspect and successful submission return exit 0 for their operation.
Verification returns 3 for an invalid/rejected/failed check and 2 when required
checks remain indeterminate/absent/pending. Thus even a Bitcoin-verified anchor
with valid hybrid signatures returns 2 while origin/enrollment are unresolved.
Usage/dependency/input/I/O failures return 1. Neither legacy profile decoding
nor a single component is used as a fallback.

## Keys, wrapping and retries

`HardwareHybridVault.create` generates both software keys, obtains the hardware
binding signature, creates the hardware AES key and atomically persists only
an authenticated encrypted private payload. Existing aliases/records and
incomplete provisioning states require explicit recovery rather than replacement.
Loading checks the expected hybrid ID, current hardware installation ID and
hardware-binding signature. Decrypt/sign operations erase module-owned buffers
on exit. AES-GCM uses a fresh provider-generated 12-byte IV and a 128-bit tag.

The authenticated associated data is:

```text
CBOR(["fingerprinto/hardware-wrapped-keys/v2", suite,
      device_id, hardware_installation_id, binding_signature])
```

The encrypted plaintext has this fixed internal layout (6143 bytes):

```text
"FPH" || 0x02 || hardware_spki[91] || ed_public[32] || ml_public[1952]
             || ed_seed[32] || ml_expanded_secret[4032]
```

The wrapped record is `"FPV" || 0x02 || device_id[32] || hardware_id[32] ||
binding_length[1] || binding_signature || iv[12] || ciphertext_with_tag`.
This is internal storage, not a portable secret-key export. Wrong IDs, altered
metadata, wrong AES keys and modified ciphertext fail. Lost hardware keys mean
the encrypted keys cannot be restored; a new enrollment/identity is required.

`SignedReceiptJournal` (Android) and `ReceiptJournal` (Python) retain exact signed
receipt bytes under a persistent operation ID and immutable request digest.
Signing occurs under a lock/transaction; retries return stored bytes and reject
changed input digests. Submit to calendars/ledger **only after** journal storage
returns successfully. Producers must perform no network/external side effects.
These are persistence mechanisms, not origin/enrollment decisions; stored data
must still be independently verified. Whole-operation crash recovery, portable
authenticated enrollment decisions and real origin acceptance remain separate.

## Validation and dependencies

Python uses cryptography Ed25519 and pinned pqcrypto 0.4.0 (PQClean ML-DSA).
Install `.[hybrid]` or `.[test]`. Java uses Bouncy Castle 1.87 lightweight APIs,
without registering a global provider or replacing AndroidKeyStore.

```sh
python -m unittest discover -s tests/fingerprinto -v
# JDK 17+, a Python environment with the hybrid extra, no Android SDK:
bash android/tests/run-hybrid-tests.sh /path/to/bcprov-jdk18on-1.87.jar
```

Real signatures cross-verify Java↔Python. Java v2 receipt roots, wire bytes and C
match Python. Tests cover each signature failing independently, swapped keys,
wrong root/ID, bad hardware binding, AES tampering/key mismatch, fresh nonces,
private-payload reload, v1/v2 rejection, concurrent journal use, restart reuse
and failure rollback. Android-specific adapters remain source-reviewed only:
no app build, Android hardware test, C2PA acceptance or real-chain check occurred.

References: [Android Keystore/StrongBox capabilities](https://developer.android.com/privacy-and-security/keystore),
[Bouncy Castle Java](https://www.bouncycastle.org/download/bouncy-castle-java/),
[pqcrypto](https://github.com/backbone-hq/pqcrypto),
[ML-DSA standard](https://csrc.nist.gov/pubs/fips/204/final).
