"""Run without pytest/OTS dependencies: unittest discover -s tests/fingerprinto."""

from dataclasses import replace
import json
from pathlib import Path
import unittest

from fingerprinto import Receipt, commitments
from fingerprinto.cbor import decode, encode


class CoreTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = json.loads((Path(__file__).parent / "fixtures/v1.json").read_text())

    def test_all_document_vectors(self):
        for case in self.vectors["cases"]:
            with self.subTest(placement=case["placement"], challenge=case["sessionChallenge"]):
                raw = bytes.fromhex(case["receipt_cbor"])
                receipt = Receipt.from_bytes(raw)
                self.assertEqual(receipt.to_bytes(), raw)
                result = commitments(receipt, bytes.fromhex(case["J"]),
                                     bytes.fromhex(case["A"]) if case["A"] else None)
                self.assertEqual({k: v.hex() for k, v in result.items()}, case["expected"])
                self.assertEqual(receipt.pk_I.hex(), self.vectors["shared"]["pk_I"])

    def test_reject_noncanonical_and_unsupported_cbor(self):
        for raw in (b"", b"\x18\x01", b"\x19\x00\xff", b"\x9f\xff",
                    b"\xf5", b"\xa0", b"\xc0\x01", b"\xfa\0\0\0\0",
                    b"\x61\xff", b"\x01\x01", b"\x5b" + b"\xff" * 8,
                    b"\x81" * 4 + b"\x00", b"\0" * (256 * 1024 + 1)):
            with self.subTest(raw=raw[:12]):
                with self.assertRaises(ValueError):
                    decode(raw)

    def test_receipt_schema(self):
        raw = bytes.fromhex(self.vectors["cases"][0]["receipt_cbor"])
        fields = decode(raw)
        for index, value in ((0, 0), (0, 2), (2, b"bad"), (2, []), (1, b"x"),
                             (3, ""), (3, "a" * 256), (4, -1), (4, 2**63),
                             (5, b"x" * 31), (8, 2**63), (9, b""),
                             (10, 2), (11, b"")):
            changed = fields.copy()
            changed[index] = value
            with self.subTest(index=index, value=str(value)[:30]):
                with self.assertRaises(ValueError):
                    Receipt.from_bytes(encode(changed))
        with self.assertRaises(ValueError):
            Receipt.from_bytes(encode(fields[:-1]))

    def test_byte_binding_and_placement(self):
        case = self.vectors["cases"][2]
        r = Receipt.from_bytes(bytes.fromhex(case["receipt_cbor"]))
        asset, credential = bytes.fromhex(case["J"]), bytes.fromhex(case["A"])
        original = commitments(r, asset, credential)
        for changed in (commitments(r, asset + b"x", credential),
                        commitments(r, asset, credential + b"x"),
                        commitments(replace(r, localTime=r.localTime + 1), asset, credential),
                        commitments(replace(r, sigma=r.sigma[:-1] + bytes([r.sigma[-1] ^ 1])), asset, credential)):
            self.assertNotEqual(changed["C"], original["C"])
        for receipt, evidence in ((r, None), (replace(r, placement=0), credential)):
            with self.assertRaises(ValueError):
                commitments(receipt, asset, evidence)


if __name__ == "__main__":
    unittest.main()
