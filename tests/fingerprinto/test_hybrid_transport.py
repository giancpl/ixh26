from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from fingerprinto.bundle import EvidenceBundle, export_bundle, import_bundle, verify_bundle, verify_bundle_hybrid
from fingerprinto.core import Receipt
try:
    from fingerprinto.hybrid import HybridPrivateKey, HybridSignature
    from fingerprinto.hybrid_receipt import HybridReceipt, verify_hybrid_receipt
except ModuleNotFoundError as error:
    if error.name != "pqcrypto":
        raise
    HybridPrivateKey = None


@unittest.skipIf(HybridPrivateKey is None, "Install the hybrid extra")
class HybridTransportTests(unittest.TestCase):
    def setUp(self):
        hardware = ec.generate_private_key(ec.SECP256R1())
        spki = hardware.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.key = HybridPrivateKey.generate(spki)
        binding = hardware.sign(self.key.public.binding_root, ec.ECDSA(hashes.SHA256()))
        fixture = json.loads((Path(__file__).parent / "fixtures/v1.json").read_text())
        chain = tuple(bytes.fromhex(cert) for cert in fixture["shared"]["chain"])
        # DER-valid synthetic chain is only syntax evidence, not accepted enrollment.
        self.fields = Receipt(spki, chain, "example.photo", 1, bytes(32), "Fixture", "Model", 123, None, 0, binding)
        self.asset = b"exact synthetic asset"
        self.receipt = HybridReceipt(self.key.public, self.fields).sign(self.key, self.asset)
        self.identity = self.key.public.device_id

    def bundle(self, receipt=None):
        return EvidenceBundle((receipt or self.receipt).to_bytes(), self.asset, profile="hybrid-v2")

    def cli(self, *args):
        result = subprocess.run([sys.executable, "-B", "-m", "fingerprinto", *map(str, args)],
                                capture_output=True, text=True, timeout=20)
        self.assertEqual(result.stderr, "")
        return result.returncode, json.loads(result.stdout)

    def test_roundtrip_explicit_profile_and_independent_results(self):
        with tempfile.TemporaryDirectory() as directory:
            path = export_bundle(self.bundle(), Path(directory) / "v2.zip")
            restored = import_bundle(path, profile="hybrid-v2")
            self.assertEqual(restored, self.bundle())
            result = verify_bundle_hybrid(restored, expected_device_id=self.identity)
            self.assertEqual(result.installation_signature.status, "valid")
            self.assertEqual(result.origin.status, "indeterminate")
            self.assertEqual(result.enrollment.status, "indeterminate")
            with self.assertRaises(ValueError):
                import_bundle(path)  # Never infer a downgrade/profile switch.
            with self.assertRaises(ValueError):
                verify_bundle(restored)

    def test_pack_inspect_verify_cli_and_required_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "receipt").write_bytes(self.receipt.to_bytes())
            (root / "asset").write_bytes(self.asset)
            path = root / "v2.zip"
            code, result = self.cli("pack-hybrid", "--receipt", root / "receipt", "--asset", root / "asset", "--out", path)
            self.assertEqual(code, 0)
            self.assertFalse(result["trust_evaluated"])
            self.assertEqual(self.cli("inspect-hybrid", path)[1]["receipt_version"], 2)
            code, result = self.cli("verify-hybrid", path, "--expected-device-id", self.identity.hex())
            self.assertEqual(code, 2)
            self.assertEqual(result["installation_signature"]["status"], "valid")
            self.assertEqual(self.cli("verify-hybrid", path)[0], 1)
            self.assertEqual(self.cli("verify-hybrid", path, "--expected-device-id", "bad")[0], 1)
            self.assertEqual(self.cli("verify-hybrid", path, "--expected-device-id", "00" * 32)[0], 3)
            self.assertEqual(self.cli("verify", path)[0], 1)
            self.assertEqual(self.cli("verify-hybrid-ots", path, "--expected-device-id", self.identity.hex())[0], 1)

    def test_invalid_component_is_transportable_but_fails_verification(self):
        changed = replace(self.receipt, signatures=HybridSignature(bytes(64), self.receipt.signatures.ml_dsa))
        with tempfile.TemporaryDirectory() as directory:
            path = export_bundle(self.bundle(changed), Path(directory) / "bad.zip")
            result = verify_bundle_hybrid(import_bundle(path, profile="hybrid-v2"), expected_device_id=self.identity)
            self.assertEqual(result.installation_signature.status, "invalid")
            self.assertIn("C", result.computed)
            self.assertEqual(self.cli("verify-hybrid", path, "--expected-device-id", self.identity.hex())[0], 3)

    def test_wrong_bundle_commitment_and_hardware_binding_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.zip"
            files = self.bundle().files()
            files["commitment.bin"] = bytes(32)
            with zipfile.ZipFile(path, "w") as archive:
                for name, data in files.items():
                    archive.writestr(name, data)
            with self.assertRaises(ValueError):
                import_bundle(path, profile="hybrid-v2")
        from fingerprinto.cbor import decode, encode
        fields = decode(self.receipt.to_bytes())
        fields[12] = b"\x30\x06\x02\x01\x01\x02\x01\x01"
        result = verify_hybrid_receipt(encode(fields), self.asset, expected_device_id=self.identity)
        self.assertEqual(result.profile.status, "rejected")

    def test_detached_and_missing_evidence(self):
        credential = b"synthetic detached origin evidence"
        fields = replace(self.fields, placement=1, sessionChallenge=bytes(32))
        receipt = HybridReceipt(self.key.public, fields).sign(self.key, self.asset, credential)
        bundle = EvidenceBundle(receipt.to_bytes(), self.asset, credential, profile="hybrid-v2")
        with tempfile.TemporaryDirectory() as directory:
            path = export_bundle(bundle, Path(directory) / "detached.zip")
            self.assertEqual(verify_bundle_hybrid(import_bundle(path, profile="hybrid-v2"), expected_device_id=self.identity).installation_signature.status, "valid")
        result = verify_hybrid_receipt(receipt.to_bytes(), self.asset, expected_device_id=self.identity)
        self.assertEqual(result.installation_signature.code, "credential_missing")


if __name__ == "__main__":
    unittest.main()
