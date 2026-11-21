"""Real OTS byte conventions with mocked calendars/RPC, not real inclusion."""
from dataclasses import replace
from datetime import datetime, timezone
import json
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from conftest import proof_bytes
from fingerprinto.anchoring_v3 import stamp_v3, verify_v3_ots
from fingerprinto.bundle import EvidenceBundle
from fingerprinto.core import digest
from fingerprinto.receipt_v3 import ReceiptV3
from fingerprinto.trust_v3 import TrustStore, sign_record
from settlement import OTSConfig


@pytest.fixture
def case_v3(tmp_path):
    c=json.loads((Path(__file__).parent/"fingerprinto/fixtures/v3.json").read_text())["cases"][0]
    bundle=EvidenceBundle(bytes.fromhex(c["receipt"]),bytes.fromhex(c["asset"]),profile="evidence-v3",
                          policy=c["policy"].encode(),report=c["report"].encode())
    f=ReceiptV3.from_bytes(bundle.receipt).fields
    key=ec.generate_private_key(ec.SECP256R1())
    public=key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    store=TrustStore(tmp_path/"store.db",authority_spki=public)
    now=datetime.now(timezone.utc)
    record={"schema":"fingerprinto/enrollment-v3/1","installation":digest(f.pk_I).hex(),
            "package":f.packageName,"signer":f.signerDigest.hex(),"minimumVersion":0,"sequence":1,
            "notBefore":int(now.timestamp())-10,"expires":int(now.timestamp())+600,"status":"active",
            "attestationPolicy":"a"*64,"references":[]}
    store.retain(sign_record(record,key))
    return bundle,store,now


def test_v3_stamp_retry_and_pending(case_v3,tmp_path,fake_calendars,monkeypatch):
    bundle,store,now=case_v3
    options=dict(config=OTSConfig(),now=now,store=store)
    submission=stamp_v3(bundle,tmp_path/"anchors",**options)
    expected=bundle.files()["commitment.bin"]
    assert submission.commitment_file.read_bytes()==expected
    proof=Path(submission.settlement.proof_file).read_bytes()
    def forbidden(*args,**kwargs):
        raise AssertionError("retry resubmitted")
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.stamp",forbidden)
    assert stamp_v3(bundle,tmp_path/"anchors",**options).commitment_file==submission.commitment_file
    result=verify_v3_ots(replace(bundle,ots_proof=proof),**options)
    assert result["anchor"]=="pending"
    assert result["decision"]["level"]=="none"


def test_v3_anchor_binding_and_enrollment_remain_separate(case_v3,trusted_node,payload):
    bundle,store,now=case_v3
    c=bundle.files()["commitment.bin"]
    payload.write_bytes(c)
    proof=proof_bytes(c,bitcoin=True)
    result=verify_v3_ots(replace(bundle,ots_proof=proof),config=OTSConfig(network="mainnet"),now=now)
    assert result["anchor"]=="verified"
    assert result["decision"]["state"]=="pending"
    wrong=proof_bytes(b"wrong payload",bitcoin=False)
    result=verify_v3_ots(replace(bundle,ots_proof=wrong),config=OTSConfig(),now=now,store=store)
    assert result["anchor"]=="failed"
    assert result["decision"]["state"]=="failed"
