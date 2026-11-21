"""Explicit synthetic enrollment only, used by offline tests and Docker demo tools."""
import base64
from dataclasses import replace
from datetime import datetime,timezone,timedelta
import hashlib
import importlib.util
import json
from pathlib import Path
import uuid
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
from nostr_sdk import Keys,EventBuilder,Kind,Tag
from social.config import Config
from social.provision import initialize
from social.evidence import fields_to_json,fields_from_json
from fingerprinto.core import Receipt
from fingerprinto.enrollment_v3 import possession_root


def setup(directory):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    spec=importlib.util.spec_from_file_location('photo_synthetic_attestation',Path(__file__).parent/'fingerprinto/test_enrollment.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    t=module.EnrollmentTests();t.setUp();t.now=datetime.now(timezone.utc)
    t.root=t.certificate(t.rootkey,t.rootkey,t.rootname,t.rootname,ca=True,serial=1)
    initialize(directory/'authority',directory/'config')
    cfg=directory/'config/config.json';settings=json.loads(cfg.read_text())
    settings['enrollment_policy']=dict(id='test-only',roots=[base64.b64encode(t.root.public_bytes(serialization.Encoding.DER)).decode()],packages={'example.photo':1},signers=[t.signer.hex()])
    rev=directory/'config/revocations.json'
    rev.write_text(json.dumps(dict(blocked_serials=[],observed_at=(t.now-timedelta(seconds=10)).isoformat(),valid_until=(t.now+timedelta(days=1)).isoformat())))
    settings['revocations_file']=str(rev);cfg.write_text(json.dumps(settings))
    config=Config(data_dir=directory/'data',evidence_config=str(cfg),network='regtest',calendars=('http://calendar:14788',),retry_seconds=.01,lease_seconds=2)
    return t,config


def enrollment(t,challenge,operation=None):
    t.challenge=bytes.fromhex(challenge)
    f=Receipt(t.public,t.chain(),'example.photo',1,t.signer,'SYNTHETIC','SYNTHETIC',int(t.now.timestamp()*1000),t.challenge,0,b'x'*8)
    proof=t.leafkey.sign(possession_root(f),ec.ECDSA(hashes.SHA256()))
    return dict(operation_id=operation or uuid.uuid4().hex,fields=fields_to_json(f),possession=base64.b64encode(proof).decode())


def auth(keys,method,path,body,base='http://127.0.0.1:8080'):
    tags=[['u',base+path],['method',method],['payload',hashlib.sha256(body).hexdigest()],['nonce',uuid.uuid4().hex]]
    event=EventBuilder(Kind(27235),'').tags([Tag.parse(t) for t in tags]).finalize(keys)
    return {'Authorization':'Nostr '+base64.b64encode(event.as_json().encode()).decode()}
