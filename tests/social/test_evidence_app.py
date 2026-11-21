"""Application-level v3 tests: real SDK/PKI/CBOR/Pillow; fake RPC/calendar only."""
import asyncio
import base64
from dataclasses import replace
import hashlib
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import random
import time
import uuid

import httpx
from PIL import Image
import pytest
from nostr_sdk import Keys, EventBuilder,Kind,Tag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from fingerprinto.core import digest,domain
from fingerprinto.receipt_v3 import ReceiptV3
from fingerprinto.prnu import Geometry
from fingerprinto.prnu_image import decode_green,DECODER
from social.api import create_app
from social.evidence import EvidenceService,un64
from social.evidence_worker import EvidenceWorker
from social.evidence_store import Conflict
from social.provision import approve
from settlement import SettlementService

spec=importlib.util.spec_from_file_location('photo_v3_support',Path(__file__).parents[1]/'v3_support.py')
support=importlib.util.module_from_spec(spec);spec.loader.exec_module(support)
PHOTO=(Path(__file__).parents[2]/'contracts/fixtures/photo.jpg').read_bytes()

class Client:
    def __init__(self,app,keys):self.app=app;self.keys=keys
    def request(self,method,path,data=None,raw=None,headers=None,private=True):
        body=raw if raw is not None else (json.dumps(data,separators=(',',':')).encode() if data is not None else b'')
        h=headers if headers is not None else (support.auth(self.keys,method,path,body) if private else {})
        async def run():
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app,raise_app_exceptions=False),base_url='http://testserver') as c:
                return await c.request(method,path,content=body,headers=h)
        return asyncio.run(run())

@pytest.fixture
def case(tmp_path):
    t,cfg=support.setup(tmp_path);app=create_app(cfg);keys=Keys.generate();client=Client(app,keys)
    yield t,cfg,app,client
    t.doCleanups()

def enroll(case):
    t,cfg,app,client=case
    response=client.request('POST','/v1/enrollments/challenge',{'operation_id':uuid.uuid4().hex})
    assert response.status_code==200,response.text
    body=support.enrollment(t,response.json()['challenge'])
    response=client.request('POST','/v1/enrollments',body)
    assert response.status_code==202,response.text
    worker=EvidenceWorker(cfg,SettlementService(cfg.ots_config()));assert worker.tick()
    response=client.request('GET','/v1/enrollments/'+body['operation_id'])
    assert response.json()['state']=='ACCEPTED',response.text
    return body['operation_id'],worker

def prepare(case):
    t,cfg,app,client=case;eid,worker=enroll(case)
    response=client.request('POST','/v1/media',raw=PHOTO,headers={'content-type':'image/jpeg'},private=False)
    media=response.json()['sha256'];identity=uuid.uuid4().hex
    data=dict(operation_id=identity,enrollment_id=eid,media_sha256=media,time=int(time.time()*1000))
    response=client.request('POST','/v1/evidence',data);assert response.status_code==202,response.text
    assert worker.tick()
    response=client.request('GET','/v1/evidence/'+identity)
    assert response.json()['state']=='READY',response.text
    output=response.json()['output'];draft=ReceiptV3.from_bytes(un64(output['template']))
    sig=t.leafkey.sign(draft.roots(PHOTO)['R'],ec.ECDSA(hashes.SHA256()))
    receipt=draft.with_signature(sig).to_bytes()
    return identity,output,receipt,media,worker

def post(keys,media,commitment):
    tags=[['title','Photo'],['imeta','url http://127.0.0.1:8080/v1/media/'+media,'m image/jpeg','x '+media,'dim 64x48'],['m','image/jpeg'],['x',media],['photo-evidence','fingerprinto/evidence-v3',commitment]]
    return json.loads(EventBuilder(Kind(20),'').tags([Tag.parse(t) for t in tags]).finalize(keys).as_json())

def test_real_enrollment_recovery_and_private_ownership(case):
    eid,worker=enroll(case);t,cfg,app,client=case
    view=client.request('GET','/v1/enrollments/'+eid).json()
    assert view['output']['installation']==digest(t.public).hex()
    other=Client(app,Keys.generate())
    assert other.request('GET','/v1/enrollments/'+eid).status_code==404
    # Retry after acceptance recovers the retained decision instead of consuming twice.
    svc=app.state.evidence;row=svc.store.get(eid)
    state,again=svc.process_enrollment(row)
    assert state=='ACCEPTED' and again==view['output']

def test_auth_replay_method_body_and_owner(case):
    _,cfg,app,client=case;path='/v1/enrollments/challenge';body=json.dumps({'operation_id':uuid.uuid4().hex}).encode()
    h=support.auth(client.keys,'POST',path,body)
    assert client.request('POST',path,raw=body,headers=h).status_code==200
    assert client.request('POST',path,raw=body,headers=h).status_code==401
    h=support.auth(client.keys,'POST',path,body)
    assert client.request('POST',path,raw=body+b' ',headers=h).status_code==401
    h=support.auth(client.keys,'GET',path,body)
    assert client.request('POST',path,raw=body,headers=h).status_code==401

def test_preparation_receipt_post_binding_retry_and_tamper(case):
    identity,out,raw,media,worker=prepare(case);t,cfg,app,client=case
    body=out['envelope']['body'].encode();authority=app.state.evidence.authority()
    authority.public_key().verify(bytes.fromhex(out['envelope']['signature']),domain('photo/evidence-preparation/1',digest(body)),ec.ECDSA(hashes.SHA256()))
    response=client.request('POST',f'/v1/evidence/{identity}/receipt',raw=raw)
    assert response.status_code==200,response.text
    assert response.json()['assessment']['decision']['level']=='none'
    commitment=response.json()['commitment']
    assert client.request('POST',f'/v1/evidence/{identity}/receipt',raw=raw).json()['commitment']==commitment
    modified=ReceiptV3.from_bytes(raw).with_signature(t.leafkey.sign(ReceiptV3.from_bytes(raw).roots(PHOTO)['R'],ec.ECDSA(hashes.SHA256()))).to_bytes()
    assert client.request('POST',f'/v1/evidence/{identity}/receipt',raw=modified).status_code in (400,409)
    ev=post(client.keys,media,commitment)
    accepted=client.request('POST','/v1/posts',{'event':ev},private=False)
    assert accepted.status_code==202,accepted.text
    assert accepted.json()['evidence']['commitment']==commitment
    wrong=post(Keys.generate(),media,commitment)
    assert client.request('POST','/v1/posts',{'event':wrong},private=False).status_code==409
    bundle=client.request('GET',f'/v1/posts/{ev["id"]}/evidence/bundle',private=False)
    assert bundle.status_code==200
    assert client.request('GET',f'/v1/posts/{ev["id"]}/evidence/proof',private=False).status_code==404

def test_changed_prepare_input_conflict_and_missing_policy(case):
    identity,out,raw,media,worker=prepare(case);_,cfg,app,client=case
    row=app.state.evidence.store.get(identity);changed=dict(row['input'],time=row['input']['time']+1)
    assert client.request('POST','/v1/evidence',changed).status_code==409
    other=create_app(replace(cfg,data_dir=cfg.data_dir/'empty',evidence_config=None));c=Client(other,Keys.generate())
    assert c.request('POST','/v1/enrollments/challenge',{'operation_id':uuid.uuid4().hex}).status_code==503
    assert c.request('POST','/v1/media',raw=PHOTO,headers={'content-type':'image/jpeg'},private=False).status_code==201

def test_orientation_profile_preserves_original_bytes():
    stream=BytesIO();exif=Image.Exif();exif[274]=6
    Image.new('RGB',(32,32),(110,120,130)).save(stream,format='JPEG',exif=exif)
    raw=stream.getvalue();geom=Geometry('synthetic','route',32,32,'pillow-13.0.0/rgb-green-stored-exif-6/1',32,32)
    assert len(decode_green(raw,geom,source_width=32,source_height=32))==1024
    assert raw==stream.getvalue()
    with pytest.raises(ValueError):decode_green(raw,replace(geom,decoder=DECODER),source_width=32,source_height=32)

def test_private_calibration_splits_and_unapproved_scores(case):
    eid,worker=enroll(case);_,cfg,app,client=case;identity=uuid.uuid4().hex
    geometry=dict(camera='synthetic',mode='fixed-route',width=32,height=32,decoder=DECODER,source_width=32,source_height=32)
    response=client.request('POST','/v1/calibrations',dict(operation_id=identity,enrollment_id=eid,geometry=geometry));assert response.status_code==202
    rng=random.Random(7021);pattern=[rng.gauss(0,2) for _ in range(1024)]
    def image():
        output=BytesIO();values=[max(0,min(255,round(120+p+rng.gauss(0,.5)))) for p in pattern]
        Image.frombytes('RGB',(32,32),bytes(v for p in values for v in (p,p,p))).save(output,format='JPEG',quality=100,subsampling=0)
        return output.getvalue()
    first=None
    for role in ('enrollment','enrollment','enrollment','development','genuine','impostor'):
        raw=image();h=digest(raw).hex();path=f'/v1/calibrations/{identity}/frames/{role}/{h}'
        response=client.request('PUT',path,raw=raw);assert response.status_code==200,response.text
        if first is None:first=(raw,h)
    raw,h=first
    assert client.request('PUT',f'/v1/calibrations/{identity}/frames/genuine/{h}',raw=raw).status_code==409
    assert Client(app,Keys.generate()).request('GET','/v1/calibrations/'+identity).status_code==404
    assert client.request('POST',f'/v1/calibrations/{identity}/evaluate',{}).status_code==200
    assert worker.tick()
    result=client.request('GET','/v1/calibrations/'+identity).json()
    assert result['state']=='COMPLETE',result
    assert result['output']['calibration_approved'] is False
    assert len(result['output']['scores'])==3
    with pytest.raises(ValueError):approve(app.state.evidence,identity,1,None,False)

def test_expired_challenge_never_enrolls(case):
    t,cfg,app,client=case
    challenge=client.request('POST','/v1/enrollments/challenge',{'operation_id':uuid.uuid4().hex}).json()
    body=support.enrollment(t,challenge['challenge'])
    assert client.request('POST','/v1/enrollments',body).status_code==202
    import sqlite3
    with sqlite3.connect(app.state.evidence.store.root/'intake.sqlite3') as db:
        db.execute('UPDATE enrollment_requests SET expires=?',(time.time()-1,))
    assert EvidenceWorker(cfg,SettlementService(cfg.ots_config())).tick()
    result=client.request('GET','/v1/enrollments/'+body['operation_id']).json()
    assert result['state']=='EXPIRED',result
    assert app.state.evidence.trust().history(digest(t.public))==()

def test_revocation_blocks_new_attachment_but_not_ordinary_publication(case):
    identity,out,raw,media,worker=prepare(case);t,cfg,app,client=case
    response=client.request('POST',f'/v1/evidence/{identity}/receipt',raw=raw)
    commitment=response.json()['commitment'];svc=app.state.evidence
    from fingerprinto.trust_v3 import sign_record
    store=svc.trust();body=store.authenticate(store.history(digest(t.public))[-1]);body.update(sequence=2,status='revoked')
    store.retain(sign_record(body,svc.authority()))
    signed=post(client.keys,media,commitment)
    assert client.request('POST','/v1/posts',{'event':signed},private=False).status_code==409
    assert app.state.store.get(signed['id']) is None
    tags=[Tag.parse(t) for t in signed['tags'] if t[0]!='photo-evidence']
    ordinary=json.loads(EventBuilder(Kind(20),'').tags(tags).finalize(client.keys).as_json())
    response=client.request('POST','/v1/posts',{'event':ordinary},private=False)
    assert response.status_code==202 and response.json()['evidence'] is None

def test_receipt_db_failure_is_recoverable_without_overwrite(case):
    identity,out,raw,media,worker=prepare(case);_,cfg,app,client=case;store=app.state.evidence.store
    with store.connection() as db:
        db.execute("CREATE TRIGGER fail_attach BEFORE INSERT ON attachments BEGIN SELECT RAISE(ABORT,'test unavailable'); END")
    response=client.request('POST',f'/v1/evidence/{identity}/receipt',raw=raw)
    assert response.status_code==500
    assert 'test unavailable' not in response.text
    assert (store.receipts/identity/'receipt.cbor').read_bytes()==raw
    with store.connection() as db:db.execute('DROP TRIGGER fail_attach')
    response=client.request('POST',f'/v1/evidence/{identity}/receipt',raw=raw)
    assert response.status_code==200,response.text
    assert (store.receipts/identity/'receipt.cbor').read_bytes()==raw

def test_expired_lease_fences_stale_worker(case):
    _,cfg,app,client=case;store=app.state.evidence.store
    identity=uuid.uuid4().hex;store.enqueue(identity,client.keys.public_key().to_hex(),'evidence',{})
    old=store.claim();assert old[0]==identity
    with store.connection() as db:db.execute('UPDATE operations SET lease_until=0 WHERE id=?',(identity,))
    fresh=store.claim();assert fresh[1]!=old[1]
    assert store.finish(identity,old[1],state='READY',output={'bad':True}) is False
    assert store.finish(identity,fresh[1],state='READY',output={'fresh':True}) is True
    assert store.get(identity)['output']=={'fresh':True}

def test_shared_app_vector_is_bound_and_signed():
    obj=json.loads((Path(__file__).parents[2]/'contracts/fixtures/app-v3.json').read_text())
    receipt=ReceiptV3.from_bytes(un64(obj['receipt']));photo=un64(obj['photo']);roots=receipt.verify_signature(photo)
    assert roots['C'].hex()==obj['commitment']
    receipt.evidence.attachments(un64(obj['policy']),un64(obj['report']))
    from upp_protocol import build_payload,verify_photo_bytes
    from upp_protocol.protocol import evidence_commitment
    event=verify_photo_bytes(obj['event'],photo)
    assert evidence_commitment(event.event)==obj['commitment']
    assert json.loads(build_payload(event.event))[1]==obj['event']['id']

def test_bad_signature_is_invalid_receipt_not_operational_error(case):
    identity,out,raw,media,worker=prepare(case);_,cfg,app,client=case
    receipt=ReceiptV3.from_bytes(raw)
    signature=bytearray(receipt.fields.sigma);signature[-1]^=1
    bad=receipt.with_signature(bytes(signature)).to_bytes()
    response=client.request('POST',f'/v1/evidence/{identity}/receipt',raw=bad)
    assert response.status_code==400 and response.json()['error']['code']=='invalid_receipt'
    assert not (app.state.evidence.store.receipts/identity/'receipt.cbor').exists()

def test_auth_storage_failure_is_unavailable_and_sanitized(case):
    _,cfg,app,client=case
    with app.state.evidence.store.connection() as db:
        db.execute('DROP TABLE http_auth_replay')
    response=client.request('POST','/v1/enrollments/challenge',{'operation_id':uuid.uuid4().hex})
    assert response.status_code==503,response.text
    assert response.json()['error']['code']=='authorization_unavailable'
    assert 'sqlite' not in response.text and 'no such table' not in response.text

def test_embedded_candidate_preserved_without_manufacturer_pass(case):
    eid,worker=enroll(case);_,cfg,app,client=case
    payload=b'JP'+b'unsupported synthetic JUMBF candidate'
    jpeg=PHOTO[:2]+b'\xff\xeb'+(len(payload)+2).to_bytes(2,'big')+payload+PHOTO[2:]
    response=client.request('POST','/v1/media',raw=jpeg,headers={'content-type':'image/jpeg'},private=False)
    assert response.status_code==201,response.text
    identity=uuid.uuid4().hex
    response=client.request('POST','/v1/evidence',dict(operation_id=identity,enrollment_id=eid,media_sha256=digest(jpeg).hex(),time=int(time.time()*1000)))
    assert response.status_code==202
    assert worker.tick()
    state=client.request('GET','/v1/evidence/'+identity).json()
    assert state['state']=='READY',state
    template=ReceiptV3.from_bytes(un64(state['output']['template']))
    report=json.loads(un64(state['output']['report']))
    assert template.evidence.placement==0
    assert report['manufacturer']['status']=='unavailable'
    assert app.state.evidence.asset(digest(jpeg).hex())==jpeg
