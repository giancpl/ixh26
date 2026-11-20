import hashlib
import socket
from pathlib import Path
from typing import Any

import pytest
from bitcoin.core import CBlockHeader
from opentimestamps.core.notary import BitcoinBlockHeaderAttestation, PendingAttestation
from opentimestamps.core.op import OpSHA256
from opentimestamps.core.serialize import BytesSerializationContext
from opentimestamps.core.timestamp import DetachedTimestampFile, Timestamp


def proof_bytes(payload: bytes, *, bitcoin: bool = False) -> bytes:
    timestamp = Timestamp(hashlib.sha256(payload).digest())
    timestamp.attestations.add(
        BitcoinBlockHeaderAttestation(100) if bitcoin else
        PendingAttestation("https://alice.btc.calendar.opentimestamps.org")
    )
    proof = DetachedTimestampFile(OpSHA256(), timestamp)
    context = BytesSerializationContext()
    proof.serialize(context)
    return context.getbytes()


@pytest.fixture(autouse=True)
def offline(request: Any, monkeypatch: Any) -> None:
    if request.node.get_closest_marker("network") or request.node.get_closest_marker("regtest"):
        return

    def forbidden(*args: Any, **kwargs: Any) -> None:
        raise AssertionError("Network access forbidden in offline tests")

    monkeypatch.setattr(socket.socket, "connect", forbidden)


@pytest.fixture
def payload(tmp_path: Path) -> Path:
    path = tmp_path / "manifest.bin"
    path.write_bytes(b"\x00opaque\xff\x80payload\n")
    return path


@pytest.fixture
def fake_calendars(tmp_path: Path, monkeypatch: Any) -> None:
    """Execute the real CLI in a child process; replace only remote calendars.

    sitecustomize is scoped to this test's child environment, never shipped.
    The fake rejects any submission that is not a 32-byte commitment.
    """
    shim = tmp_path / "shim"
    shim.mkdir()
    (shim / "sitecustomize.py").write_text('''
import socket
import otsclient.cmds
from opentimestamps.core.timestamp import Timestamp
from opentimestamps.core.notary import PendingAttestation
def forbidden(*args, **kwargs):
    raise AssertionError("Network forbidden in test subprocess")
socket.socket.connect = forbidden
class Calendar:
    def submit(self, msg, timeout=None):
        assert isinstance(msg, bytes) and len(msg) == 32
        result = Timestamp(msg)
        result.attestations.add(PendingAttestation("https://alice.btc.calendar.opentimestamps.org"))
        return result
otsclient.cmds.remote_calendar = lambda uri: Calendar()
''')
    import os
    monkeypatch.setenv("PYTHONPATH", str(shim) + os.pathsep + str(Path.cwd()))


@pytest.fixture
def trusted_node(monkeypatch: Any, payload: Path) -> Any:
    class Node:
        def __init__(self, **kwargs: Any):
            self.closed = False

        def _call(self, method: str) -> dict[str, Any]:
            assert method == "getblockchaininfo"
            return {"chain": "main", "initialblockdownload": False, "blocks": 1000, "headers": 1000}

        def getblockhash(self, height: int) -> bytes:
            assert height == 100
            return b"\x01" * 32

        def getblockheader(self, block_hash: bytes) -> CBlockHeader:
            return CBlockHeader(hashMerkleRoot=hashlib.sha256(payload.read_bytes()).digest(), nTime=1700000000)

        def close(self) -> None:
            self.closed = True

    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", Node)
    return Node
