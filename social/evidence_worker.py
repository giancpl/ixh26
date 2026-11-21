"""Leased evidence work; authority key is mounted only in this process."""
import fcntl
import json
import os
import threading
from .evidence import EvidenceService, Unavailable
from .storage import canonical


class EvidenceWorker:
    def __init__(self,config,service):
        self.config=config;self.service=service;self.evidence=EvidenceService(config)

    def tick(self):
        claim=self.evidence.store.claim()
        if claim is None:return False
        identity,lease=claim;store=self.evidence.store
        fd=os.open(store.root/(identity+'.lock'),os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
        try:
            try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:
                store.finish(identity,lease,state='QUEUED',retry=True);return True
            stopped=threading.Event()
            def heartbeat():
                while not stopped.wait(self.config.lease_seconds/3):
                    try:
                        if not store.renew(identity,lease):return
                    except Exception:return
            thread=threading.Thread(target=heartbeat,daemon=True);thread.start()
            try:
                row=store.get(identity)
                if row['kind']=='anchor':state,output=self.anchor(row,lease)
                else:
                    process=getattr(self.evidence,'process_'+row['kind'])
                    state,output=process(row)
                store.finish(identity,lease,state=state,output=output)
            except (Unavailable,OSError):
                store.finish(identity,lease,state='UNAVAILABLE',error='operation_unavailable')
            except (ValueError,KeyError,TypeError):
                store.finish(identity,lease,state='ERROR',error='invalid_evidence')
            except Exception:
                store.finish(identity,lease,state='UNAVAILABLE',error='operation_unavailable')
            finally:
                stopped.set();thread.join(timeout=1)
        finally:os.close(fd)
        return True

    def anchor(self,row,lease):
        store=self.evidence.store;att=store.attachment(row['input']['commitment'])
        if att is None or not att['event']:raise ValueError('Unpublished receipt')
        folder=store.receipts/att['operation'];payload=folder/'commitment.bin';proof=folder/'commitment.bin.ots'
        if not proof.is_file():result=self.service.stamp_file(payload,folder)
        else:
            upgraded=self.service.upgrade_proof(proof)
            result=self.service.verify_file(payload,proof)
            result.diagnostics.extend(d for d in upgraded.diagnostics if d.code!='not_verified')
        public=result.to_dict();public.pop('proof_file',None)
        public['proof_url']=f'{self.config.public_url}/v1/posts/{att["event"]}/evidence/proof'
        with store.connection() as db:
            db.execute('UPDATE attachments SET settlement=? WHERE commitment=? AND EXISTS(SELECT 1 FROM operations WHERE id=? AND lease=?)',
                       (canonical(public),att['commitment'],row['id'],lease))
        return ('COMPLETE' if public['status'] in ('CONFIRMED','INVALID') else 'UNAVAILABLE'),public
