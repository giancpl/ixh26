import hashlib
import json
from pathlib import Path
import subprocess
import sys
from typing import Any

import pytest

from settlement import OTSConfig, SettlementService, Status, stamp_file, verify_file
from settlement.service import _lock
from conftest import proof_bytes


def test_stamp_real_client_pending_and_repeatable(payload: Path, tmp_path: Path, fake_calendars: None) -> None:
    before = payload.read_bytes()
    out = tmp_path / "proofs"
    result = stamp_file(payload, out)
    assert result.status is Status.PENDING, result.to_dict()
    assert result.binding_checked
    assert result.payload_sha256 == hashlib.sha256(before).hexdigest()
    proof = Path(result.proof_file)
    assert proof.is_file()
    original = proof.read_bytes()
    assert result.proof_sha256 == hashlib.sha256(original).hexdigest()
    assert json.loads(Path(str(proof) + ".json").read_text()) == result.to_dict()
    assert payload.read_bytes() == before
    assert result.bitcoin.block_height is None
    # Repeat must avoid submission even when calendars fail.
    result2 = stamp_file(payload, out)
    assert result2.to_dict() == result.to_dict()
    assert proof.read_bytes() == original


@pytest.mark.parametrize("existing", [b"malformed", proof_bytes(b"different")])
def test_existing_conflict(payload: Path, tmp_path: Path, existing: bytes) -> None:
    proof = tmp_path / (payload.name + ".ots")
    proof.write_bytes(existing)
    result = stamp_file(payload, tmp_path)
    assert result.status is Status.ERROR
    assert proof.read_bytes() == existing


def test_missing_and_directory_paths(tmp_path: Path) -> None:
    service = SettlementService()
    for path in (tmp_path / "absent", tmp_path):
        assert service.stamp_file(path, tmp_path).status is Status.ERROR
        assert service.upgrade_proof(path).status is Status.ERROR
        assert service.verify_file(path, path).status is Status.ERROR


def test_symlinks_and_receipt_collision(payload: Path, tmp_path: Path) -> None:
    link = tmp_path / "link"
    link.symlink_to(payload)
    assert stamp_file(link, tmp_path).status is Status.ERROR
    receipt = tmp_path / (payload.name + ".ots.json")
    receipt.write_text("existing")
    assert stamp_file(payload, tmp_path).status is Status.ERROR
    assert receipt.read_text() == "existing"


def test_stamp_timeout_and_failure(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    def timeout(*args: Any, **kwargs: Any) -> None:
        assert "shell" not in kwargs
        raise subprocess.TimeoutExpired(args[0], 1)
    monkeypatch.setattr("settlement.ots_backend.subprocess.run", timeout)
    assert stamp_file(payload, tmp_path).status is Status.UNVERIFIABLE
    monkeypatch.setattr("settlement.ots_backend.subprocess.run", lambda *a, **k: subprocess.CompletedProcess(a, 1, b"", b"secret"))
    result = stamp_file(payload, tmp_path)
    assert result.status is Status.ERROR
    assert "secret" not in json.dumps(result.to_dict())


def test_payload_mutation_during_stamp(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    service = SettlementService()
    original = payload.read_bytes()
    def changing(fd: Any) -> bytes:
        payload.write_bytes(b"changed")
        return proof_bytes(original)
    monkeypatch.setattr(service._backend, "stamp", changing)
    result = service.stamp_file(payload, tmp_path)
    assert result.status is Status.ERROR
    assert result.diagnostics[0].code == "payload_changed"
    assert not (tmp_path / (payload.name + ".ots")).exists()


def test_proof_write_failure(payload: Path, tmp_path: Path, fake_calendars: None, monkeypatch: Any) -> None:
    def fail(*args: Any, **kwargs: Any) -> None:
        raise OSError("disk full")
    monkeypatch.setattr("settlement.service.os.link", fail)
    assert stamp_file(payload, tmp_path).status is Status.ERROR
    assert not (tmp_path / (payload.name + ".ots")).exists()
    assert not list(tmp_path.glob(".upp-*"))


def test_concurrent_lock(payload: Path, tmp_path: Path) -> None:
    proof = tmp_path / (payload.name + ".ots")
    with _lock(proof):
        result = stamp_file(payload, tmp_path)
    assert result.status is Status.ERROR
    assert result.diagnostics[0].code == "proof_busy"


def test_verify_does_not_trust_receipt(payload: Path, tmp_path: Path) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes()))
    Path(str(proof) + ".json").write_text('{"status":"CONFIRMED"}')
    result = verify_file(payload, proof)
    assert result.status is Status.PENDING
    assert result.binding_checked


def test_cli_real_stamp_and_verify(payload: Path, tmp_path: Path, fake_calendars: None) -> None:
    stamp = subprocess.run([sys.executable, "-m", "settlement", "stamp", str(payload), "--out", str(tmp_path)], capture_output=True)
    assert stamp.returncode == 0, stamp.stderr
    result = json.loads(stamp.stdout)
    verify = subprocess.run([sys.executable, "-m", "settlement", "verify", str(payload), result["proof_file"]], capture_output=True)
    assert verify.returncode == 2
    assert json.loads(verify.stdout)["status"] == "PENDING"


@pytest.mark.parametrize("args", [[], ["stamp"], ["verify", "absent", "absent"], ["stamp", "a", "--out", "b", "--timeout", "0"]])
def test_cli_error_json(args: list[str]) -> None:
    completed = subprocess.run([sys.executable, "-m", "settlement", *args], capture_output=True)
    assert completed.returncode == 1
    assert json.loads(completed.stdout)["status"] == "ERROR"


def test_config_validation() -> None:
    with pytest.raises(ValueError):
        OTSConfig(calendars=("http://untrusted",))


def test_cli_invalid_and_unverifiable(payload: Path, tmp_path: Path) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(b"wrong"))
    command = [sys.executable, "-m", "settlement", "verify", str(payload), str(proof)]
    completed = subprocess.run(command, capture_output=True)
    assert completed.returncode == 3
    assert json.loads(completed.stdout)["status"] == "INVALID"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    conf = tmp_path / "bitcoin.conf"
    conf.write_text("datadir=" + str(tmp_path / "no-node") + "\n")
    completed = subprocess.run(command + ["--bitcoin-conf", str(conf)], capture_output=True)
    assert completed.returncode == 4
    assert json.loads(completed.stdout)["status"] == "UNVERIFIABLE"
