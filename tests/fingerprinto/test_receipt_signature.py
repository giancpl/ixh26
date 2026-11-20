"""Real ECDSA checks; fixtures deliberately have no trusted enrollment/origin."""

from dataclasses import replace
from hashlib import sha256
import json
from pathlib import Path
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

from fingerprinto import Receipt, commitments
from fingerprinto.core import domain
from fingerprinto.verification import (
    P256_ORDER, SPKI_PREFIX, load_installation_key, validate_signature_encoding,
    verify_installation_signature, verify_receipt,
)


class VerificationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = json.loads((Path(__file__).parent / "fixtures/v1.json").read_text())

    def fixture(self, index=0):
        case = self.vectors["cases"][index]
        return (Receipt.from_bytes(bytes.fromhex(case["receipt_cbor"])),
                bytes.fromhex(case["J"]),
                bytes.fromhex(case["A"]) if case["A"] else None)

    def test_all_four_real_signatures_and_separate_outcomes(self):
        for index, case in enumerate(self.vectors["cases"]):
            with self.subTest(index=index):
                receipt, asset, credential = self.fixture(index)
                result = verify_receipt(receipt.to_bytes(), asset, credential)
                self.assertEqual(result.profile.status, "accepted")
                self.assertEqual(result.installation_signature.status, "valid")
                self.assertEqual(result.origin.status, "indeterminate")
                self.assertEqual(result.enrollment.status, "indeterminate")
                self.assertEqual(result.anchor.status, "absent")
                self.assertEqual(result.computed["C"], case["expected"]["C"])
                self.assertEqual(result.computed["OTS_SHA256_C"], case["expected"]["OTS_SHA256_C"])
                self.assertEqual(json.loads(json.dumps(result.to_dict()))["installation_signature"]["status"], "valid")

    def test_modifications_break_signature(self):
        receipt, asset, credential = self.fixture(2)
        variants = [(receipt, asset + b"x", credential), (receipt, asset, credential + b"x")]
        for field, value in (("packageName", "other.app"), ("versionCode", 2),
                             ("manufacturer", "Other"), ("model", "Other"),
                             ("localTime", 0), ("sessionChallenge", bytes(32)),
                             ("signerDigest", bytes(32))):
            variants.append((replace(receipt, **{field: value}), asset, credential))
        r, s = utils.decode_dss_signature(receipt.sigma)
        variants.append((replace(receipt, sigma=utils.encode_dss_signature(r, s - 1)), asset, credential))
        for changed, image, evidence in variants:
            with self.subTest(receipt=changed, image_changed=image != asset):
                result = verify_receipt(changed.to_bytes(), image, evidence)
                self.assertEqual(result.installation_signature.status, "invalid")

    def test_no_legacy_domain_or_wrong_prehash_fallback(self):
        receipt, asset, credential = self.fixture()
        values = commitments(receipt, asset, credential)
        # The fixture public key is the P-256 generator (test private scalar 1).
        key = ec.derive_private_key(1, ec.SECP256R1())
        self.assertEqual(key.public_key().public_bytes(serialization.Encoding.DER,
                         serialization.PublicFormat.SubjectPublicKeyInfo), receipt.pk_I)
        for message in (values["T"], domain("fingerprinto/v2", values["T"]), sha256(values["R"]).digest()):
            signature = key.sign(message, ec.ECDSA(hashes.SHA256()))
            with self.assertRaises(InvalidSignature):
                verify_installation_signature(receipt.pk_I, signature, values["R"])
        # Prehashed R incorrectly omits the SHA256(R) required by JCA.
        wrong = key.sign(values["R"], ec.ECDSA(utils.Prehashed(hashes.SHA256())))
        with self.assertRaises(InvalidSignature):
            verify_installation_signature(receipt.pk_I, wrong, values["R"])
        right = key.sign(sha256(values["R"]).digest(), ec.ECDSA(utils.Prehashed(hashes.SHA256())))
        verify_installation_signature(receipt.pk_I, right, values["R"])

    def test_signature_bytes_not_normalized(self):
        receipt, asset, credential = self.fixture()
        r, s = utils.decode_dss_signature(receipt.sigma)
        alternate = replace(receipt, sigma=utils.encode_dss_signature(r, P256_ORDER - s))
        first = verify_receipt(receipt.to_bytes(), asset, credential)
        second = verify_receipt(alternate.to_bytes(), asset, credential)
        self.assertEqual(first.installation_signature.status, "valid")
        self.assertEqual(second.installation_signature.status, "valid")
        self.assertEqual(first.computed["R"], second.computed["R"])
        self.assertNotEqual(first.computed["C"], second.computed["C"])

    def test_invalid_key_encodings_and_points(self):
        receipt, _, _ = self.fixture()
        other_curve = ec.generate_private_key(ec.SECP256K1()).public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        for spki in (receipt.pk_I + b"x", receipt.pk_I[:-1], other_curve,
                     SPKI_PREFIX + bytes(64), receipt.pk_I[:25] + b"\x01" + receipt.pk_I[26:],
                     receipt.pk_I[:26] + b"\x02" + receipt.pk_I[27:]):
            with self.subTest(spki=spki.hex()):
                with self.assertRaises(ValueError):
                    load_installation_key(spki)

    def test_signature_der_is_strict(self):
        receipt, _, _ = self.fixture()
        bad = [b"", receipt.sigma + b"x", receipt.sigma[:-1],
               bytes.fromhex("300702020001020101"),  # unnecessary INTEGER padding
               bytes.fromhex("3006020180020101"),  # negative integer
               bytes.fromhex("308106020101020101"),  # unnecessary long length
               bytes.fromhex("3009020101020101020101")]  # third integer
        bad += [utils.encode_dss_signature(r, s) for r, s in
                ((0, 1), (1, 0), (P256_ORDER, 1), (1, P256_ORDER))]
        for signature in bad:
            with self.subTest(signature=signature.hex()):
                with self.assertRaises(ValueError):
                    validate_signature_encoding(signature)

    def test_bad_certificates_do_not_hide_valid_signature(self):
        receipt, asset, credential = self.fixture()
        for certificate in (receipt.chain[0] + b"x", receipt.chain[0][:-1], b"not DER",
                            b"\x30\x80\0\0", b"\x30\x81\x00"):
            changed = replace(receipt, chain=(certificate,))
            result = verify_receipt(changed.to_bytes(), asset, credential)
            self.assertEqual(result.profile.status, "rejected")
            self.assertEqual(result.enrollment.status, "rejected")
            self.assertEqual(result.installation_signature.status, "valid")

    def test_missing_or_untrusted_evidence_never_accepts_trust(self):
        receipt, asset, credential = self.fixture(2)
        missing = verify_receipt(receipt.to_bytes(), None, credential)
        self.assertEqual(missing.installation_signature.code, "asset_missing")
        missing = verify_receipt(receipt.to_bytes(), asset)
        self.assertEqual(missing.installation_signature.code, "credential_missing")
        supplied = verify_receipt(receipt.to_bytes(), asset, credential, anchor_evidence=b"verified=true")
        self.assertEqual(supplied.anchor.status, "indeterminate")
        self.assertEqual(supplied.origin.status, "indeterminate")
        self.assertEqual(supplied.enrollment.status, "indeterminate")
        malformed = verify_receipt(b"not CBOR", asset)
        self.assertEqual(malformed.profile.status, "rejected")
        self.assertEqual(malformed.installation_signature.status, "indeterminate")


if __name__ == "__main__":
    unittest.main()
