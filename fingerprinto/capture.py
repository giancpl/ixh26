"""Streaming host checks for Android's unsigned capture diagnostic ZIP."""

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import zipfile
import zlib

MAX_IMAGE = 128 * 1024 * 1024
MAX_REPORT = 64 * 1024


def _report(data: bytes) -> dict:
    def unique(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError("Duplicate report field")
            value[key] = item
        return value

    try:
        report = json.loads(data.decode("utf-8"), object_pairs_hook=unique)
    except RecursionError as exc:
        raise ValueError("Report nesting exceeds limits") from exc
    if type(report) is not dict:
        raise ValueError("Report must be an object")
    fixed = {"format": "fingerprinto-capture-diagnostic/v1", "asset": "original.jpg",
             "capture_route": "CameraX ImageCapture.OutputFileOptions",
             "origin": "indeterminate", "reason": "credential_not_evaluated"}
    text = ("manufacturer", "model", "os_build", "package_name", "app_version_name", "camera_library_version")
    integers = {"size_bytes": (0, MAX_IMAGE), "received_at_unix_ms": (-2**63, 2**63 - 1),
                "sdk_int": (1, 2**31 - 1), "app_version_code": (0, 2**63 - 1), "flash_mode": (0, 2)}
    expected = set(fixed) | set(text) | set(integers) | {
        "sha256", "device_time_trusted", "detached_credential_collected", "lens_selection"}
    if set(report) != expected:
        raise ValueError("Missing or unknown diagnostic fields")
    if any(report[k] != v for k, v in fixed.items()):
        raise ValueError("Unsupported diagnostic profile")
    if any(type(report[k]) is not str or len(report[k].encode("utf-8")) > 4096 for k in text):
        raise ValueError("Invalid diagnostic text field")
    for key, (low, high) in integers.items():
        value = report[key]
        if type(value) is not int or not low <= value <= high:
            raise ValueError("Invalid diagnostic integer")
    if (report["device_time_trusted"] is not False or report["detached_credential_collected"] is not False
            or report["lens_selection"] not in ("front", "back")
            or type(report["sha256"]) is not str or not re.fullmatch(r"[0-9a-f]{64}", report["sha256"])):
        raise ValueError("Invalid diagnostic value")
    return report


def check_capture(source: str | Path) -> dict:
    """Check file/report agreement, not camera provenance or report authenticity.

    Only two fixed members are read, with bounded streaming decompression and
    CRC checking. Nothing is extracted, rewritten, fetched or executed.
    """
    limits = {"original.jpg": MAX_IMAGE, "capture-report.json": MAX_REPORT}
    try:
        with Path(source).open("rb") as stream:
            if os.fstat(stream.fileno()).st_size > MAX_IMAGE + MAX_REPORT + 1024 * 1024:
                raise ValueError("Oversized capture archive")
            with zipfile.ZipFile(stream) as archive:
                members = archive.infolist()
                if len(members) != 2 or {m.filename for m in members} != set(limits):
                    raise ValueError("Expected exactly the original and capture report")
                for member in members:
                    mode = member.external_attr >> 16
                    if (member.orig_filename != member.filename or member.flag_bits & 1
                            or member.file_size > limits[member.filename]
                            or stat.S_IFMT(mode) not in (0, stat.S_IFREG)
                            or member.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)):
                        raise ValueError("Invalid capture archive member")
                with archive.open("capture-report.json") as member:
                    raw = member.read(MAX_REPORT + 1)
                    if len(raw) > MAX_REPORT:
                        raise ValueError("Oversized capture report")
                report = _report(raw)
                digest = hashlib.sha256()
                size = 0
                with archive.open("original.jpg") as member:
                    while chunk := member.read(65536):
                        size += len(chunk)
                        if size > MAX_IMAGE:
                            raise ValueError("Oversized decompressed capture")
                        digest.update(chunk)
                actual = digest.hexdigest()
        matches = actual == report["sha256"] and size == report["size_bytes"]
        return {
            "format": "fingerprinto-capture-check/v1",
            "file_binding": {"status": "valid" if matches else "invalid",
                             "code": "report_matches_file" if matches else "report_file_mismatch"},
            "computed_sha256": actual, "computed_size_bytes": size,
            "origin": {"status": "indeterminate", "code": "origin_validator_unconfigured"},
            "report_authenticated": False,
            "declared_capture": report,
        }
    except (zipfile.BadZipFile, EOFError, RuntimeError, NotImplementedError, zlib.error) as exc:
        raise ValueError("Invalid capture archive") from exc
