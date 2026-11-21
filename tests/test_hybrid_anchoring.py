"""Hybrid v2 through real OTS serialization/client; calendar/RPC are simulated."""

from dataclasses import replace
import json
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from conftest import proof_bytes
from fingerprinto.anchoring import stamp_hybrid_receipt, verify_hybrid_receipt_ots
from fingerprinto.bundle import EvidenceBundle, export_bundle
from fingerprinto.cli import main
from fingerprinto.core import Receipt
from fingerprinto.hybrid import HybridPrivateKey, HybridSignature
from fingerprinto.hybrid_receipt import HybridReceipt
from settlement import OTSConfig, Status


@pytest.fixture
def hybrid_case():
    hardware = ec.generate_private_key(ec.SECP256R1())
    spki = hardware.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    key = HybridPrivateKey.generate(spki)
    binding = hardware.sign(key.public.binding_root, ec.ECDSA(hashes.SHA256()))
    vectors = json.loads((Path(__file__).parent / "fingerprinto/fixtures/v1.json").read_text())
    chain = tuple(bytes.fromhex(c) for c in vectors["shared"]["chain"])
    fields = Receipt(spki, chain, "example.photo", 1, bytes(32), "Fixture", "Model", 123, None, 0, binding)
    asset = b"synthetic asset"
    receipt = HybridReceipt(key.public, fields).sign(key, asset)
    return receipt, asset, key.public.device_id


def test_stamp_cli_and_repeat_reuses_ots_proof(hybrid_case, tmp_path, fake_calendars, capsys, monkeypatch):
    receipt, asset, identity = hybrid_case
    path = export_bundle(EvidenceBundle(receipt.to_bytes(), asset, profile="hybrid-v2"), tmp_path / "v2.zip")
    args = ["stamp-hybrid", str(path), "--expected-device-id", identity.hex(), "--network", "mainnet", "--out", str(tmp_path / "anchors")]
    assert main(args) == 0
    result = json.loads(capsys.readouterr().out)
    assert result["status"] == "PENDING"
    assert Path(result["commitment_file"]).read_bytes() == receipt.commitment(asset)
    proof = Path(result["proof_file"]).read_bytes()
    def forbidden(*args, **kwargs):
        raise AssertionError("Repeated stamp must not resubmit")
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.stamp", forbidden)
    assert main(args) == 0
    assert json.loads(capsys.readouterr().out)["status"] == "PENDING"
    checked = verify_hybrid_receipt_ots(receipt.to_bytes(), asset, expected_device_id=identity, proof=proof, config=OTSConfig())
    assert checked.anchor.status == "pending"
    assert checked.installation_signature.status == "valid"


def test_confirmed_hybrid_cli_still_requires_origin_and_enrollment(hybrid_case, payload, trusted_node, tmp_path, capsys):
    receipt, asset, identity = hybrid_case
    commitment = receipt.commitment(asset)
    payload.write_bytes(commitment)
    bundle = EvidenceBundle(receipt.to_bytes(), asset, ots_proof=proof_bytes(commitment, bitcoin=True),
                            anchor_locator=b'{"backend":"opentimestamps-bitcoin","network":"regtest"}', profile="hybrid-v2")
    path = export_bundle(bundle, tmp_path / "v2.zip")
    assert main(["verify-hybrid-ots", str(path), "--expected-device-id", identity.hex(), "--network", "mainnet"]) == 2
    result = json.loads(capsys.readouterr().out)
    assert result["installation_signature"]["status"] == "valid"
    assert result["anchor"]["status"] == "verified"
    assert result["origin"]["status"] == result["enrollment"]["status"] == "indeterminate"
    assert result["anchor_details"]["network"] == "mainnet"


def test_wrong_contract_rejected_before_rpc(hybrid_case, monkeypatch):
    receipt, asset, identity = hybrid_case
    def forbidden(*args, **kwargs):
        raise AssertionError("Wrong binding must not query Bitcoin")
    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", forbidden)
    for raw in (asset, receipt.commitment(asset).hex().encode(), receipt.commitment(asset) + b"\n"):
        checked = verify_hybrid_receipt_ots(receipt.to_bytes(), asset, expected_device_id=identity,
                                           proof=proof_bytes(raw, bitcoin=True), config=OTSConfig())
        assert checked.anchor.code == "ots_commitment_mismatch"


def test_anchor_is_independent_and_invalid_signature_never_stamps(hybrid_case, payload, trusted_node, tmp_path):
    receipt, asset, identity = hybrid_case
    bad = replace(receipt, signatures=HybridSignature(receipt.signatures.ed25519, bytes(3309)))
    commitment = bad.commitment(asset)
    payload.write_bytes(commitment)
    result = verify_hybrid_receipt_ots(bad.to_bytes(), asset, expected_device_id=identity,
                                      proof=proof_bytes(commitment, bitcoin=True), config=OTSConfig())
    assert result.anchor.status == "verified"
    assert result.installation_signature.status == "invalid"
    with pytest.raises(ValueError):
        stamp_hybrid_receipt(bad.to_bytes(), asset, tmp_path / "bad", expected_device_id=identity, config=OTSConfig())
    assert not (tmp_path / "bad").exists()


def test_wrong_identity_and_node_network(hybrid_case, payload, trusted_node, monkeypatch, tmp_path):
    receipt, asset, identity = hybrid_case
    with pytest.raises(ValueError):
        stamp_hybrid_receipt(receipt.to_bytes(), asset, tmp_path / "bad", expected_device_id=bytes(32), config=OTSConfig())
    commitment = receipt.commitment(asset)
    payload.write_bytes(commitment)
    original = trusted_node._call
    monkeypatch.setattr(trusted_node, "_call", lambda self, method: original(self, method) | {"chain": "regtest"})
    result = verify_hybrid_receipt_ots(receipt.to_bytes(), asset, expected_device_id=identity,
                                      proof=proof_bytes(commitment, bitcoin=True), config=OTSConfig())
    assert result.anchor.status == "indeterminate"
    assert any(d["code"] == "network_mismatch" for d in result.anchor_details["diagnostics"])
