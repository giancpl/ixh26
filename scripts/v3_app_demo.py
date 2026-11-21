"""Real application/relay/calendar/Core lifecycle with isolated SYNTHETIC identities.

This starts an isolated loopback HTTP API inside the tools container. Its test
PKI and authority are never installed into the normal API/worker or Android.
"""
import base64
from dataclasses import replace
from datetime import datetime,timezone
import hashlib
from io import BytesIO
import json
from pathlib import Path
import random
import sys
import threading
import time
import uuid

import httpx
import uvicorn
from PIL import Image
from nostr_sdk import Keys,EventBuilder,Kind,Tag
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tests'))
import v3_support
from fingerprinto.core import domain,digest
from fingerprinto.receipt_v3 import ReceiptV3
from fingerprinto.enrollment_requests import instant
from fingerprinto.bundle import import_bundle
from social.config import Config
from social.api import create_app
from social.worker import Worker
from social.evidence import un64
from upp_protocol.verify_bundle import verify_bundle
from settlement.ots_backend import OTSBackend


def run():
    run_id='v3-app-'+uuid.uuid4().hex;directory=Path('/artifacts')/run_id;directory.mkdir(parents=True)
    t,synthetic=v3_support.setup(directory/'SYNTHETIC-ONLY')
    base=Config.from_env();url='http://127.0.0.1:18081'
    config=replace(synthetic,public_url=url,relay_url=base.relay_url,bitcoin_conf=base.bitcoin_conf,timeout=base.timeout,retry_seconds=1,lease_seconds=30)
    app=create_app(config);server=uvicorn.Server(uvicorn.Config(app,host='127.0.0.1',port=18081,log_level='warning',access_log=False))
    stopped=threading.Event();worker=Worker(config)
    def work():
        while not stopped.is_set():
            try:worker.tick()
            except Exception:pass
            stopped.wait(.1)
    http_thread=threading.Thread(target=server.run,daemon=True);http_thread.start()
    worker_thread=threading.Thread(target=work,daemon=True);worker_thread.start()
    keys=Keys.generate();client=httpx.Client(base_url=url,timeout=30)
    def request(method,path,data=None,raw=None,private=True):
        body=raw if raw is not None else json.dumps(data or {},separators=(',',':')).encode() if method!='GET' else b''
        headers=v3_support.auth(keys,method,path,body,base=url) if private else {}
        if path=='/v1/media':headers['content-type']='image/jpeg'
        response=client.request(method,path,content=body,headers=headers)
        if response.status_code not in (200,201,202):raise RuntimeError(f'{method} {path}: {response.status_code}: {response.text[:300]}')
        return response
    def poll(path,state,deadline=None):
        deadline=deadline or time.monotonic()+30
        while time.monotonic()<deadline:
            value=request('GET',path).json()
            if value['state']==state:return value
            if value['state'] in ('REJECTED','EXPIRED','ERROR'):raise RuntimeError(json.dumps(value))
            time.sleep(.2)
        raise TimeoutError(path)
    try:
        deadline=time.monotonic()+20
        while not server.started:
            if time.monotonic()>deadline:raise TimeoutError('HTTP API startup')
            time.sleep(.1)
        challenge=request('POST','/v1/enrollments/challenge',{'operation_id':uuid.uuid4().hex}).json()
        enrollment=v3_support.enrollment(t,challenge['challenge'])
        request('POST','/v1/enrollments',enrollment);eid=enrollment['operation_id'];poll('/v1/enrollments/'+eid,'ACCEPTED')
        rng=random.Random(63131);pattern=[rng.gauss(0,2) for _ in range(1024)]
        def jpeg(other=False):
            output=BytesIO();values=[max(0,min(255,round(120+(rng.gauss(0,2) if other else p)+rng.gauss(0,.6)))) for p in pattern]
            Image.frombytes('RGB',(32,32),bytes(v for p in values for v in (p,p,p))).save(output,format='JPEG',quality=100,subsampling=0)
            return output.getvalue()
        cid=uuid.uuid4().hex
        geometry=dict(camera='SYNTHETIC',mode='SYNTHETIC-fixed',width=32,height=32,decoder='pillow-13.0.0/rgb-green-stored/1',source_width=32,source_height=32)
        request('POST','/v1/calibrations',dict(operation_id=cid,enrollment_id=eid,geometry=geometry))
        for role in ['enrollment']*4+['development','genuine','impostor']:
            raw=jpeg(role=='impostor');sha=digest(raw).hex()
            request('PUT',f'/v1/calibrations/{cid}/frames/{role}/{sha}',raw=raw)
        request('POST',f'/v1/calibrations/{cid}/evaluate',{})
        calibration=poll('/v1/calibrations/'+cid,'COMPLETE')
        assert calibration['output']['calibration_approved'] is False
        photo=jpeg();(directory/'photo.jpg').write_bytes(photo)
        media=request('POST','/v1/media',raw=photo,private=False).json();assert media['sha256']==digest(photo).hex()
        operation=uuid.uuid4().hex
        request('POST','/v1/evidence',dict(operation_id=operation,enrollment_id=eid,media_sha256=media['sha256'],time=int(time.time()*1000),reference_hash=calibration['output']['reference_hash']))
        proposal=poll('/v1/evidence/'+operation,'READY')['output']
        envelope=proposal['envelope'];body=envelope['body'].encode()
        authority=serialization.load_der_public_key(un64(app.state.evidence.settings()['authority_spki']))
        authority.verify(bytes.fromhex(envelope['signature']),domain('photo/evidence-preparation/1',digest(body)),ec.ECDSA(hashes.SHA256()))
        claim=json.loads(body);assert claim['media_sha256']==digest(photo).hex() and claim['owner']==keys.public_key().to_hex()
        for name in ('template','policy','report'):assert digest(un64(proposal[name])).hex()==claim[name+'_sha256']
        template=ReceiptV3.from_bytes(un64(proposal['template']));root=template.roots(photo)['R'];assert root.hex()==claim['root']
        signed=template.with_signature(t.leafkey.sign(root,ec.ECDSA(hashes.SHA256()))).to_bytes()
        receipt=request('POST',f'/v1/evidence/{operation}/receipt',raw=signed).json();commitment=receipt['commitment']
        tags=[['title','PhoTO v3 local demo'],['imeta','url '+media['url'],'m image/jpeg','x '+media['sha256'],'dim 32x32'],['m','image/jpeg'],['x',media['sha256']],['photo-evidence','fingerprinto/evidence-v3',commitment]]
        event=json.loads(EventBuilder(Kind(20),'PhoTO v3 local demo').tags([Tag.parse(v) for v in tags]).finalize(keys).as_json());event_id=event['id']
        request('POST','/v1/posts',{'event':event},private=False)
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            snapshot=request('GET','/v1/posts/'+event_id,private=False).json()
            if snapshot['publication_status']=='PUBLISHED' and snapshot['settlement'] and snapshot['evidence']['settlement']:
                if snapshot['settlement']['status']=='PENDING' and snapshot['evidence']['settlement']['status']=='PENDING':break
            time.sleep(.2)
        else:raise TimeoutError('Both pending proofs and relay publication')
        (directory/'pending.json').write_text(json.dumps(snapshot,indent=2))
        backend=OTSBackend(config.ots_config());node=backend._bitcoin_proxy(wallet='ots');mined=[]
        try:
            while time.monotonic()<deadline:
                result=request('POST',f'/v1/posts/{event_id}/verify',{},private=False).json()
                v3=request('POST',f'/v1/posts/{event_id}/evidence/verify',{},private=False).json()
                if result['settlement']['status']=='CONFIRMED' and v3['settlement'] and v3['settlement']['status']=='CONFIRMED':break
                if node._call('getrawmempool'):
                    mined.extend(node._call('generatetoaddress',2,node._call('getnewaddress')))
                time.sleep(.5)
            else:raise TimeoutError('Both independent proofs must confirm within 120s')
        finally:node.close()
        assert v3['assessment']['decision']['level']=='none' and v3['assessment']['anchor']=='verified'
        assert result['settlement']['network']==v3['settlement']['network']=='regtest'
        assert result['settlement']['payload_sha256']!=v3['settlement']['payload_sha256']
        for report in (result['settlement'],v3['settlement']):
            assert report['binding_checked'] and isinstance(report['bitcoin']['block_height'],int) and isinstance(report['bitcoin']['block_time'],int)
        social_bundle=directory/'nostr.zip';evidence_bundle=directory/'evidence-v3.zip'
        social_bundle.write_bytes(request('GET',f'/v1/posts/{event_id}/bundle',private=False).content)
        evidence_bundle.write_bytes(request('GET',f'/v1/posts/{event_id}/evidence/bundle',private=False).content)
        store=app.state.evidence.trust();authority_path=directory/'authority.der';authority_path.write_bytes(store.authority)
        verification=verify_bundle(social_bundle,config.ots_config(),evidence_bundle=evidence_bundle,trust_db=store.path,authority_spki=authority_path,reference_dir=app.state.evidence.store.references)
        assert verification['evidence']['verification']['anchor']=='verified'
        # Independent CLI subprocess reads proofs/trust, not HTTP receipts.
        import subprocess
        args=[sys.executable,'-m','upp_protocol.verify_bundle',str(social_bundle),'--evidence-bundle',str(evidence_bundle),
              '--network','regtest','--bitcoin-conf',config.bitcoin_conf,'--trust-db',store.path,'--authority-spki',str(authority_path),'--reference-dir',str(app.state.evidence.store.references)]
        cli=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
        assert cli.returncode==0,cli.stdout.decode()+cli.stderr.decode()
        (directory/'cli.json').write_bytes(cli.stdout)
        import zipfile
        altered=directory/'altered.zip'
        with zipfile.ZipFile(social_bundle) as source,zipfile.ZipFile(altered,'w') as target:
            for name in source.namelist():target.writestr(name,source.read(name)+(b'changed' if name=='photo.jpg' else b''))
        changed=subprocess.run([*args[:3],str(altered),*args[4:]],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
        assert changed.returncode==3,changed.stdout.decode()
        (directory/'altered-verification.json').write_bytes(changed.stdout)
        # Restart the app and worker over the same persistent records.
        worker=Worker(config);fresh=create_app(config)
        assert fresh.state.store.get(event_id) and fresh.state.evidence.snapshot(commitment,verify=True)['settlement']['status']=='CONFIRMED'
        summary=dict(synthetic_identity=True,hardware_origin_verified=False,origin='none',event_id=event_id,commitment=commitment,
            publication='PUBLISHED',network='regtest',nostr=result['settlement'],evidence=v3['settlement'],mined_blocks=mined,
            calibration_approved=False,both_cli_verified=True,modified_photo='INVALID',restart_persistence=True,artifacts=str(directory))
        (directory/'confirmed.json').write_text(json.dumps(result,indent=2));(directory/'v3-verification.json').write_text(json.dumps(v3,indent=2));(directory/'calibration.json').write_text(json.dumps(calibration,indent=2))
        (directory/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary));return summary
    finally:
        stopped.set();server.should_exit=True;worker_thread.join(timeout=5);http_thread.join(timeout=5);client.close();t.doCleanups()

if __name__=='__main__':run()
