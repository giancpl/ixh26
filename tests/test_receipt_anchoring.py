"""POSIX receipt/OTS integration: real serialization/CLI, simulated RPC/calendar."""

from dataclasses import replace
import hashlib
import json
from pathlib import Path

import pytest
from bitcoin.core import CBlockHeader
from opentimestamps.core.notary import BitcoinBlockHeaderAttestation, PendingAttestation
from opentimestamps.core.op import OpSHA256
from opentimestamps.core.serialize import BytesSerializationContext
from opentimestamps.core.timestamp import DetachedTimestampFile, Timestamp

from fingerprinto import Receipt, commitments
from fingerprinto.anchoring import export_commitment, stamp_receipt, verify_receipt_ots
from settlement import OTSConfig, SettlementService, Status


@pytest.fixture
def bundle():
    vectors = json.loads((Path(__file__).parent / "fingerprinto/fixtures/v1.json").read_text())
    case = vectors["cases"][0]
    return bytes.fromhex(case["receipt_cbor"]), bytes.fromhex(case["J"])


def proof_for(digest: bytes, confirmed=False):
    stamp = Timestamp(digest)
    stamp.attestations.add(BitcoinBlockHeaderAttestation(100) if confirmed else
                           PendingAttestation("https://alice.btc.calendar.opentimestamps.org"))
    proof = DetachedTimestampFile(OpSHA256(), stamp)
    ctx = BytesSerializationContext()
    proof.serialize(ctx)
    return ctx.getbytes()


def expected(bundle):
    return commitments(Receipt.from_bytes(bundle[0]), bundle[1])


def install_node(monkeypatch, digest, *, chain="main", mismatch=False, reorg=False):
    class Node:
        def __init__(self, **kwargs):
            self.calls = 0

        def _call(self, method):
            assert method == "getblockchaininfo"
            return {"chain": chain, "initialblockdownload": False, "blocks": 1000, "headers": 1000}

        def getblockhash(self, height):
            self.calls += 1
            return bytes([self.calls if reorg else 1]) * 32

        def getblockheader(self, block_hash):
            return CBlockHeader(hashMerkleRoot=bytes(32) if mismatch else digest, nTime=1700000000)

        def close(self):
            pass

    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", Node)


def test_export_is_exact_idempotent_and_conflict_safe(bundle, tmp_path):
    path = export_commitment(*bundle, tmp_path)
    assert path.name == "commitment.bin"
    assert path.read_bytes() == expected(bundle)["C"]
    assert len(path.read_bytes()) == 32
    assert export_commitment(*bundle, tmp_path) == path
    path.write_bytes(b"existing unrelated data")
    with pytest.raises(ValueError, match="different bytes"):
        export_commitment(*bundle, tmp_path)
    assert path.read_bytes() == b"existing unrelated data"
    assert not list(path.parent.glob(".commitment-*"))


def test_export_rejects_tampered_asset_and_symlink(bundle, tmp_path):
    with pytest.raises(ValueError, match="valid installation signature"):
        export_commitment(bundle[0], bundle[1] + b"x", tmp_path)
    path = export_commitment(*bundle, tmp_path)
    path.unlink()
    target = tmp_path / "target"
    target.write_bytes(expected(bundle)["C"])
    path.symlink_to(target)
    with pytest.raises(ValueError, match="Conflicting"):
        export_commitment(*bundle, tmp_path)


def test_stamp_real_cli_and_repeat_without_resubmission(bundle, tmp_path, fake_calendars, monkeypatch):
    config = OTSConfig()
    submission = stamp_receipt(*bundle, tmp_path, config=config)
    result = submission.settlement
    assert result.status is Status.PENDING
    assert result.payload_sha256 == expected(bundle)["OTS_SHA256_C"].hex()
    proof_path = Path(result.proof_file)
    assert proof_path.name == "commitment.bin.ots"
    original = proof_path.read_bytes()

    def forbidden(*args, **kwargs):
        raise AssertionError("Must reuse existing proof")

    monkeypatch.setattr("settlement.ots_backend.OTSBackend.stamp", forbidden)
    assert stamp_receipt(*bundle, tmp_path, config=config).settlement.status is Status.PENDING
    assert proof_path.read_bytes() == original
    checked = verify_receipt_ots(*bundle, proof=original, config=config)
    assert checked.installation_signature.status == "valid"
    assert checked.anchor.status == "pending"
    assert checked.origin.status == checked.enrollment.status == "indeterminate"


@pytest.mark.parametrize("kind", ["photo", "C_as_digest", "hex", "newline", "malformed"])
def test_wrong_proof_contract_fails_before_rpc(bundle, monkeypatch, kind):
    values = expected(bundle)
    digests = {"photo": hashlib.sha256(bundle[1]).digest(), "C_as_digest": values["C"],
               "hex": hashlib.sha256(values["C"].hex().encode()).digest(),
               "newline": hashlib.sha256(values["C"] + b"\n").digest()}
    proof = b"not OTS" if kind == "malformed" else proof_for(digests[kind], True)
    def forbidden(*args, **kwargs):
        raise AssertionError("Invalid binding must not query Bitcoin")
    monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", forbidden)
    result = verify_receipt_ots(*bundle, proof=proof, config=OTSConfig())
    assert result.anchor.status == "failed"


@pytest.mark.parametrize("network,chain,status", [("mainnet", "main", "verified"),
                         ("mainnet", "regtest", "indeterminate"), ("regtest", "regtest", "verified")])
def test_confirmation_checks_expected_network(bundle, monkeypatch, network, chain, status):
    digest = expected(bundle)["OTS_SHA256_C"]
    install_node(monkeypatch, digest, chain=chain)
    if network == "regtest":
        # Replace only configuration discovery; the official header verifier runs.
        from settlement.ots_backend import OTSBackend, bitcoin
        monkeypatch.setattr(OTSBackend, "_bitcoin_proxy", lambda self: bitcoin.rpc.Proxy())
    result = verify_receipt_ots(*bundle, proof=proof_for(digest, True), config=OTSConfig(network=network))
    assert result.anchor.status == status
    assert result.anchor_details["network"] == network
    assert result.origin.status == result.enrollment.status == "indeterminate"
    if status == "verified":
        assert result.anchor_details["block_height"] == 100
        assert result.anchor_details["block_time"] == 1700000000
    if network == "regtest":
        assert any(d["code"] == "regtest_only" for d in result.anchor_details["diagnostics"])


@pytest.mark.parametrize("failure,status", [("mismatch", "failed"), ("reorg", "indeterminate"), ("offline", "indeterminate")])
def test_bitcoin_failures(bundle, monkeypatch, failure, status):
    digest = expected(bundle)["OTS_SHA256_C"]
    install_node(monkeypatch, digest, mismatch=failure == "mismatch", reorg=failure == "reorg")
    if failure == "offline":
        def offline(*args, **kwargs):
            raise OSError("private RPC credentials")
        monkeypatch.setattr("settlement.ots_backend.bitcoin.rpc.Proxy", offline)
    result = verify_receipt_ots(*bundle, proof=proof_for(digest, True), config=OTSConfig())
    assert result.anchor.status == status
    assert "private RPC credentials" not in json.dumps(result.to_dict())


def test_anchor_does_not_repair_invalid_signature(bundle, monkeypatch):
    receipt = Receipt.from_bytes(bundle[0])
    changed = replace(receipt, localTime=receipt.localTime + 1)
    digest = commitments(changed, bundle[1])["OTS_SHA256_C"]
    install_node(monkeypatch, digest)
    result = verify_receipt_ots(changed.to_bytes(), bundle[1], proof=proof_for(digest, True), config=OTSConfig())
    assert result.installation_signature.status == "invalid"
    assert result.anchor.status == "verified"


def test_absent_missing_and_oversized_evidence(bundle):
    assert verify_receipt_ots(*bundle, proof=None, config=OTSConfig()).anchor.status == "absent"
    pending = proof_for(expected(bundle)["OTS_SHA256_C"])
    assert verify_receipt_ots(bundle[0], None, proof=pending, config=OTSConfig()).anchor.status == "indeterminate"
    assert verify_receipt_ots(*bundle, proof=b"x" * (16 * 1024 * 1024 + 1), config=OTSConfig()).anchor.status == "failed"


def test_upgrade_and_verify_use_same_commitment(bundle, tmp_path, fake_calendars, monkeypatch):
    submission = stamp_receipt(*bundle, tmp_path, config=OTSConfig())
    path = Path(submission.settlement.proof_file)
    def upgrade(calendar, msg, timeout):
        stamp = Timestamp(msg)
        stamp.attestations.add(BitcoinBlockHeaderAttestation(100))
        return stamp
    monkeypatch.setattr("settlement.ots_backend.get_timestamp_within", upgrade)
    result = SettlementService().upgrade_proof(path)
    assert result.status is Status.PENDING
    # Randomized stamping means the attested message differs from SHA256(C).
    from settlement.ots_backend import OTSBackend
    parsed = OTSBackend().parse(path.read_bytes())
    msg = next(msg for msg, att in parsed.timestamp.all_attestations() if isinstance(att, BitcoinBlockHeaderAttestation))
    install_node(monkeypatch, msg)
    verified = verify_receipt_ots(*bundle, proof=path.read_bytes(), config=OTSConfig())
    assert verified.anchor.status == "verified"


def test_bundle_roundtrip_and_network_owned_by_verifier(bundle, tmp_path, monkeypatch):
    from fingerprinto.bundle import EvidenceBundle, export_bundle, import_bundle, verify_bundle_ots
    digest = expected(bundle)["OTS_SHA256_C"]
    install_node(monkeypatch, digest, chain="main")
    evidence = EvidenceBundle(*bundle, ots_proof=proof_for(digest, True),
                              anchor_locator=b'{"backend":"opentimestamps-bitcoin","network":"regtest"}')
    path = export_bundle(evidence, tmp_path / "bundle.zip")
    result = verify_bundle_ots(import_bundle(path), config=OTSConfig(network="mainnet"))
    assert result.anchor.status == "verified"
    assert result.anchor_details["network"] == "mainnet"


def test_locator_without_proof_does_not_claim_absence(bundle):
    from fingerprinto.bundle import EvidenceBundle, verify_bundle_ots
    evidence = EvidenceBundle(*bundle, anchor_locator=b'{"backend":"opentimestamps-bitcoin","network":"mainnet"}')
    result = verify_bundle_ots(evidence, config=OTSConfig())
    assert result.anchor.status == "indeterminate"


def test_cli_bitcoin_verification_remains_incomplete_without_origin(bundle, tmp_path, monkeypatch, capsys):
    from fingerprinto.bundle import EvidenceBundle, export_bundle
    from fingerprinto.cli import main
    digest = expected(bundle)["OTS_SHA256_C"]
    install_node(monkeypatch, digest)
    path = export_bundle(EvidenceBundle(*bundle, ots_proof=proof_for(digest, True)), tmp_path / "cli.zip")
    assert main(["verify-ots", str(path), "--network", "mainnet"]) == 2
    result = json.loads(capsys.readouterr().out)
    assert result["anchor"]["status"] == "verified"
    assert result["origin"]["status"] == result["enrollment"]["status"] == "indeterminate"
    assert result["anchor_details"]["network"] == "mainnet"
