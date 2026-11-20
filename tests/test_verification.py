from pathlib import Path
from typing import Any

import pytest
from bitcoin.core import CBlockHeader
from opentimestamps.core.notary import PendingAttestation, UnknownAttestation
from opentimestamps.core.op import OpSHA1
from opentimestamps.core.serialize import BytesSerializationContext
from opentimestamps.core.timestamp import DetachedTimestampFile, Timestamp

from settlement import SettlementService, Status, verify_file
from conftest import proof_bytes


def test_confirmation_uses_official_attestation_verifier(payload: Path, tmp_path: Path, trusted_node: Any) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    before = proof.read_bytes()
    result = verify_file(payload, proof)
    assert result.status is Status.CONFIRMED
    assert result.binding_checked
    assert result.bitcoin.block_height == 100
    assert result.bitcoin.block_time == 1700000000
    assert proof.read_bytes() == before
    payload.write_bytes(b"modified")
    assert verify_file(payload, proof).status is Status.INVALID


@pytest.mark.parametrize("data", [b"", b"nonsense", proof_bytes(b"wrong"), proof_bytes(b"wrong") + b"trailing"])
def test_bad_or_mismatched_proof(payload: Path, tmp_path: Path, data: bytes) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(data)
    assert verify_file(payload, proof).status is Status.INVALID


def test_no_node_never_confirms(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    def unavailable(**kwargs: Any) -> None:
        raise OSError("credentials must not leak")
    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", unavailable)
    result = verify_file(payload, proof)
    assert result.status is Status.UNVERIFIABLE
    assert result.binding_checked
    assert result.bitcoin.block_height is None
    assert "credentials" not in str(result.to_dict())


def test_corrupt_rpc_header_is_unverifiable(payload: Path, tmp_path: Path, trusted_node: Any, monkeypatch: Any) -> None:
    from bitcoin.core.serialize import SerializationError
    def corrupt(*args: Any) -> None:
        raise SerializationError("Malformed RPC header")
    monkeypatch.setattr(trusted_node, "getblockheader", corrupt)
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    assert verify_file(payload, proof).status is Status.UNVERIFIABLE


@pytest.mark.parametrize("change", [{"chain": "test"}, {"initialblockdownload": True}, {"headers": 1001}, {"blocks": 99, "headers": 99}])
def test_node_not_ready(payload: Path, tmp_path: Path, trusted_node: Any, monkeypatch: Any, change: dict[str, Any]) -> None:
    original = trusted_node._call
    monkeypatch.setattr(trusted_node, "_call", lambda self, method: original(self, method) | change)
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    assert verify_file(payload, proof).status is Status.UNVERIFIABLE


def test_wrong_bitcoin_merkle_root(payload: Path, tmp_path: Path, trusted_node: Any, monkeypatch: Any) -> None:
    monkeypatch.setattr(trusted_node, "getblockheader", lambda *args: CBlockHeader(hashMerkleRoot=b"x" * 32, nTime=42))
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    result = verify_file(payload, proof)
    assert result.status is Status.INVALID
    assert result.bitcoin.block_time is None


def test_reorg_is_unverifiable(payload: Path, tmp_path: Path, trusted_node: Any, monkeypatch: Any) -> None:
    hashes = iter([b"x" * 32, b"y" * 32])
    monkeypatch.setattr(trusted_node, "getblockhash", lambda *args: next(hashes))
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    assert verify_file(payload, proof).status is Status.UNVERIFIABLE


def test_verify_never_contacts_calendars(payload: Path, tmp_path: Path, monkeypatch: Any) -> None:
    def forbidden(*args: Any) -> None:
        raise AssertionError("verify must not upgrade")
    monkeypatch.setattr("settlement.ots_backend.remote_calendar", forbidden)
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes()))
    assert verify_file(payload, proof).status is Status.PENDING


def test_unknown_attestation_and_non_sha256(payload: Path, tmp_path: Path) -> None:
    import hashlib
    proof = tmp_path / "proof.ots"
    for op, digest, expected in [(None, hashlib.sha256(payload.read_bytes()).digest(), Status.UNVERIFIABLE),
                                 (OpSHA1(), hashlib.sha1(payload.read_bytes()).digest(), Status.INVALID)]:
        from opentimestamps.core.op import OpSHA256
        stamp = Timestamp(digest)
        stamp.attestations.add(UnknownAttestation(b"abcdefgh", b"unknown"))
        detached = DetachedTimestampFile(OpSHA256() if op is None else op, stamp)
        ctx = BytesSerializationContext()
        detached.serialize(ctx)
        proof.write_bytes(ctx.getbytes())
        assert SettlementService().verify_file(payload, proof).status is expected
