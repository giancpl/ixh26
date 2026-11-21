"""Independent transaction/recovery regressions from PR #9 review; synthetic evidence only."""
import sys, sqlite3
from pathlib import Path
from dataclasses import replace
from concurrent.futures import ThreadPoolExecutor
import pytest
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
import test_enrollment as enrollment_fixtures
import test_workflow_v3 as workflow_fixtures
from fingerprinto.core import Receipt,digest
from fingerprinto.enrollment_v3 import intake,possession_root,recover_intake,retain_accepted
from fingerprinto.trust_v3 import TrustStore,sign_record
from fingerprinto.workflow_v3 import Finding
from fingerprinto.evidence_v3 import Outcome

def spki(key):
    return key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)

@pytest.fixture
def case():
    t=enrollment_fixtures.EnrollmentTests();t.setUp()
    f=Receipt(t.public,t.chain(),'example.photo',1,t.signer,'synthetic','synthetic',0,t.challenge,0,b'x'*8)
    signature=t.leafkey.sign(possession_root(f),ec.ECDSA(hashes.SHA256()))
    args=dict(policy=t.policy,requests=t.requests,revocations=t.revocations,now=t.now)
    yield t,f,signature,args
    t.doCleanups()

def test_acceptance_insert_failure_rolls_back_challenge(case):
    t,f,sig,args=case
    with sqlite3.connect(t.requests.path) as db:
        db.execute("CREATE TRIGGER fail_accept BEFORE INSERT ON enrollment_acceptances BEGIN SELECT RAISE(ABORT,'test failure'); END")
    assert intake(f,sig,**args).enrollment.status=='indeterminate'
    with sqlite3.connect(t.requests.path) as db:
        assert db.execute('SELECT consumed FROM enrollment_requests WHERE challenge=?',(t.challenge,)).fetchone()==(None,)
        assert db.execute('SELECT count(*) FROM enrollment_acceptances').fetchone()==(0,)
        db.execute('DROP TRIGGER fail_accept')
    assert intake(f,sig,**args).enrollment.status=='accepted'

def test_recover_exact_original_and_reject_changed_scope(case):
    t,f,sig,args=case
    accepted=intake(f,sig,**args)
    recovered=recover_intake(f,policy=t.policy,requests=t.requests)
    assert accepted==recovered
    assert recover_intake(f,policy=replace(t.policy,identifier='changed'),requests=t.requests) is None
    assert recover_intake(replace(f,sessionChallenge=b'z'*32),policy=t.policy,requests=t.requests) is None
    with pytest.raises(ValueError):
        recover_intake(replace(f,versionCode=2),policy=t.policy,requests=t.requests)

def test_legacy_acceptance_cannot_be_promoted_to_v3(case):
    t,f,sig,args=case
    assert t.verify().enrollment.status=='accepted'
    with pytest.raises(ValueError):
        recover_intake(f,policy=t.policy,requests=t.requests)

def test_publish_race_retry_and_no_revocation_resurrection(case,tmp_path):
    t,f,sig,args=case
    accepted=intake(f,sig,**args)
    key=ec.generate_private_key(ec.SECP256R1())
    store=TrustStore(tmp_path/'trust.db',authority_spki=spki(key))
    opts=dict(authority_private_key=key,store=store,sequence=1,not_before=int(t.now.timestamp())-1,expires=int(t.now.timestamp())+300)
    with ThreadPoolExecutor(max_workers=4) as pool:
        outputs=list(pool.map(lambda _:retain_accepted(f,accepted,**opts),range(8)))
    assert len(set(outputs))==1
    assert len(store.history(digest(f.pk_I)))==1
    body=store.authenticate(outputs[0]);body.update(sequence=2,status='revoked')
    store.retain(sign_record(body,key))
    with pytest.raises(ValueError): retain_accepted(f,accepted,**opts)
    assert store.lookup(f,now=t.now)[1]=='installation_revoked'
    assert len(store.history(digest(f.pk_I)))==2

def test_store_migration_and_index_consistency(tmp_path):
    t=workflow_fixtures.WorkflowV3Tests();t.setUp()
    try:
        raw=sign_record(t.body,t.authority)
        path=tmp_path/'old.db'
        with sqlite3.connect(path) as db:
            db.execute('CREATE TABLE enrollment_v3 (installation TEXT PRIMARY KEY,sequence INTEGER NOT NULL,record BLOB NOT NULL)')
            db.execute('INSERT INTO enrollment_v3 VALUES (?,?,?)',(t.body['installation'],1,raw))
        store=TrustStore(path,authority_spki=spki(t.authority))
        assert store.history(digest(t.fields.pk_I))==(raw,)
        assert store.lookup(t.fields,now=t.now)[1]=='enrollment_record_valid'
        store.retain(sign_record(dict(t.body,sequence=2,status='revoked'),t.authority))
        with sqlite3.connect(path) as db:
            db.execute('UPDATE enrollment_v3 SET sequence=1,record=?',(raw,))
        with pytest.raises(ValueError):store.lookup(t.fields,now=t.now)
    finally:t.doCleanups()

def test_oversize_report_fails_before_hardware_signing():
    t=workflow_fixtures.WorkflowV3Tests();t.setUp()
    try:
        validator=lambda *args: Finding(Outcome.INCONCLUSIVE,'x'*(1024*1024),{})
        with pytest.raises(ValueError):t.issue(manufacturer_validator=validator)
        assert t.calls==0
        with sqlite3.connect(Path(t.tmp.name)/'journal.db') as db:
            assert db.execute('SELECT count(*) FROM signed_receipts').fetchone()==(0,)
    finally:t.doCleanups()
