"""Regenerate public synthetic application vectors; never use these identities in an app."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tests'))
import base64,json,tempfile,time
from nostr_sdk import Keys,EventBuilder,Kind,Tag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from fingerprinto.core import digest
from fingerprinto.receipt_v3 import ReceiptV3
from social.evidence import EvidenceService,b64,un64
from social.evidence_worker import EvidenceWorker
from social.storage import publish_bytes
from settlement import SettlementService
import v3_support


def generate(destination):
    with tempfile.TemporaryDirectory() as tmp:
        t,cfg=v3_support.setup(tmp)
        try:
            svc=EvidenceService(cfg);owner=Keys.parse('0'*63+'1');pub=owner.public_key().to_hex()
            challenge=svc.challenge('1'*32,pub)
            enroll=v3_support.enrollment(t,challenge['challenge'],'2'*32)
            svc.store.enqueue('2'*32,pub,'enrollment',enroll)
            worker=EvidenceWorker(cfg,SettlementService(cfg.ots_config()));assert worker.tick()
            assert svc.store.get('2'*32)['state']=='ACCEPTED'
            photo=(Path(__file__).resolve().parents[1]/'contracts/fixtures/photo.jpg').read_bytes();sha=digest(photo).hex()
            publish_bytes(cfg.data_dir/'media'/(sha+'.jpg'),photo)
            data=dict(operation_id='3'*32,enrollment_id='2'*32,media_sha256=sha,time=int(time.time()*1000))
            svc.store.enqueue('3'*32,pub,'evidence',data);assert worker.tick()
            output=svc.store.get('3'*32)['output'];draft=ReceiptV3.from_bytes(un64(output['template']))
            root=draft.roots(photo)['R'];sig=t.leafkey.sign(root,ec.ECDSA(hashes.SHA256()));receipt=draft.with_signature(sig)
            accepted=svc.receive_receipt('3'*32,pub,receipt.to_bytes());commitment=accepted['commitment']
            tags=[['title','Photo'],['imeta','url http://127.0.0.1:8080/v1/media/'+sha,'m image/jpeg','x '+sha,'dim 64x48'],['m','image/jpeg'],['x',sha],['photo-evidence','fingerprinto/evidence-v3',commitment]]
            event=json.loads(EventBuilder(Kind(20),'').tags([Tag.parse(v) for v in tags]).finalize(owner).as_json())
            auth=v3_support.auth(owner,'POST','/v1/evidence',json.dumps(data).encode())['Authorization'][6:]
            vector=dict(schema='photo/app-v3-vectors/1',synthetic=True,photo=b64(photo),photo_sha256=sha,
                owner=pub,operation='3'*32,template=output['template'],policy=output['policy'],report=output['report'],
                envelope=output['envelope'],fields=output['fields'],authority_spki=svc.settings()['authority_spki'],
                enrollment_record=svc.store.get('2'*32)['output']['record'],receipt=b64(receipt.to_bytes()),root=root.hex(),commitment=commitment,
                event=event,http_auth_event=json.loads(base64.b64decode(auth)),expected_origin='none')
            Path(destination).write_text(json.dumps(vector,ensure_ascii=False,indent=2)+'\n')
        finally:t.doCleanups()

if __name__=='__main__':generate(sys.argv[1])
