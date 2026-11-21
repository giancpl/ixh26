"""Explicit v2 receipt profile; independent of the legacy v1 bundle/CLI format."""

from dataclasses import dataclass, replace

from .cbor import decode, encode
from .core import Receipt, commitments, domain
from .hybrid import SUITE, HybridPrivateKey, HybridPublicKey, HybridSignature, verify, verify_hardware_binding


@dataclass(frozen=True)
class HybridReceipt:
    public: HybridPublicKey
    # Reuse v1 field-type/limit validation ONLY. sigma here is the hardware
    # binding signature, not a v1 signature on a photo root.
    fields: Receipt
    signatures: HybridSignature | None = None

    def __post_init__(self):
        if self.fields.pk_I != self.public.hardware_spki:
            raise ValueError("Hardware key mismatch")
        verify_hardware_binding(self.public, self.fields.sigma)

    def roots(self, asset: bytes, credential: bytes | None = None):
        base = commitments(self.fields, asset, credential)
        r = {k: base[k] for k in ("SHA256_J", "SHA256_A") if k in base}
        r["ID_I"] = self.public.device_id
        r["F_I"] = domain("fingerprinto/v2/install", r["ID_I"], self.fields.packageName,
                           self.fields.versionCode, self.fields.signerDigest)
        photo = [r["F_I"], r["SHA256_J"]]
        if "SHA256_A" in r:
            photo.append(r["SHA256_A"])
        r["F_P"] = domain("fingerprinto/v2/photo", *photo)
        values = [("ID_I", r["ID_I"]), ("manufacturer", self.fields.manufacturer),
                  ("model", self.fields.model), ("localTime", self.fields.localTime),
                  ("sessionChallenge", self.fields.sessionChallenge)]
        for i, (name, value) in enumerate(values):
            r[f"L{i}"] = domain("fingerprinto/v2/field", name, value)
        node = lambda a, b: domain("fingerprinto/v2/node", a, b)
        r["F_D"] = node(node(node(r["L0"], r["L1"]), node(r["L2"], r["L3"])), r["L4"])
        r["T"] = node(node(r["F_I"], r["F_P"]), r["F_D"])
        r["R"] = domain("fingerprinto/v2", SUITE, r["T"])
        return r

    def sign(self, key: HybridPrivateKey, asset: bytes, credential: bytes | None = None):
        if self.signatures is not None:
            raise ValueError("Receipt already signed; reuse it rather than re-signing on retry")
        if key.public != self.public:
            raise ValueError("Hybrid signing key mismatch")
        return replace(self, signatures=key.sign(self.roots(asset, credential)["R"]))

    def verify(self, asset: bytes, credential: bytes | None = None, *, expected_device_id: bytes):
        if self.signatures is None:
            raise ValueError("Unsigned hybrid receipt")
        root = self.roots(asset, credential)["R"]
        verify(self.public, root, self.signatures, expected_device_id=expected_device_id)
        return self.commitment(asset, credential)

    def commitment(self, asset: bytes, credential: bytes | None = None):
        """Reconstruct C even for invalid component signatures; no trust decision."""
        if self.signatures is None:
            raise ValueError("Unsigned hybrid receipt")
        root = self.roots(asset, credential)["R"]
        return domain("fingerprinto/hybrid-receipt/v2", self.public.to_bytes(), root,
                      self.signatures.to_bytes(), self.fields.sigma)

    def to_bytes(self):
        if self.signatures is None:
            raise ValueError("Unsigned hybrid receipt")
        f = self.fields
        return encode([2, SUITE, self.public.to_bytes(), list(f.chain), f.packageName, f.versionCode,
                       f.signerDigest, f.manufacturer, f.model, f.localTime, f.sessionChallenge,
                       f.placement, f.sigma, self.signatures.to_bytes()])

    @classmethod
    def from_bytes(cls, data):
        fields = decode(data)
        if type(fields) is not list or len(fields) != 14 or type(fields[0]) is not int or fields[:2] != [2, SUITE]:
            raise ValueError("Unsupported hybrid receipt profile")
        if type(fields[3]) is not list:
            raise ValueError("Invalid certificate chain")
        public = HybridPublicKey.from_bytes(fields[2])
        metadata = Receipt(public.hardware_spki, tuple(fields[3]), *fields[4:13])
        return cls(public, metadata, HybridSignature.from_bytes(fields[13]))


def verify_hybrid_receipt(receipt_bytes: bytes, asset: bytes | None, credential: bytes | None = None,
                          *, expected_device_id: bytes, anchor_evidence: bytes | None = None):
    """Signature/pinned-ID checks only; origin and enrollment remain separate."""
    from cryptography.exceptions import InvalidSignature
    from cryptography.exceptions import UnsupportedAlgorithm
    from cryptography import x509
    from .core import digest
    from .verification import Check, VerificationResult, _certificate

    if type(expected_device_id) is not bytes or len(expected_device_id) != 32:
        raise ValueError("Verifier-owned hybrid device ID must be 32 bytes")
    origin = Check("indeterminate", "origin_validator_unconfigured", "Origin policy and validator are required.")
    enrollment = Check("indeterminate", "hybrid_enrollment_record_required", "A pinned key ID alone does not validate Android enrollment.")
    anchor = (Check("absent", "anchor_evidence_absent", "No anchor evidence supplied.") if anchor_evidence is None else
              Check("indeterminate", "anchor_not_checked", "Anchor evidence has not been verified."))
    try:
        receipt = HybridReceipt.from_bytes(receipt_bytes)
    except (ValueError, TypeError, InvalidSignature):
        return VerificationResult(Check("rejected", "hybrid_profile_invalid", "Invalid v2 encoding or hardware binding."),
                                  origin, Check("invalid", "hybrid_receipt_invalid", "Hybrid receipt is invalid."),
                                  enrollment, anchor, verifier_profile="fingerprinto/v2-hybrid")
    profile = Check("accepted", "hybrid_schema_valid", "v2 encoding and hardware binding pass.")
    try:
        for certificate in receipt.fields.chain:
            _certificate(certificate)
    except (ValueError, UnsupportedAlgorithm, x509.DuplicateExtension):
        profile = Check("rejected", "certificate_encoding_invalid", "Invalid certificate encoding.")
        enrollment = Check("rejected", "certificate_encoding_invalid", "Malformed enrollment evidence.")
    details = {"suite": SUITE, "hardware_binding_signature": "valid",
               "hybrid_device_id": receipt.public.device_id.hex(),
               "expected_hybrid_device_id": expected_device_id.hex()}
    computed = {}
    signature = Check("indeterminate", "asset_missing", "Original asset is required.")
    if asset is not None:
        if receipt.fields.placement == 1 and credential is None:
            signature = Check("indeterminate", "credential_missing", "Detached origin credential is required.")
        else:
            try:
                roots = receipt.roots(asset, credential)
                commitment = receipt.commitment(asset, credential)
                computed = {name: roots[name].hex() for name in ("ID_I", "T", "R")}
                computed.update(C=commitment.hex(), OTS_SHA256_C=digest(commitment).hex())
            except (ValueError, TypeError):
                profile = Check("rejected", "asset_profile_invalid", "Asset or credential violates the profile.")
                signature = Check("indeterminate", "asset_profile_invalid", "Cannot reconstruct the signed root.")
            else:
                try:
                    verify(receipt.public, roots["R"], receipt.signatures, expected_device_id=expected_device_id)
                except InvalidSignature:
                    signature = Check("invalid", "hybrid_signature_or_identity_mismatch", "Both signatures and the enrolled hybrid ID must match.")
                else:
                    signature = Check("valid", "hybrid_signatures_valid", "ML-DSA-65 and Ed25519 both verify for the expected hybrid device.")
    return VerificationResult(profile, origin, signature, enrollment, anchor, computed,
                              enrollment_details=details, verifier_profile="fingerprinto/v2-hybrid")
