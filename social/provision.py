"""Operator-only local authority setup and calibration approval.

Never exposed through the HTTP API. Roots/revocation material are external,
operator-authenticated deployment inputs; no synthetic policy is installed.
"""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fingerprinto.core import digest
from fingerprinto.trust_v3 import sign_record
from fingerprinto.prnu_records import json_bytes,evaluate_samples
from .config import Config
from .evidence import EvidenceService,b64
from .storage import publish_bytes


def initialize(authority_dir,config_dir):
    authority_dir=Path(authority_dir);config_dir=Path(config_dir)
    authority_dir.mkdir(mode=0o700,parents=True,exist_ok=True);config_dir.mkdir(parents=True,exist_ok=True)
    path=authority_dir/'authority.pem'
    if not path.exists():
        key=ec.generate_private_key(ec.SECP256R1())
        raw=key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())
        fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
        with os.fdopen(fd,'wb') as stream:stream.write(raw);stream.flush();os.fsync(stream.fileno())
    key=serialization.load_pem_private_key(path.read_bytes(),password=None)
    spki=key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    publish_bytes(config_dir/'authority.der',spki)
    cfg=config_dir/'config.json'
    if not cfg.exists():publish_bytes(cfg,json_bytes(dict(authority_spki=b64(spki),authority_key_file=str(path),enrollment_policy=None,revocations_file=None)))
    return {'authority_sha256':digest(spki).hex(),'enrollment_configured':bool(json.loads(cfg.read_text()).get('enrollment_policy'))}


def approve(service,identity,accept,reject,confirm):
    if not confirm:raise ValueError('Explicit independent physical-camera association is required')
    row=service.store.get(identity)
    if row is None or row['kind']!='calibration' or row['state']!='COMPLETE':raise ValueError('Completed calibration required')
    output=row['output'];f,record=service.enrolled_fields(row['input']['enrollment_id'],row['owner'])
    samples=[dict(asset=s['asset'],device='enrolled-camera' if s['role']=='genuine' else 'other-camera',
                  mode=output['geometry']['mode'],genuine=s['role']=='genuine',score=s['score']) for s in output['scores'] if s['role'] in ('genuine','impostor')]
    if not any(s['genuine'] for s in samples) or not any(not s['genuine'] for s in samples):raise ValueError('Held-out genuine and impostor samples are required')
    report=evaluate_samples(samples,enrollment_ids=output['enrollment'],development_ids=output['development'],accept=accept,reject=reject)
    report_raw=json_bytes(report);ch=digest(report_raw).hex()
    config=dict(method='fingerprinto/prnu-haar-green/1',geometry=output['geometry'],accept=accept,reject=reject,calibrationHash=ch)
    policy=json_bytes({'schema':'fingerprinto/policy-v3/1','prnu':config})
    association=dict(hash=output['reference_hash'],geometry=output['geometry'],policyHash=digest(policy).hex(),calibrationHash=ch,accept=accept,reject=reject)
    body=dict(record,sequence=record['sequence']+1,references=[r for r in record['references'] if r['hash']!=association['hash']]+[association])
    publish_bytes(service.store.references/(ch+'.calibration.json'),report_raw)
    service.trust().retain(sign_record(body,service.authority()))
    return {'reference_hash':association['hash'],'sequence':body['sequence'],'report':report,'approved_by_operator':True}


def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    init=sub.add_parser('init');init.add_argument('--authority-dir',required=True);init.add_argument('--config-dir',required=True)
    configure=sub.add_parser('configure');configure.add_argument('--policy',required=True);configure.add_argument('--revocations-file',required=True)
    approval=sub.add_parser('approve-calibration');approval.add_argument('id');approval.add_argument('--accept',type=float);approval.add_argument('--reject',type=float);approval.add_argument('--confirm-physical-association',action='store_true')
    args=parser.parse_args()
    try:
        if args.command=='init':result=initialize(args.authority_dir,args.config_dir)
        elif args.command=='configure':
            config=Config.from_env();service=EvidenceService(config);settings=service.settings()
            settings.update(enrollment_policy=json.loads(Path(args.policy).read_text()),revocations_file=args.revocations_file)
            # Validate before atomic replacement; credentials are never printed.
            from tempfile import NamedTemporaryFile
            with NamedTemporaryFile(dir=Path(config.evidence_config).parent,delete=False) as tmp:
                tmp.write(json_bytes(settings));name=tmp.name
            from dataclasses import replace
            try:
                check=EvidenceService(replace(config,evidence_config=name));check.policy();check.revocations()
                os.replace(name,config.evidence_config)
            finally:Path(name).unlink(missing_ok=True)
            result={'configured':True}
        else:result=approve(EvidenceService(Config.from_env()),args.id,args.accept,args.reject,args.confirm_physical_association)
        print(json.dumps(result));return 0
    except Exception:
        print(json.dumps({'error':{'code':'provisioning_failed','message':'Operator configuration or approval is invalid/unavailable.'}}));return 1


if __name__=='__main__':raise SystemExit(main())
