import hashlib
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from fingerprinto.capture import check_capture


class CaptureCheckTests(unittest.TestCase):
    def report(self, asset):
        return {"format": "fingerprinto-capture-diagnostic/v1", "asset": "original.jpg",
                "sha256": hashlib.sha256(asset).hexdigest(), "size_bytes": len(asset),
                "received_at_unix_ms": 1700000000000, "device_time_trusted": False,
                "manufacturer": "Fixture", "model": "Test", "os_build": "test",
                "sdk_int": 35, "package_name": "com.example.photoanchor", "app_version_code": 1,
                "app_version_name": "0.1", "capture_route": "CameraX ImageCapture.OutputFileOptions",
                "camera_library_version": "1.4.1", "lens_selection": "back", "flash_mode": 2,
                "origin": "indeterminate", "reason": "credential_not_evaluated",
                "detached_credential_collected": False}

    def write(self, path, asset, report, extra=None):
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("original.jpg", asset)
            archive.writestr("capture-report.json", report if isinstance(report, bytes) else json.dumps(report).encode())
            if extra:
                archive.writestr(*extra)

    def test_streamed_match_still_indeterminate(self):
        asset = bytes(range(256)) * 1000
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.zip"
            self.write(path, asset, self.report(asset))
            result = check_capture(path)
            self.assertEqual(result["file_binding"]["status"], "valid")
            self.assertEqual(result["origin"]["status"], "indeterminate")
            self.assertFalse(result["report_authenticated"])
            self.assertEqual(result["computed_size_bytes"], len(asset))

    def test_hash_and_size_mismatches(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.zip"
            for key, value in (("sha256", "0" * 64), ("size_bytes", 1)):
                report = {**self.report(b"image"), key: value}
                self.write(path, b"image", report)
                self.assertEqual(check_capture(path)["file_binding"]["status"], "invalid")

    def test_invalid_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.zip"
            variants = [b'[]', b'\xff', b'{"format":1,"format":2}', b'{}']
            for key, value in (("origin", "accepted"), ("device_time_trusted", True),
                               ("sdk_int", True), ("size_bytes", -1), ("sha256", "bad"),
                               ("model", []), ("unknown", "value")):
                variants.append({**self.report(b"image"), key: value})
            for report in variants:
                self.write(path, b"image", report)
                with self.assertRaises(ValueError):
                    check_capture(path)

    def test_archive_paths_links_and_limits(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.zip"
            self.write(path, b"image", self.report(b"image"), ("../escape", b"x"))
            with self.assertRaises(ValueError):
                check_capture(path)
            link = zipfile.ZipInfo("original.jpg")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(link, b"target")
                archive.writestr("capture-report.json", json.dumps(self.report(b"target")))
            with self.assertRaises(ValueError):
                check_capture(path)
            self.write(path, b"a" * 10000, self.report(b"a" * 10000))
            with patch("fingerprinto.capture.MAX_IMAGE", 1024):
                with self.assertRaises(ValueError):
                    check_capture(path)
            path.write_bytes(b"not zip")
            with self.assertRaises(ValueError):
                check_capture(path)

    def test_cli_returns_incomplete_and_invalid_distinctly(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.zip"
            for asset, code in ((b"image", 2), (b"changed", 3)):
                self.write(path, asset, self.report(b"image"))
                result = subprocess.run([sys.executable, "-B", "-m", "fingerprinto", "check-capture", str(path)],
                                        capture_output=True, text=True, timeout=15)
                self.assertEqual(result.returncode, code)
                self.assertEqual(result.stderr, "")
                self.assertEqual(json.loads(result.stdout)["origin"]["status"], "indeterminate")


if __name__ == "__main__":
    unittest.main()
