from dataclasses import replace, asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import tempfile
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from fingerprinto.core import Receipt, digest
from fingerprinto.evidence_v3 import Outcome
from fingerprinto.receipt_journal import ReceiptJournal
from fingerprinto.receipt_v3 import ReceiptV3
from fingerprinto.trust_v3 import TrustStore, sign_record
from fingerprinto.workflow_v3 import Finding, issue_v3, verify_v3


def spki(key):
    return key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


class WorkflowV3Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.key, self.authority = ec.generate_private_key(ec.SECP256R1()), ec.generate_private_key(ec.SECP256R1())
        v = json.loads((Path(__file__).parent/"fixtures/v1.json").read_text())
        self.fields = replace(Receipt.from_bytes(bytes.fromhex(v["cases"][0]["receipt_cbor"])), pk_I=spki(self.key))
        self.asset = b'\xff\xd8\xff\xd9'  # Only tests orchestration, not a physical photo.
        self.now = datetime(2026,11,21,tzinfo=timezone.utc)
        self.body = {"schema":"fingerprinto/enrollment-v3/1", "installation":digest(self.fields.pk_I).hex(),
                     "package":self.fields.packageName,"signer":self.fields.signerDigest.hex(),
                     "minimumVersion":0,"sequence":1,"notBefore":int(self.now.timestamp())-10,
                     "expires":int(self.now.timestamp())+600,"status":"active","attestationPolicy":"a"*64,
                     "references":[]}
        self.store = TrustStore(Path(self.tmp.name)/"trust.db",authority_spki=spki(self.authority))
        self.store.retain(sign_record(self.body,self.authority))
        self.journal = ReceiptJournal(Path(self.tmp.name)/"journal.db")
        self.calls=0

    def sign(self, root):
        self.calls+=1
        return self.key.sign(root,ec.ECDSA(hashes.SHA256()))

    def issue(self, **kw):
        args = dict(placement=2,credential=None,policy=b'{"trial":true}',reference_hash=None,
                    now=self.now,store=self.store,journal=self.journal,operation="a"*32,sign_root=self.sign)
        args.update(kw)
        return issue_v3(self.fields,self.asset,**args)

    def test_no_manufacturer_receipt_and_retry(self):
        bundle = self.issue()
        again = self.issue()
        self.assertEqual(bundle,again)
        self.assertEqual(self.calls,1)
        result = verify_v3(bundle,now=self.now,store=self.store)
        self.assertEqual(result["decision"]["level"],"none")
        self.assertTrue(result["decision"]["can_offer_choice"])
        self.assertEqual(verify_v3(bundle,now=self.now)["decision"]["state"],"pending")
        with self.assertRaises(ValueError):
            self.issue(policy=b'{"trial":false}')

    def test_signature_and_revocation_override_pass_claims(self):
        validator=lambda *args: Finding(Outcome.PASS,"synthetic_adapter",{})
        bundle=self.issue(placement=0,manufacturer_validator=validator)
        # Without the adapter, the signed pass alone never creates a pass.
        self.assertEqual(verify_v3(bundle,now=self.now,store=self.store)["decision"]["level"],"none")
        self.assertEqual(verify_v3(bundle,now=self.now,store=self.store,manufacturer_validator=validator)["decision"]["level"],"manufacturer")
        bad=replace(bundle,asset=self.asset+b"changed")
        self.assertEqual(verify_v3(bad,now=self.now,store=self.store,manufacturer_validator=validator)["decision"]["state"],"failed")
        self.store.retain(sign_record(dict(self.body,sequence=2,status="revoked"),self.authority))
        self.assertEqual(verify_v3(bundle,now=self.now,store=self.store,manufacturer_validator=validator)["decision"]["state"],"failed")
        with self.assertRaises(ValueError):
            self.issue(placement=0,manufacturer_validator=validator)

    def test_both_checks_before_refusing_signing(self):
        visited=[]
        def manufacturer(*args):
            visited.append("manufacturer")
            return Finding(Outcome.FAIL,"synthetic_invalid_signature",{})
        def loader(identity):
            visited.append("prnu")
            return None
        with self.assertRaises(ValueError):
            self.issue(placement=0,reference_hash=b"r"*32,manufacturer_validator=manufacturer,reference_loader=loader)
        self.assertEqual(visited,["manufacturer","prnu"])
        self.assertEqual(self.calls,0)
        with self.assertRaises(ValueError):
            self.issue(store=None)

    def test_authenticated_records_replay_and_foreign_authority(self):
        other=ec.generate_private_key(ec.SECP256R1())
        with self.assertRaises(InvalidSignature):
            self.store.retain(sign_record(dict(self.body,sequence=2),other))
        self.store.retain(sign_record(dict(self.body,sequence=2),self.authority))
        with self.assertRaises(ValueError):
            self.store.retain(sign_record(self.body,self.authority))
        record,reason=self.store.lookup(replace(self.fields,signerDigest=b"x"*32),now=self.now)
        self.assertIsNone(record)
        self.assertEqual(reason,"enrolled_release_mismatch")

    def test_jpeg_reference_requires_separate_calibration_approval(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Optional prnu extra required")
        from io import BytesIO
        import random
        from fingerprinto.prnu import Geometry,Estimator
        from fingerprinto.prnu_image import DECODER,decode_green
        from fingerprinto.prnu_records import reference_bytes
        from fingerprinto.prnu_records import json_bytes
        rng=random.Random(431)
        geometry=Geometry("synthetic-sensor","jpeg-trial",32,32,DECODER,32,32)
        pattern=[rng.gauss(0,2) for _ in range(1024)]
        def image():
            pixels=[max(0,min(255,round(120+v+rng.gauss(0,.2)))) for v in pattern]
            output=BytesIO()
            Image.frombytes("RGB",(32,32),bytes(c for v in pixels for c in (v,v,v))).save(output,format="JPEG",quality=100,subsampling=0)
            return output.getvalue()
        estimator=Estimator(geometry)
        for _ in range(8):
            asset=image()
            estimator.add(decode_green(asset,geometry,source_width=32,source_height=32),asset_digest=digest(asset))
        raw=reference_bytes(estimator.finish())
        self.asset=image()
        config={"method":"fingerprinto/prnu-haar-green/1","geometry":asdict(geometry),
                "accept":None,"reject":None,"calibrationHash":"c"*64}
        policy=json_bytes({"schema":"fingerprinto/policy-v3/1","prnu":config})
        association={"hash":digest(raw).hex(),"geometry":asdict(geometry),
                     "policyHash":digest(policy).hex(),"calibrationHash":"c"*64,
                     "accept":None,"reject":None}
        self.store.retain(sign_record(dict(self.body,sequence=2,references=[association]),self.authority))
        loader=lambda identity:raw
        bundle=self.issue(reference_hash=digest(raw),reference_loader=loader,policy=policy)
        result=verify_v3(bundle,now=self.now,store=self.store,reference_loader=loader)
        self.assertEqual(result["prnu"]["status"],"inconclusive")
        self.assertIsNotNone(result["prnu"]["details"]["score"])
        # Synthetic authority approval tests control flow, not measured accuracy.
        approved_policy=json_bytes({"schema":"fingerprinto/policy-v3/1","prnu":dict(config,accept=0)})
        approved=dict(association,accept=0,policyHash=digest(approved_policy).hex())
        self.store.retain(sign_record(dict(self.body,sequence=3,references=[approved]),self.authority))
        result=verify_v3(bundle,now=self.now,store=self.store,reference_loader=loader)
        self.assertEqual(result["prnu"]["status"],"inconclusive")
        self.assertEqual(result["prnu"]["reason"],"supplied_policy_not_approved_for_reference")
        bundle=self.issue(reference_hash=digest(raw),reference_loader=loader,policy=approved_policy,operation="b"*32)
        result=verify_v3(bundle,now=self.now,store=self.store,reference_loader=loader)
        self.assertEqual(result["prnu"]["status"],"pass")
        self.assertEqual(result["signedClaims"]["prnu"],"pass")
        self.assertEqual(result["decision"]["level"],"prnu")
        # A signed pass still cannot supply an unavailable current reference.
        self.assertEqual(verify_v3(bundle,now=self.now,store=self.store)["prnu"]["status"],"unavailable")
