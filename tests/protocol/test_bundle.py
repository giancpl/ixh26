import hashlib
import json
from pathlib import Path
import subprocess
import struct
import sys
import zipfile

import pytest
from bitcoin.core import CBlockHeader

from conftest import proof_bytes
from settlement import OTSConfig
from upp_protocol import ProtocolError
from upp_protocol.verify_bundle import verify_bundle

FIXTURES = Path(__file__).parents[2] / "contracts" / "fixtures"


def make_bundle(tmp_path, changes=None, *, bitcoin=False, compression=zipfile.ZIP_STORED):
    members = {name: (FIXTURES / name).read_bytes()
               for name in ("photo.jpg", "event.json", "payload.bin")}
    members["proof.ots"] = proof_bytes(members["payload.bin"], bitcoin=bitcoin)
    # A forged receipt must never influence actual verification.
    members["receipt.json"] = b'{"status":"CONFIRMED","network":"mainnet"}'
    members.update(changes or {})
    bundle = tmp_path / "bundle.zip"
    with zipfile.ZipFile(bundle, "w", compression=compression) as archive:
        for name, data in members.items():
            archive.writestr(name, data)
    return bundle


def test_pending_ignores_forged_receipt_and_never_calls_calendars(tmp_path, monkeypatch):
    def forbidden(*a, **kw):
        raise AssertionError("No calendar during bundle verification")
    monkeypatch.setattr("settlement.ots_backend.remote_calendar", forbidden)
    result = verify_bundle(make_bundle(tmp_path), OTSConfig(network="regtest"))
    assert result["signature_valid"] and result["integrity_valid"]
    assert result["settlement"]["status"] == "PENDING"
    assert result["settlement"]["network"] == "regtest"
    assert result["settlement"]["binding_checked"]


@pytest.mark.parametrize("member,code", [("photo.jpg", "photo_hash_mismatch"),
                                        ("event.json", "invalid_signature"),
                                        ("payload.bin", "payload_mismatch")])
def test_bundle_tampering(tmp_path, member, code):
    changed = {"photo.jpg": (FIXTURES / "negative/photo.jpg").read_bytes(),
               "event.json": (FIXTURES / "negative/signature.json").read_bytes(),
               "payload.bin": b"wrong"}
    with pytest.raises(ProtocolError) as exc:
        verify_bundle(make_bundle(tmp_path, {member: changed[member]}))
    assert exc.value.code == code


def test_bundle_confirms_against_official_verifier_and_mock_rpc_only(tmp_path, monkeypatch):
    digest = hashlib.sha256((FIXTURES / "payload.bin").read_bytes()).digest()

    class Node:
        def __init__(self, **kwargs): pass
        def _call(self, method):
            return {"chain": "regtest", "blocks": 100, "headers": 100, "initialblockdownload": False}
        def getblockhash(self, height): return b"x" * 32
        def getblockheader(self, block_hash): return CBlockHeader(hashMerkleRoot=digest, nTime=1700000100)
        def close(self): pass

    monkeypatch.setattr("settlement.ots_backend.OTSBackend._bitcoin_proxy", lambda self: Node())
    result = verify_bundle(make_bundle(tmp_path, bitcoin=True), OTSConfig(network="regtest"))
    assert result["settlement"]["status"] == "CONFIRMED"
    assert result["settlement"]["bitcoin"] == {"block_height": 100, "block_time": 1700000100}


def test_missing_or_duplicate_members_are_rejected(tmp_path):
    bundle = make_bundle(tmp_path)
    with zipfile.ZipFile(bundle, "a") as archive:
        with pytest.warns(UserWarning):
            archive.writestr("payload.bin", b"duplicate")
    with pytest.raises(ProtocolError, match="once"):
        verify_bundle(bundle)


def test_cli_subprocess_pending_and_missing(tmp_path):
    result = subprocess.run([sys.executable, "-m", "upp_protocol.verify_bundle",
                             str(make_bundle(tmp_path)), "--network", "regtest"],
                            capture_output=True, text=True)
    assert result.returncode == 2, result.stderr
    assert json.loads(result.stdout)["settlement"]["status"] == "PENDING"
    result = subprocess.run([sys.executable, "-m", "upp_protocol.verify_bundle", "missing.zip"],
                            capture_output=True, text=True)
    assert result.returncode == 1
    assert json.loads(result.stdout)["status"] == "ERROR"


@pytest.mark.parametrize("corruption", ["deflate_stream", "declared_size"])
def test_corrupt_zip_cli_is_structured_invalid_without_traceback(tmp_path, corruption):
    bundle = make_bundle(tmp_path, compression=zipfile.ZIP_DEFLATED)
    data = bytearray(bundle.read_bytes())
    with zipfile.ZipFile(bundle) as archive:
        info = archive.getinfo("photo.jpg")
        if corruption == "deflate_stream":
            name_length, extra_length = struct.unpack_from("<HH", data, info.header_offset + 26)
            start = info.header_offset + 30 + name_length + extra_length
            # RFC 1951 BTYPE=11 is reserved; this corrupts real compressed bytes
            # rather than mocking the ZIP reader or decoder exception.
            data[start] = (data[start] & ~0x06) | 0x06
        else:
            # The first central directory entry is photo.jpg. Claim one extra
            # decoded byte while leaving the valid stream and CRC untouched.
            struct.pack_into("<I", data, archive.start_dir + 24, info.file_size + 1)
    bundle.write_bytes(data)
    result = subprocess.run([sys.executable, "-m", "upp_protocol.verify_bundle", str(bundle)],
                            capture_output=True, text=True)
    assert result.returncode == 3, result.stderr
    response = json.loads(result.stdout)
    assert response["status"] == "INVALID"
    assert response["error"]["code"] == "invalid_bundle"
    assert "Traceback" not in result.stderr


def test_local_bundle_io_error_is_not_misclassified_as_invalid(tmp_path, monkeypatch):
    def denied(*args, **kwargs):
        raise PermissionError("Filesystem failure")
    monkeypatch.setattr(zipfile, "ZipFile", denied)
    with pytest.raises(PermissionError):
        verify_bundle(tmp_path / "bundle.zip")
