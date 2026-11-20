import json
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import bitcoin
import pytest
from opentimestamps.core.notary import PendingAttestation, BitcoinBlockHeaderAttestation
from opentimestamps.core.serialize import BytesSerializationContext
from opentimestamps.core.timestamp import Timestamp

from settlement import OTSConfig, SettlementService, Status
from settlement.cli import main
from settlement.ots_backend import DEFAULT_CALENDARS, OTSBackend
from conftest import proof_bytes


@pytest.fixture
def regtest_conf(tmp_path: Path) -> Path:
    datadir = tmp_path / "bitcoin"
    (datadir / "regtest").mkdir(parents=True)
    (datadir / "regtest/.cookie").write_text("__cookie__:development-secret\n")
    # A mainnet cookie must not be selected by the regtest resolver.
    (datadir / ".cookie").write_text("wrong:wrong")
    conf = tmp_path / "bitcoin.conf"
    conf.write_text(f"datadir={datadir}\nrpcconnect=node\n")
    return conf


def test_network_defaults_and_authorization() -> None:
    assert OTSConfig().network == "mainnet"
    assert OTSConfig().calendars == DEFAULT_CALENDARS
    assert OTSConfig(network="regtest").calendars == ()
    assert OTSConfig(network="regtest", calendars=("http://calendar:14788",)).calendars
    for options in ({"network": "testnet"}, {"calendars": ("http://calendar:14788",)}):
        with pytest.raises(ValueError):
            OTSConfig(**options)


def test_missing_regtest_calendar_is_error(payload: Path, tmp_path: Path) -> None:
    service = SettlementService(OTSConfig(network="regtest"))
    result = service.stamp_file(payload, tmp_path / "proofs")
    assert result.status is Status.ERROR
    assert result.network == "regtest"
    assert result.diagnostics[0].code == "calendar_required"
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes()))
    assert service.upgrade_proof(proof).status is Status.ERROR


def test_cookie_port_and_no_global_params(regtest_conf: Path, monkeypatch: Any) -> None:
    calls = []
    def proxy(**kwargs: Any) -> object:
        calls.append(kwargs)
        return object()
    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", proxy)
    previous = bitcoin.params
    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf=str(regtest_conf)))
    backend._bitcoin_proxy()
    parsed = urlparse(calls[-1]["service_url"])
    assert (parsed.hostname, parsed.port, parsed.username, parsed.password) == ("node", 18443, "__cookie__", "development-secret")
    assert bitcoin.params is previous
    backend._bitcoin_proxy(wallet="ots wallet")
    assert urlparse(calls[-1]["service_url"]).path == "/wallet/ots%20wallet"


def test_regtest_config_section_and_custom_cookie(regtest_conf: Path, tmp_path: Path, monkeypatch: Any) -> None:
    cookie = tmp_path / "rpc.cookie"
    cookie.write_text("custom:secret")
    with regtest_conf.open("a") as fd:
        fd.write(f"[main]\nrpcport=8332\n[regtest]\nrpcport=19443\nrpccookiefile={cookie}\n")
    calls = []
    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", lambda **kw: calls.append(kw))
    OTSBackend(OTSConfig(network="regtest", bitcoin_conf=str(regtest_conf)))._bitcoin_proxy()
    parsed = urlparse(calls[0]["service_url"])
    assert parsed.port == 19443
    assert parsed.username == "custom"


def test_regtest_confirmation_and_wrong_network(payload: Path, tmp_path: Path, trusted_node: Any,
                                              monkeypatch: Any, regtest_conf: Path) -> None:
    original = trusted_node._call
    monkeypatch.setattr(trusted_node, "_call", lambda self, method: original(self, method) | {"chain": "regtest"})
    proof = tmp_path / "proof.ots"
    proof.write_bytes(proof_bytes(payload.read_bytes(), bitcoin=True))
    service = SettlementService(OTSConfig(network="regtest", bitcoin_conf=str(regtest_conf)))
    result = service.verify_file(payload, proof)
    assert result.status is Status.CONFIRMED
    assert result.network == "regtest"
    assert result.diagnostics[0].code == "regtest_only"
    assert SettlementService().verify_file(payload, proof).status is Status.UNVERIFIABLE
    monkeypatch.setattr(trusted_node, "_call", original)
    result = service.verify_file(payload, proof)
    assert result.status is Status.UNVERIFIABLE
    assert result.diagnostics[0].code == "network_mismatch"


def test_regtest_upgrade_contacts_only_explicit_uri(payload: Path, monkeypatch: Any) -> None:
    backend = OTSBackend(OTSConfig(network="regtest", calendars=("http://calendar:14788",)))
    proof = backend.parse(proof_bytes(payload.read_bytes()))
    # Official mainnet whitelist is deliberately disabled on regtest.
    ctx = BytesSerializationContext()
    proof.serialize(ctx)
    def forbidden(*args: Any) -> None:
        raise AssertionError("Mainnet URI must not be contacted in regtest")
    monkeypatch.setattr("settlement.ots_backend.remote_calendar", forbidden)
    data, diagnostics = backend.upgrade(ctx.getbytes())
    assert data == ctx.getbytes()
    assert diagnostics[0].code == "calendar_not_allowed"
    proof.timestamp.attestations = {PendingAttestation("http://calendar:14788")}
    local = BytesSerializationContext()
    proof.serialize(local)
    monkeypatch.setattr("settlement.ots_backend.remote_calendar", lambda uri: uri)
    contacted = []
    def upgrade(calendar: str, msg: bytes, timeout: int) -> Timestamp:
        contacted.append(calendar)
        stamp = Timestamp(msg)
        stamp.attestations.add(BitcoinBlockHeaderAttestation(100))
        return stamp
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", upgrade)
    upgraded, _ = backend.upgrade(local.getbytes())
    assert contacted == ["http://calendar:14788"]
    assert upgraded != local.getbytes()


def test_cli_error_keeps_selected_network(capsys: Any) -> None:
    assert main(["stamp", "absent", "--out", "proofs", "--network", "regtest", "--timeout", "0"]) == 1
    assert json.loads(capsys.readouterr().out)["network"] == "regtest"
