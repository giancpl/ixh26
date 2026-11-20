"""Offline installation-signature checks, without enrollment/origin trust.

Import this module after installing the ``fingerprinto`` optional dependency.
No network, file writes, trust anchors or application-supplied trust booleans
are used. A valid signature is never promoted to an accepted installation.
"""

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Literal

from cryptography import x509
from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

from .core import Receipt, commitments


P256_ORDER = int("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
# Strict DER algorithm identifiers, BIT STRING with zero unused bits, and
# uncompressed SEC1 point marker. The remaining 64 bytes are affine x and y.
SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d03010703420004")


@dataclass(frozen=True)
class Check:
    status: Literal["valid", "invalid", "accepted", "rejected", "indeterminate", "absent", "pending", "verified", "failed"]
    code: str
    message: str


@dataclass(frozen=True)
class VerificationResult:
    profile: Check
    origin: Check
    installation_signature: Check
    enrollment: Check
    anchor: Check
    # These are reconstructed values, not claims of ledger inclusion.
    computed: dict[str, str] = field(default_factory=dict)
    anchor_details: dict = field(default_factory=dict)
    enrollment_details: dict = field(default_factory=dict)
    evaluated_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    verifier_profile: str = "fingerprinto/v1-signature-only"

    def to_dict(self) -> dict:
        return asdict(self)


def load_installation_key(spki: bytes) -> ec.EllipticCurvePublicKey:
    """Require the exact v1 SPKI encoding and a finite on-curve P-256 point."""
    if type(spki) is not bytes or len(spki) != 91 or not spki.startswith(SPKI_PREFIX):
        raise ValueError("Expected strict uncompressed P-256 SPKI")
    # OpenSSL validates the point; do not implement curve arithmetic here.
    return ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), spki[26:])


def validate_signature_encoding(signature: bytes) -> None:
    if type(signature) is not bytes or not 8 <= len(signature) <= 72:
        raise ValueError("Invalid ECDSA DER size")
    r, s = utils.decode_dss_signature(signature)
    if not (1 <= r < P256_ORDER and 1 <= s < P256_ORDER):
        raise ValueError("ECDSA scalars outside P-256 range")
    if utils.encode_dss_signature(r, s) != signature:
        raise ValueError("Non-canonical ECDSA DER")
    # Both high-s and low-s are permitted. Never normalize the bytes: C binds
    # to the particular signature stored in the receipt.


def verify_installation_signature(spki: bytes, signature: bytes, root: bytes) -> None:
    """Raise ValueError/InvalidSignature on failure; root is R, not its hash."""
    if type(root) is not bytes or len(root) != 32:
        raise ValueError("Expected the 32 raw bytes of R")
    key = load_installation_key(spki)
    validate_signature_encoding(signature)
    # Matches Android SHA256withECDSA: hash R once inside the signature API.
    key.verify(signature, root, ec.ECDSA(hashes.SHA256()))


def _certificate(data: bytes) -> x509.Certificate:
    """Require one bounded DER SEQUENCE, then delegate X.509 parsing."""
    if not 2 <= len(data) <= 16384 or data[0] != 0x30:
        raise ValueError("Invalid certificate envelope")
    length = data[1]
    start = 2
    if length & 0x80:
        count = length & 0x7f
        if not 1 <= count <= 2 or len(data) < 2 + count or data[2] == 0:
            raise ValueError("Invalid DER certificate length")
        length = int.from_bytes(data[2:2 + count], "big")
        if length < 128 or (count == 2 and length < 256):
            raise ValueError("Non-minimal DER certificate length")
        start += count
    if start + length != len(data):
        raise ValueError("Truncated certificate or trailing bytes")
    cert = x509.load_der_x509_certificate(data)
    # Force lazy extension parsing, including duplicate-extension rejection.
    _ = cert.extensions
    if cert.public_bytes(serialization.Encoding.DER) != data:
        raise ValueError("Certificate DER changed on serialization")
    return cert


def verify_receipt(
    receipt_bytes: bytes,
    asset: bytes | None,
    credential: bytes | None = None,
    *,
    anchor_evidence: bytes | None = None,
) -> VerificationResult:
    """Check independent components of supplied evidence, without accepting trust.

    Anchor evidence is opaque in this piece and never verified. Absent means
    none was supplied, not that no anchor exists on any ledger. Certificate
    syntax checking is not chain validation or Android attestation validation.
    """
    origin = Check("indeterminate", "origin_validator_unconfigured",
                   "No authorized camera-credential validator and trust policy configured.")
    enrollment = Check("indeterminate", "enrollment_policy_unconfigured",
                       "Android attestation, enrollment record and trust policy are not evaluated.")
    anchor = (Check("absent", "anchor_evidence_absent", "No anchor evidence supplied.")
              if anchor_evidence is None else
              Check("indeterminate", "anchor_not_checked", "Supplied anchor evidence is not evaluated."))
    try:
        receipt = Receipt.from_bytes(receipt_bytes)
    except (ValueError, TypeError):
        return VerificationResult(
            Check("rejected", "receipt_profile_invalid", "Receipt violates the v1 byte schema or limits."),
            origin, Check("indeterminate", "receipt_unreadable", "Cannot reconstruct the signed receipt."),
            enrollment, anchor)

    profile = Check("accepted", "receipt_schema_valid", "Receipt byte schema and field limits pass.")
    try:
        for data in receipt.chain:
            _certificate(data)
    except (ValueError, UnsupportedAlgorithm, x509.DuplicateExtension):
        profile = Check("rejected", "certificate_encoding_invalid", "Certificate encoding is invalid or unsupported.")
        enrollment = Check("rejected", "certificate_encoding_invalid", "Enrollment contains invalid certificate evidence.")

    computed: dict[str, str] = {}
    signature = Check("indeterminate", "asset_missing", "Original asset is required to reconstruct R.")
    try:
        key = load_installation_key(receipt.pk_I)
        validate_signature_encoding(receipt.sigma)
    except ValueError:
        profile = Check("rejected", "key_or_signature_encoding_invalid", "Key or signature violates the v1 profile.")
        signature = Check("invalid", "key_or_signature_encoding_invalid", "Invalid signing key or signature encoding.")
    else:
        if asset is not None:
            if receipt.placement == 1 and credential is None:
                signature = Check("indeterminate", "credential_missing", "Detached credential is required to reconstruct R.")
            else:
                try:
                    reconstructed = commitments(receipt, asset, credential)
                except ValueError:
                    profile = Check("rejected", "asset_profile_invalid", "Asset or detached evidence violates the profile.")
                    signature = Check("indeterminate", "asset_profile_invalid", "Cannot reconstruct R from supplied evidence.")
                else:
                    computed = {name: reconstructed[name].hex() for name in ("ID_I", "T", "R", "C", "OTS_SHA256_C")}
                    try:
                        key.verify(receipt.sigma, reconstructed["R"], ec.ECDSA(hashes.SHA256()))
                    except InvalidSignature:
                        signature = Check("invalid", "signature_mismatch", "Signature does not match the reconstructed R.")
                    else:
                        signature = Check("valid", "signature_valid", "P-256 signature verifies over the reconstructed R.")
    return VerificationResult(profile, origin, signature, enrollment, anchor, computed)
