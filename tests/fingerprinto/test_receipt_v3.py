from dataclasses import replace
import json
from pathlib import Path
import tempfile
import unittest

from cryptography.exceptions import InvalidSignature

from fingerprinto.bundle import EvidenceBundle, export_bundle, import_bundle
from fingerprinto.cbor import decode, encode
from fingerprinto.core import Receipt, digest
from fingerprinto.receipt_v3 import Evidence, ReceiptV3, json_object


FIXTURE = Path(__file__).parent / "fixtures/v3.json"


class ReceiptV3Tests(unittest.TestCase):
    def test_vectors_and_transport(self):
        for case in json.loads(FIXTURE.read_text())["cases"]:
            raw, asset = bytes.fromhex(case["receipt"]), bytes.fromhex(case["asset"])
            credential = None if case["credential"] is None else bytes.fromhex(case["credential"])
            receipt = ReceiptV3.from_bytes(raw)
            self.assertEqual(raw, receipt.to_bytes())
            roots = receipt.verify_signature(asset, credential)
            self.assertEqual(case["roots"], {k: v.hex() for k, v in roots.items()})
            receipt.evidence.attachments(case["policy"].encode(), case["report"].encode())
            with self.assertRaises(InvalidSignature):
                receipt.verify_signature(asset + b"tampered", credential)
            with self.assertRaises(ValueError):
                Receipt.from_bytes(raw)
            bundle = EvidenceBundle(raw, asset, credential, profile="evidence-v3",
                                    policy=case["policy"].encode(), report=case["report"].encode())
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "v3.zip"
                export_bundle(bundle, path)
                self.assertEqual(bundle, import_bundle(path, profile="evidence-v3"))
                with self.assertRaises(ValueError):
                    import_bundle(path)

    def test_attachment_limits_json_and_disagreement(self):
        for raw in (b'{"x":0,"x":1}', b'{"x":{"a":1,"a":2}}', b'{"x":NaN}',
                    b'{"x":1e999}', b'[]', b'{"x":"\\ud800"}', b'{}' * 600000,
                    b'{"x":' + b'[' * 20 + b'0' + b']' * 20 + b'}'):
            with self.subTest(raw=raw[:40]), self.assertRaises(ValueError):
                json_object(raw)
        case = json.loads(FIXTURE.read_text())["cases"][0]
        receipt = ReceiptV3.from_bytes(bytes.fromhex(case["receipt"]))
        policy, report = case["policy"].encode(), case["report"].encode()
        for p, r in ((policy + b" ", report), (policy, report + b" "), (None, report)):
            with self.assertRaises(ValueError):
                receipt.evidence.attachments(p, r)
        changed = json.loads(report)
        changed["manufacturer"]["status"] = "pass"
        raw = json.dumps(changed).encode()
        with self.assertRaises(ValueError):
            replace(receipt.evidence, report_hash=digest(raw)).attachments(policy, raw)
        with self.assertRaises(ValueError):
            receipt.evidence.attachments(policy, report, b"wrong reference")

    def test_schema_placement_and_evidence_tampering(self):
        cases = json.loads(FIXTURE.read_text())["cases"]
        f = decode(bytes.fromhex(cases[0]["receipt"]))
        for index, value in ((0, 1), (0, True), (10, []), (10, [2, None, 0, 0, None, None, None])):
            changed = f.copy()
            changed[index] = value
            with self.assertRaises(ValueError):
                ReceiptV3.from_bytes(encode(changed))
        receipt = ReceiptV3.from_bytes(bytes.fromhex(cases[0]["receipt"]))
        for evidence in (replace(receipt.evidence, report_hash=b"a" * 32),
                         replace(receipt.evidence, ref_hash=b"b" * 32),
                         replace(receipt.evidence, policy_hash=b"c" * 32)):
            with self.assertRaises(InvalidSignature):
                replace(receipt, evidence=evidence).verify_signature(bytes.fromhex(cases[0]["asset"]))
        with self.assertRaises(ValueError):
            replace(receipt.evidence, placement=True)
        detached = next(c for c in cases if c["credential"] is not None)
        r = ReceiptV3.from_bytes(bytes.fromhex(detached["receipt"]))
        for credential in (None, b"different"):
            with self.assertRaises(ValueError):
                r.roots(bytes.fromhex(detached["asset"]), credential)
