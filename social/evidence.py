"""Application v3 orchestration. Phone keys never enter this service."""
import base64
from dataclasses import asdict, replace
from datetime import datetime, timezone
from io import BytesIO
import json
from pathlib import Path
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from PIL import Image
from fingerprinto.core import Receipt, digest, domain
from fingerprinto.receipt_v3 import ReceiptV3, json_object
from fingerprinto.prnu_records import json_bytes, reference_bytes, read_reference, evaluate_samples
from fingerprinto.prnu import Geometry, Estimator, score
from fingerprinto.prnu_image import decode_green
from fingerprinto.trust_v3 import TrustStore
from fingerprinto.enrollment import EnrollmentPolicy, RevocationSnapshot
from fingerprinto.enrollment_requests import EnrollmentRequests
from fingerprinto.enrollment_v3 import intake, recover_intake, retain_accepted
from fingerprinto.workflow_v3 import prepare_v3, verify_v3, embedded_candidate
from fingerprinto.anchoring_v3 import verify_v3_ots
from fingerprinto.bundle import EvidenceBundle
from .evidence_store import EvidenceStore, Conflict, HASH
from .storage import publish_bytes, canonical

PLACEHOLDER = bytes.fromhex('3006020101020101')


def b64(raw):return base64.b64encode(raw).decode()
def un64(raw):return base64.b64decode(raw,validate=True)
def utc():return datetime.now(timezone.utc)


def fields_from_json(obj):
    if type(obj) is not dict or set(obj)!={'key','chain','package','version','signer','manufacturer','model','time','challenge'}:
        raise ValueError('Invalid installation fields')
    return Receipt(un64(obj['key']),tuple(un64(x) for x in obj['chain']),obj['package'],obj['version'],
        bytes.fromhex(obj['signer']),obj['manufacturer'],obj['model'],obj['time'],bytes.fromhex(obj['challenge']),0,PLACEHOLDER)


def fields_to_json(f):
    return dict(key=b64(f.pk_I),chain=[b64(c) for c in f.chain],package=f.packageName,version=f.versionCode,
                signer=f.signerDigest.hex(),manufacturer=f.manufacturer,model=f.model,time=f.localTime,challenge=f.sessionChallenge.hex())


class Unavailable(RuntimeError):pass


class EvidenceService:
    def __init__(self, config):
        self.config=config
        self.store=EvidenceStore(config)

    def settings(self):
        if not self.config.evidence_config:raise Unavailable('Evidence deployment is not configured')
        try:
            raw=Path(self.config.evidence_config).read_bytes()
            return json_object(raw)
        except (OSError,ValueError):raise Unavailable('Evidence deployment is unavailable') from None

    def trust(self):
        settings=self.settings()
        return TrustStore(self.store.root/'trust.sqlite3',authority_spki=un64(settings['authority_spki']))

    def authority(self):
        settings=self.settings()
        key=serialization.load_pem_private_key(Path(settings['authority_key_file']).read_bytes(),password=None)
        if not isinstance(key,ec.EllipticCurvePrivateKey) or not isinstance(key.curve,ec.SECP256R1):raise ValueError('P-256 authority required')
        spki=key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        if spki!=un64(settings['authority_spki']):raise ValueError('Authority pin mismatch')
        return key

    def policy(self):
        cfg=self.settings().get('enrollment_policy')
        if not cfg:raise Unavailable('Enrollment policy is not configured')
        return EnrollmentPolicy(identifier=cfg['id'],roots=tuple(un64(v) for v in cfg['roots']),
            packages=tuple((k,v) for k,v in cfg['packages'].items()),signer_digests=frozenset(bytes.fromhex(v) for v in cfg['signers']),
            allowed_boot_states=frozenset(cfg.get('boot_states',[0])),
            allowed_boot_keys=frozenset(bytes.fromhex(v) for v in cfg['boot_keys']) if cfg.get('boot_keys') else None,
            minimum_os_version=cfg.get('minimum_os_version',0),minimum_os_patch=cfg.get('minimum_os_patch',0))

    def revocations(self):
        path=self.settings().get('revocations_file')
        if not path:raise Unavailable('Current operator-owned revocation evidence is required')
        raw=Path(path).read_bytes();obj=json_object(raw)
        # This file is provisioned over an authenticated source by the operator.
        # Neither its URL nor contents are accepted from HTTP clients.
        return RevocationSnapshot(frozenset(int(v) for v in obj['blocked_serials']),
            datetime.fromisoformat(obj['observed_at']),datetime.fromisoformat(obj['valid_until']),digest(raw))

    def challenge(self,identity,owner):
        policy=self.policy()
        with self.store.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT * FROM challenges WHERE id=?',(identity,)).fetchone()
            if row:
                if row['owner']!=owner:raise Conflict('Challenge owner differs')
                return {k:row[k] for k in ('challenge','expires','policy')}
            challenge=EnrollmentRequests(self.store.root/'intake.sqlite3').issue(policy.digest,now=utc())
            expires=time.time()+300
            db.execute('INSERT INTO challenges VALUES(?,?,?,?,?)',(identity,owner,challenge.hex(),expires,policy.digest.hex()))
            return dict(challenge=challenge.hex(),expires=expires,policy=policy.digest.hex())

    def check_enrollment_input(self,data,owner):
        fields=fields_from_json(data['fields'])
        signature=un64(data['possession'])
        if not 8<=len(signature)<=72:raise ValueError('Invalid possession proof')
        with self.store.connection() as db:
            row=db.execute('SELECT * FROM challenges WHERE owner=? AND challenge=?',(owner,fields.sessionChallenge.hex())).fetchone()
        if row is None:raise ValueError('Challenge was not issued to this identity')
        return fields

    def enrolled_fields(self,identity,owner):
        row=self.store.get(identity,owner)
        if row is None or row['kind']!='enrollment':raise ValueError('Enrollment not found')
        f=fields_from_json(row['input']['fields'])
        record,reason=self.trust().lookup(f,now=utc())
        if row['state']!='ACCEPTED' or record is None:raise Unavailable(reason if record is None else 'Enrollment pending')
        return f,record

    def enrollment_view(self,identity,owner):
        row=self.store.get(identity,owner)
        if row is None:return None
        view=self.store.public(identity,owner)
        if row['kind']=='enrollment':
            view['fields']=row['input']['fields']
            if row['state']=='ACCEPTED':
                try:
                    fields=fields_from_json(row['input']['fields'])
                    trust=self.trust()
                    _,reason=trust.lookup(fields,now=utc())
                    if reason=='enrollment_record_valid':
                        view['output']['record']=b64(trust.history(digest(fields.pk_I))[-1])
                    if reason!='enrollment_record_valid':
                        view['state']='REJECTED' if reason in ('installation_revoked','enrolled_release_mismatch') else 'EXPIRED'
                        view['error']=reason
                except (OSError,ValueError,Unavailable):view.update(state='UNAVAILABLE',error='trust_unavailable')
        return view

    def asset(self,digest_hex):
        if not isinstance(digest_hex,str) or not HASH.fullmatch(digest_hex):raise ValueError('Invalid image hash')
        path=self.config.data_dir/'media'/(digest_hex+'.jpg')
        raw=path.read_bytes()
        if digest(raw).hex()!=digest_hex or path.is_symlink():raise ValueError('Stored JPEG mismatch')
        return raw

    def reference(self,identity):
        path=self.store.references/(identity.hex()+'.json')
        return path.read_bytes() if path.is_file() and not path.is_symlink() else None

    def process_enrollment(self,row):
        f=self.check_enrollment_input(row['input'],row['owner'])
        policy=self.policy();requests=EnrollmentRequests(self.store.root/'intake.sqlite3');now=utc()
        result=recover_intake(f,policy=policy,requests=requests)
        if result is None:result=intake(f,un64(row['input']['possession']),policy=policy,requests=requests,revocations=self.revocations(),now=now)
        else:
            # Recovery must not publish stale policy/status grants.
            from fingerprinto.enrollment import verify_enrollment
            class Fresh:
                def consume(self,*args,**kw):return True  # No grant; only re-evaluate already retained intake.
            fresh=verify_enrollment(f.pk_I,f.chain,policy=policy,requests=Fresh(),revocations=self.revocations(),now=now)
            if fresh.enrollment.status!='accepted':return 'UNAVAILABLE',fresh.to_dict()
        if result.enrollment.status!='accepted':
            status='REJECTED' if result.enrollment.status=='rejected' else 'UNAVAILABLE'
            if result.enrollment.code=='enrollment_challenge_unavailable':status='EXPIRED'
            return status,result.to_dict()
        accepted_at=int(datetime.fromisoformat(result.evaluated_at).timestamp())
        raw=retain_accepted(f,result,authority_private_key=self.authority(),store=self.trust(),sequence=1,
                            not_before=accepted_at,expires=accepted_at+86400*30)
        return 'ACCEPTED',dict(record=b64(raw),installation=digest(f.pk_I).hex())

    def calibration_view(self,identity,owner):
        view=self.store.public(identity,owner)
        if view and view['state']=='COMPLETE' and view['output']:
            row=self.store.get(identity,owner)
            try:
                _,record=self.enrolled_fields(row['input']['enrollment_id'],owner)
                approved=next((r for r in record['references'] if r['hash']==view['output']['reference_hash']),None)
                view['output']['association_trusted']=approved is not None
                view['output']['approved_regions']={k:approved[k] for k in ('accept','reject')} if approved else None
            except (Unavailable,OSError):
                view['output']['association_trusted']=False;view['output']['approved_regions']=None
        return view

    def process_calibration(self,row):
        self.enrolled_fields(row['input']['enrollment_id'],row['owner'])
        geometry=Geometry(**row['input']['geometry'])
        frames=self.store.frame_rows(row['id'])
        enrolled=[f for f in frames if f['role']=='enrollment']
        if len(enrolled)<2:raise ValueError('At least two distinct enrollment frames required; 30 suggested')
        estimator=Estimator(geometry)
        def decode(frame):
            raw=(self.store.frames/(frame['digest']+'.jpg')).read_bytes()
            if digest(raw).hex()!=frame['digest']:raise ValueError('Frame hash mismatch')
            return decode_green(raw,geometry,source_width=geometry.source_width,source_height=geometry.source_height)
        for f in enrolled:estimator.add(decode(f),asset_digest=bytes.fromhex(f['digest']))
        ref=estimator.finish();raw=reference_bytes(ref);rh=digest(raw).hex()
        publish_bytes(self.store.references/(rh+'.json'),raw)
        scores=[]
        for f in frames:
            if f['role']=='enrollment':continue
            result=score(decode(f),geometry,ref,asset_digest=bytes.fromhex(f['digest']))
            scores.append(dict(asset=f['digest'],role=f['role'],score=result.value,reason=result.reason))
        return 'COMPLETE',dict(reference_hash=rh,geometry=asdict(geometry),scores=scores,calibration_approved=False,
                               enrollment=[f['digest'] for f in enrolled],development=[f['digest'] for f in frames if f['role']=='development'])

    def process_evidence(self,row):
        data=row['input'];fields,record=self.enrolled_fields(data['enrollment_id'],row['owner'])
        fields=replace(fields,localTime=data['time'])
        asset=self.asset(data['media_sha256']);rh=data.get('reference_hash')
        if rh is not None and not HASH.fullmatch(rh):raise ValueError('Invalid reference hash')
        policy=json_bytes({'schema':'fingerprinto/policy-v3/1'})
        if rh:
            # A private reference must belong to an owner-scoped completed session.
            with self.store.connection() as db:
                sessions=db.execute("SELECT output FROM operations WHERE owner=? AND kind='calibration' AND state='COMPLETE'",(row['owner'],)).fetchall()
            if not any(json.loads(s[0]).get('reference_hash')==rh for s in sessions):raise ValueError('Reference is not owned by this identity')
            approved=next((r for r in record['references'] if r['hash']==rh),None)
            if approved:
                policy=json_bytes({'schema':'fingerprinto/policy-v3/1','prnu':dict(method='fingerprinto/prnu-haar-green/1',
                    geometry=approved['geometry'],accept=approved['accept'],reject=approved['reject'],calibrationHash=approved['calibrationHash'])})
        placement=0 if embedded_candidate(asset) is True else 2
        draft,policy,report=prepare_v3(fields,asset,placement=placement,credential=None,policy=policy,
            reference_hash=bytes.fromhex(rh) if rh else None,now=utc(),store=self.trust(),reference_loader=self.reference)
        template=draft.with_signature(PLACEHOLDER).to_bytes();root=draft.roots(asset,None)['R']
        now=int(time.time())
        body=json_bytes(dict(schema='photo/evidence-preparation/1',operation=row['id'],owner=row['owner'],
            media_sha256=data['media_sha256'],root=root.hex(),template_sha256=digest(template).hex(),
            policy_sha256=digest(policy).hex(),report_sha256=digest(report).hex(),issued=now,expires=now+86400))
        signature=self.authority().sign(domain('photo/evidence-preparation/1',digest(body)),ec.ECDSA(hashes.SHA256()))
        return 'READY',dict(envelope=dict(body=body.decode(),signature=signature.hex()),template=b64(template),
                            policy=b64(policy),report=b64(report),fields=fields_to_json(fields))

    def receive_receipt(self,identity,owner,raw):
        row=self.store.get(identity,owner)
        if row is None or row['kind']!='evidence' or row['state'] not in ('READY','COMPLETE'):raise Conflict('Evidence is not ready')
        output=row['output'];envelope=output['envelope'];body=json_object(envelope['body'].encode())
        if row['state']=='READY' and time.time()>body['expires']:raise Conflict('Preparation expired; create a new operation')
        signed=ReceiptV3.from_bytes(raw);template=ReceiptV3.from_bytes(un64(output['template']))
        asset=self.asset(row['input']['media_sha256']);policy=un64(output['policy']);report=un64(output['report'])
        signed.evidence.attachments(policy,report)
        roots=signed.verify_signature(asset,None)
        if roots['R'].hex()!=body['root'] or signed.with_signature(PLACEHOLDER).to_bytes()!=template.to_bytes():raise Conflict('Receipt differs from preparation')
        bundle=EvidenceBundle(raw,asset,profile='evidence-v3',policy=policy,report=report)
        assessment=verify_v3(bundle,now=utc(),store=self.trust(),reference_loader=self.reference)
        if assessment['decision']['state']!='complete':raise Conflict('Current trust does not authorize this receipt')
        commitment=roots['C'].hex();folder=self.store.receipts/identity;folder.mkdir(mode=0o700,exist_ok=True)
        # Immutable files can be recovered by retry if DB commit is interrupted.
        for name,value in bundle.files().items():publish_bytes(folder/name,value)
        with self.store.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            existing=db.execute('SELECT commitment FROM attachments WHERE operation=?',(identity,)).fetchone()
            if existing and existing[0]!=commitment:raise Conflict('Receipt signature changed')
            db.execute('INSERT OR IGNORE INTO attachments(commitment,operation,owner,media) VALUES(?,?,?,?)',
                       (commitment,identity,owner,row['input']['media_sha256']))
            db.execute("UPDATE operations SET state='COMPLETE' WHERE id=? AND owner=?",(identity,owner))
        return dict(commitment=commitment,assessment=assessment)

    def bundle(self,attachment):
        row=self.store.get(attachment['operation']);out=row['output'];folder=self.store.receipts/row['id']
        proof=folder/'commitment.bin.ots'
        return EvidenceBundle((folder/'receipt.cbor').read_bytes(),self.asset(attachment['media']),
            ots_proof=proof.read_bytes() if proof.is_file() else None,profile='evidence-v3',
            policy=un64(out['policy']),report=un64(out['report']))

    def snapshot(self,commitment,*,verify=False):
        att=self.store.attachment(commitment)
        if att is None:return None
        assessment=None
        public=json.loads(att['settlement']) if att['settlement'] else None
        if verify:
            try:
                assessment=verify_v3_ots(self.bundle(att),config=self.config.ots_config(),now=utc(),store=self.trust(),reference_loader=self.reference)
            except (Unavailable,OSError):
                assessment={'status':'UNVERIFIABLE','reason':'current_trust_unavailable'}
            folder=self.store.receipts/att['operation']
            if (folder/'commitment.bin.ots').is_file():
                from settlement import SettlementService
                public=SettlementService(self.config.ots_config()).verify_file(folder/'commitment.bin',folder/'commitment.bin.ots').to_dict()
                public.pop('proof_file',None)
                public['proof_url']=f'{self.config.public_url}/v1/posts/{att["event"]}/evidence/proof'
            else:public=None
        return dict(profile='evidence-v3',commitment=commitment,assessment=assessment,
            settlement=public,
            proof_url=f'{self.config.public_url}/v1/posts/{att["event"]}/evidence/proof' if att['event'] else None,
            bundle_url=f'{self.config.public_url}/v1/posts/{att["event"]}/evidence/bundle' if att['event'] else None)
