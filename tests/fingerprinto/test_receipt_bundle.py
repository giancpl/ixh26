"""Evidence transport tests: byte preservation and hostile ZIP inputs."""

from dataclasses import replace
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

from fingerprinto.bundle import EvidenceBundle, export_bundle, import_bundle, verify_bundle


class BundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = json.loads((Path(__file__).parent / "fixtures/v1.json").read_text())

    def fixture(self, index=0):
        case = self.vectors["cases"][index]
        return EvidenceBundle(bytes.fromhex(case["receipt_cbor"]), bytes.fromhex(case["J"]),
                              bytes.fromhex(case["A"]) if case["A"] else None)

    def write_zip(self, path, files, *, compressed=False):
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED) as out:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                for name, data in files:
                    out.writestr(name, data)

    def test_round_trip_all_vectors(self):
        with tempfile.TemporaryDirectory() as directory:
            for index in range(4):
                bundle = self.fixture(index)
                path = Path(directory) / f"{index}.zip"
                export_bundle(bundle, path)
                imported = import_bundle(path)
                self.assertEqual(bundle, imported)
                self.assertEqual(verify_bundle(imported).installation_signature.status, "valid")
                self.assertEqual(imported.files()["commitment.bin"].hex(), self.vectors["cases"][index]["expected"]["C"])

    def test_optional_evidence_is_preserved_not_trusted(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = replace(self.fixture(), ots_proof=b"untrusted proof",
                             anchor_locator=b'{"backend":"opentimestamps-bitcoin","network":"regtest"}')
            path = export_bundle(bundle, Path(directory) / "bundle.zip")
            imported = import_bundle(path)
            self.assertEqual(imported, bundle)
            result = verify_bundle(imported)
            self.assertEqual(result.anchor.status, "indeterminate")
            self.assertEqual(result.origin.status, "indeterminate")
            self.assertEqual(result.enrollment.status, "indeterminate")

    def test_existing_destination_and_write_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.zip"
            path.write_bytes(b"keep me")
            with self.assertRaises(FileExistsError):
                export_bundle(self.fixture(), path)
            self.assertEqual(path.read_bytes(), b"keep me")
            with patch("fingerprinto.bundle.os.link", side_effect=OSError("disk failure")):
                with self.assertRaises(OSError):
                    export_bundle(self.fixture(), Path(directory) / "other.zip")
            self.assertFalse(list(Path(directory).glob(".fingerprinto-*")))

    def test_untrusted_names_duplicates_links_and_missing_members(self):
        normal = list(self.fixture().files().items())
        link = zipfile.ZipInfo("credential.bin")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        variants = [normal + [(name, b"bad")] for name in
                    ("../escape", "/absolute", "a/b", "a\\b", "receipt.cbor", "unknown")]
        variants += [normal[1:], normal + [(link, b"target")]]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.zip"
            for files in variants:
                self.write_zip(path, files)
                with self.assertRaises(ValueError):
                    import_bundle(path)
            self.assertEqual(set(p.name for p in Path(directory).iterdir()), {"bundle.zip"})

    def test_changed_bytes_and_conflicting_placement(self):
        normal = self.fixture().files()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.zip"
            for name in ("asset.bin", "commitment.bin"):
                files = {**normal, name: normal[name] + b"x"}
                self.write_zip(path, files.items())
                with self.assertRaises(ValueError):
                    import_bundle(path)
        with self.assertRaises(ValueError):
            replace(self.fixture(), credential=b"unexpected").files()
        with self.assertRaises(ValueError):
            replace(self.fixture(2), credential=None).files()

    def test_locator_rejects_ambiguous_or_active_fields(self):
        for locator in (b'[]', b'{"backend":"xrpl","network":"mainnet"}',
                        b'{"backend":"opentimestamps-bitcoin","network":"mainnet","url":"http://evil"}',
                        b'{"backend":"xrpl","backend":"xrpl","network":"mainnet"}', b'\xff'):
            with self.subTest(locator=locator):
                with self.assertRaises(ValueError):
                    replace(self.fixture(), anchor_locator=locator).files()

    def test_locator_without_proof_is_indeterminate(self):
        bundle = replace(self.fixture(), anchor_locator=json.dumps({
            "backend": "xrpl", "network": "testnet", "transaction_hash": "A" * 64
        }).encode())
        self.assertEqual(verify_bundle(bundle).anchor.status, "indeterminate")

    def test_transport_does_not_accept_a_forged_receipt(self):
        from fingerprinto import Receipt
        original = self.fixture()
        receipt = replace(Receipt.from_bytes(original.receipt), localTime=0)
        forged = replace(original, receipt=receipt.to_bytes())
        with tempfile.TemporaryDirectory() as directory:
            path = export_bundle(forged, Path(directory) / "forged.zip")
            result = verify_bundle(import_bundle(path))
            self.assertEqual(result.installation_signature.status, "invalid")

    def test_bounded_decompression_and_total(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.zip"
            self.write_zip(path, self.fixture().files().items(), compressed=True)
            self.assertEqual(import_bundle(path), self.fixture())
            with patch("fingerprinto.bundle.MAX_TOTAL", 100):
                with self.assertRaises(ValueError):
                    import_bundle(path)
            normal = self.fixture().files()
            self.write_zip(path, {**normal, "commitment.bin.ots": b"x" * 10000}.items(), compressed=True)
            with patch.dict("fingerprinto.bundle.LIMITS", {"commitment.bin.ots": 1024}):
                with self.assertRaises(ValueError):
                    import_bundle(path)

    def test_corrupt_archive_and_crc(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.zip"
            path.write_bytes(b"invalid ZIP")
            with self.assertRaises(ValueError):
                import_bundle(path)
            export_bundle(self.fixture(), Path(directory) / "good.zip")
            data = (Path(directory) / "good.zip").read_bytes()
            # Corrupt a stored asset byte without updating its CRC.
            offset = data.index(self.fixture().asset)
            path.write_bytes(data[:offset] + bytes([data[offset] ^ 1]) + data[offset + 1:])
            with self.assertRaises(ValueError):
                import_bundle(path)


if __name__ == "__main__":
    unittest.main()
