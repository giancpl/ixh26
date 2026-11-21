"""Exercise the public CLI in fresh Python processes with no network access."""

from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from fingerprinto import Receipt
from fingerprinto.bundle import EvidenceBundle, export_bundle


class CLITests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = json.loads((Path(__file__).parent / "fixtures/v1.json").read_text())

    def run_cli(self, *args):
        result = subprocess.run([sys.executable, "-B", "-m", "fingerprinto", *map(str, args)],
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.stderr, "")
        return result.returncode, json.loads(result.stdout)

    def test_pack_inspect_verify_all_placements(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index, case in enumerate(self.vectors["cases"]):
                receipt, asset, credential = root / "receipt", root / "asset", root / "credential"
                receipt.write_bytes(bytes.fromhex(case["receipt_cbor"]))
                asset.write_bytes(bytes.fromhex(case["J"]))
                args = ["pack", "--receipt", receipt, "--asset", asset, "--out", root / f"{index}.zip"]
                if case["A"]:
                    credential.write_bytes(bytes.fromhex(case["A"]))
                    args += ["--credential", credential]
                code, packed = self.run_cli(*args)
                self.assertEqual(code, 0)
                self.assertFalse(packed["trust_evaluated"])
                code, info = self.run_cli("inspect", packed["bundle"])
                self.assertEqual(code, 0)
                self.assertEqual(info["commitment"], case["expected"]["C"])
                self.assertFalse(info["trust_evaluated"])
                code, verified = self.run_cli("verify", packed["bundle"])
                self.assertEqual(code, 2)
                self.assertEqual(verified["installation_signature"]["status"], "valid")
                self.assertEqual(verified["origin"]["status"], "indeterminate")
                self.assertEqual(verified["enrollment"]["status"], "indeterminate")

    def test_bad_signature_is_exit_three(self):
        case = self.vectors["cases"][0]
        receipt = replace(Receipt.from_bytes(bytes.fromhex(case["receipt_cbor"])), localTime=0)
        with tempfile.TemporaryDirectory() as directory:
            path = export_bundle(EvidenceBundle(receipt.to_bytes(), bytes.fromhex(case["J"])), Path(directory) / "bad.zip")
            code, result = self.run_cli("verify", path)
            self.assertEqual(code, 3)
            self.assertEqual(result["installation_signature"]["status"], "invalid")

    def test_errors_are_one_json_and_network_is_explicit(self):
        for args in ((), ("wrong-command",), ("verify", "missing.zip"),
                     ("verify-ots", "missing.zip"),
                     ("verify-ots", "missing.zip", "--network", "testnet")):
            code, result = self.run_cli(*args)
            self.assertEqual(code, 1)
            self.assertEqual(result["status"], "error")

    def test_pack_never_overwrites(self):
        case = self.vectors["cases"][0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "receipt").write_bytes(bytes.fromhex(case["receipt_cbor"]))
            (root / "asset").write_bytes(bytes.fromhex(case["J"]))
            output = root / "keep.zip"
            output.write_bytes(b"keep this")
            code, _ = self.run_cli("pack", "--receipt", root / "receipt", "--asset", root / "asset", "--out", output)
            self.assertEqual(code, 1)
            self.assertEqual(output.read_bytes(), b"keep this")

    def test_limits_apply_before_reading(self):
        from fingerprinto.cli import _read
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "large"
            path.write_bytes(b"12345")
            with self.assertRaises(ValueError):
                _read(str(path), 4)


if __name__ == "__main__":
    unittest.main()
