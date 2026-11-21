from dataclasses import replace
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

try:
    from fingerprinto.hybrid import HybridPrivateKey, HybridPublicKey, HybridSignature, verify, verify_hardware_binding, receipt_commitment
except ModuleNotFoundError as error:
    if error.name != "pqcrypto":
        raise
    HybridPrivateKey = None


@unittest.skipIf(HybridPrivateKey is None, "Install the hybrid extra")
class HybridTests(unittest.TestCase):
    def setUp(self):
        self.hardware = ec.generate_private_key(ec.SECP256R1())
        self.spki = self.hardware.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.key = HybridPrivateKey.generate(self.spki)
        self.root = bytes(range(32))

    def test_both_signatures_and_binding(self):
        sig = self.key.sign(self.root)
        verify(self.key.public, self.root, HybridSignature.from_bytes(sig.to_bytes()), expected_device_id=self.key.public.device_id)
        self.assertEqual(HybridPublicKey.from_bytes(self.key.public.to_bytes()), self.key.public)
        binding = self.hardware.sign(self.key.public.binding_root, ec.ECDSA(hashes.SHA256()))
        verify_hardware_binding(self.key.public, binding)
        self.assertEqual(len(receipt_commitment(self.key.public, self.root, sig, binding)), 32)

    def test_each_component_is_required(self):
        sig = self.key.sign(self.root)
        for changed in (replace(sig, ed25519=bytes(64)), replace(sig, ml_dsa=bytes(3309))):
            with self.assertRaises(InvalidSignature):
                verify(self.key.public, self.root, changed, expected_device_id=self.key.public.device_id)
        for root, identity in ((bytes(32), self.key.public.device_id), (self.root, bytes(32))):
            with self.assertRaises(InvalidSignature):
                verify(self.key.public, root, sig, expected_device_id=identity)

    def test_key_mix_and_bad_profiles(self):
        other = HybridPrivateKey.generate(self.spki)
        mixed = replace(self.key.public, ml_dsa=other.public.ml_dsa)
        with self.assertRaises(InvalidSignature):
            verify(mixed, self.root, self.key.sign(self.root), expected_device_id=mixed.device_id)
        for data in (b"", self.key.public.to_bytes() + b"x", b"\x81\x01"):
            with self.assertRaises(ValueError):
                HybridPublicKey.from_bytes(data)
        with self.assertRaises(ValueError):
            HybridSignature(bytes(64), b"")

    def test_wrong_hardware_binding(self):
        wrong = self.hardware.sign(self.root, ec.ECDSA(hashes.SHA256()))
        with self.assertRaises(InvalidSignature):
            verify_hardware_binding(self.key.public, wrong)

    def test_v2_receipt_roundtrip_and_no_downgrade(self):
        from fingerprinto.core import Receipt
        from fingerprinto.hybrid_receipt import HybridReceipt
        binding = self.hardware.sign(self.key.public.binding_root, ec.ECDSA(hashes.SHA256()))
        fields = Receipt(self.spki, (b"synthetic certificate, not enrollment",), "example.photo", 1, bytes(32),
                         "Fixture", "Model", 123, None, 0, binding)
        draft = HybridReceipt(self.key.public, fields)
        signed = draft.sign(self.key, b"original")
        restored = HybridReceipt.from_bytes(signed.to_bytes())
        commitment = restored.verify(b"original", expected_device_id=self.key.public.device_id)
        self.assertEqual(len(commitment), 32)
        self.assertEqual(restored.to_bytes(), signed.to_bytes())
        with self.assertRaises(InvalidSignature):
            restored.verify(b"changed", expected_device_id=self.key.public.device_id)
        with self.assertRaises(ValueError):
            signed.sign(self.key, b"original")
        with self.assertRaises(ValueError):
            Receipt.from_bytes(signed.to_bytes())
        with self.assertRaises(ValueError):
            HybridReceipt.from_bytes(fields.to_bytes())


if __name__ == "__main__":
    unittest.main()
