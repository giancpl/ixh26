"""Experimental v2 ML-DSA-65 AND Ed25519 signatures bound to an enrolled device.

Python generation is for independent tooling/tests. Android secrets are software
signing material wrapped at rest by hardware Keystore AES, not non-exportable
ML-DSA/Ed25519 keys. There is no v1 fallback and no origin/enrollment acceptance.
"""

from dataclasses import dataclass, field
import hmac

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from pqcrypto.sign import ml_dsa_65

from .cbor import decode, encode
from .core import domain
from .verification import load_installation_key, verify_installation_signature

SUITE = "fingerprinto/v2/ml-dsa-65+ed25519"


def _bytes(value, size):
    if type(value) is not bytes or len(value) != size:
        raise ValueError("Invalid hybrid field length/type")
    return value


@dataclass(frozen=True)
class HybridPublicKey:
    hardware_spki: bytes
    ed25519: bytes
    ml_dsa: bytes

    def __post_init__(self):
        load_installation_key(self.hardware_spki)
        _bytes(self.ed25519, 32)
        _bytes(self.ml_dsa, 1952)

    def to_bytes(self):
        return encode([2, SUITE, self.hardware_spki, self.ed25519, self.ml_dsa])

    @classmethod
    def from_bytes(cls, data):
        fields = decode(data)
        if type(fields) is not list or len(fields) != 5 or type(fields[0]) is not int or fields[:2] != [2, SUITE]:
            raise ValueError("Unsupported hybrid key profile")
        return cls(*fields[2:])

    @property
    def device_id(self):
        return domain("fingerprinto/hybrid-device/v2", self.to_bytes())

    @property
    def binding_root(self):
        return domain("fingerprinto/device-binding/v2", SUITE, self.to_bytes())


def _transcript(public: HybridPublicKey, root: bytes) -> bytes:
    return encode(["fingerprinto/hybrid-signature/v2", SUITE, public.device_id, _bytes(root, 32)])


@dataclass(frozen=True)
class HybridSignature:
    ed25519: bytes
    ml_dsa: bytes

    def __post_init__(self):
        _bytes(self.ed25519, 64)
        _bytes(self.ml_dsa, 3309)

    def to_bytes(self):
        return encode([2, SUITE, self.ed25519, self.ml_dsa])

    @classmethod
    def from_bytes(cls, data):
        fields = decode(data)
        if type(fields) is not list or len(fields) != 4 or type(fields[0]) is not int or fields[:2] != [2, SUITE]:
            raise ValueError("Unsupported hybrid signature profile")
        return cls(*fields[2:])


@dataclass(frozen=True)
class HybridPrivateKey:
    public: HybridPublicKey
    ed25519_seed: bytes = field(repr=False)
    ml_dsa_secret: bytes = field(repr=False)

    def __post_init__(self):
        _bytes(self.ed25519_seed, 32)
        _bytes(self.ml_dsa_secret, 4032)

    @classmethod
    def generate(cls, hardware_spki):
        ed = Ed25519PrivateKey.generate()
        seed = ed.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())
        ed_public = ed.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        pq_public, pq_secret = ml_dsa_65.generate_keypair()
        return cls(HybridPublicKey(hardware_spki, ed_public, pq_public), seed, pq_secret)

    def sign(self, root: bytes) -> HybridSignature:
        transcript = _transcript(self.public, root)
        signature = HybridSignature(Ed25519PrivateKey.from_private_bytes(self.ed25519_seed).sign(transcript),
                                    ml_dsa_65.sign(self.ml_dsa_secret, transcript))
        # Detect mismatched imported public/private components before publication.
        verify(self.public, root, signature, expected_device_id=self.public.device_id)
        return signature


def verify(public: HybridPublicKey, root: bytes, signature: HybridSignature, *, expected_device_id: bytes) -> None:
    """Verify BOTH signatures and a verifier-owned enrolled hybrid device ID."""
    if not hmac.compare_digest(public.device_id, _bytes(expected_device_id, 32)):
        raise InvalidSignature("Hybrid key differs from enrolled device")
    transcript = _transcript(public, root)
    Ed25519PublicKey.from_public_bytes(public.ed25519).verify(signature.ed25519, transcript)
    if not ml_dsa_65.verify(public.ml_dsa, transcript, signature.ml_dsa):
        raise InvalidSignature("ML-DSA signature mismatch")


def verify_hardware_binding(public: HybridPublicKey, binding_signature: bytes) -> None:
    """Verify P-256 proof binding the key pair; Android enrollment is separate."""
    verify_installation_signature(public.hardware_spki, binding_signature, public.binding_root)


def receipt_commitment(public: HybridPublicKey, root: bytes, signature: HybridSignature, binding_signature: bytes) -> bytes:
    verify(public, root, signature, expected_device_id=public.device_id)
    verify_hardware_binding(public, binding_signature)
    return domain("fingerprinto/hybrid-receipt/v2", public.to_bytes(), root,
                  signature.to_bytes(), binding_signature)
