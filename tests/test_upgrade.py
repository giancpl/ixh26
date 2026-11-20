from pathlib import Path
from typing import Any
from urllib.error import URLError

from opentimestamps.core.notary import BitcoinBlockHeaderAttestation, PendingAttestation
from opentimestamps.core.serialize import BytesSerializationContext
from opentimestamps.core.timestamp import DetachedTimestampFile
from opentimestamps.core.op import OpSHA256
from opentimestamps.core.timestamp import Timestamp
from opentimestamps.calendar import CommitmentNotFoundError

from settlement import Status, upgrade_proof
from conftest import proof_bytes


def test_upgrade_repeatable(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes()))
    original = proof.read_bytes()
    def upgraded(calendar: Any, msg: bytes, timeout: int) -> Timestamp:
        result = Timestamp(msg)
        result.attestations.add(BitcoinBlockHeaderAttestation(100))
        return result
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", upgraded)
    result = upgrade_proof(proof)
    assert result.status is Status.PENDING
    assert not result.binding_checked
    assert result.bitcoin.block_height is None
    assert proof.read_bytes() != original
    upgraded_bytes = proof.read_bytes()
    assert upgrade_proof(proof).status is Status.PENDING
    assert proof.read_bytes() == upgraded_bytes
    assert not list(tmp_path.glob(".upp-*"))


def test_pending_and_unavailable_keep_original(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    proof = tmp_path / "proof.ots"
    original = proof_bytes(payload.read_bytes())
    proof.write_bytes(original)
    for error, code in [(CommitmentNotFoundError("pending"), "calendar_pending"),
                        (URLError("secret"), "calendar_unavailable")]:
        def unavailable(*args: Any) -> None:
            raise error
        monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", unavailable)
        for _ in range(2):
            result = upgrade_proof(proof)
            assert result.status is Status.PENDING
            assert result.diagnostics[0].code == code
            assert proof.read_bytes() == original


def test_upgrade_write_failure_preserves_original(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    proof = tmp_path / "proof.ots"
    original = proof_bytes(payload.read_bytes())
    proof.write_bytes(original)
    def upgraded(calendar: Any, msg: bytes, timeout: int) -> Timestamp:
        stamp = Timestamp(msg)
        stamp.attestations.add(BitcoinBlockHeaderAttestation(100))
        return stamp
    def fail(*args: Any) -> None:
        raise OSError("disk error")
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", upgraded)
    monkeypatch.setattr("settlement.service.os.replace", fail)
    assert upgrade_proof(proof).status is Status.ERROR
    assert proof.read_bytes() == original


def test_malformed_upgrade(tmp_path: Path) -> None:
    proof = tmp_path / "bad.ots"
    proof.write_bytes(b"bad")
    assert upgrade_proof(proof).status is Status.INVALID
    assert proof.read_bytes() == b"bad"


def test_unapproved_calendar_is_not_contacted(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    import hashlib
    stamp = Timestamp(hashlib.sha256(payload.read_bytes()).digest())
    stamp.attestations.add(PendingAttestation("https://attacker.example"))
    ctx = BytesSerializationContext()
    DetachedTimestampFile(OpSHA256(), stamp).serialize(ctx)
    proof = tmp_path / "proof.ots"
    proof.write_bytes(ctx.getbytes())
    def forbidden(*args: Any) -> None:
        raise AssertionError("Unapproved endpoint must not be contacted")
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", forbidden)
    result = upgrade_proof(proof)
    assert result.status is Status.PENDING
    assert result.diagnostics[0].code == "calendar_not_allowed"
    assert proof.read_bytes() == ctx.getbytes()


def test_partial_upgrade_discarded_if_another_calendar_fails(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    from settlement.ots_backend import OTSBackend
    proof_object = OTSBackend().parse(proof_bytes(payload.read_bytes()))
    proof_object.timestamp.attestations.add(PendingAttestation("https://bob.btc.calendar.opentimestamps.org"))
    ctx = BytesSerializationContext()
    proof_object.serialize(ctx)
    original = ctx.getbytes()
    proof = tmp_path / "proof.ots"
    proof.write_bytes(original)
    calls = 0
    def partially_available(calendar: Any, msg: bytes, timeout: int) -> Timestamp:
        nonlocal calls
        calls += 1
        if calls == 2:
            # Upstream raises plain Exception for oversized calendar responses.
            raise Exception("Calendar response exceeded size limit")
        stamp = Timestamp(msg)
        stamp.attestations.add(BitcoinBlockHeaderAttestation(100))
        return stamp
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", partially_available)
    result = upgrade_proof(proof)
    assert calls == 2
    assert result.status is Status.PENDING
    assert any(d.code == "calendar_unavailable" for d in result.diagnostics)
    assert proof.read_bytes() == original
