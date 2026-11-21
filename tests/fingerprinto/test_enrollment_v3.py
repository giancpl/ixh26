from dataclasses import replace
from pathlib import Path
import unittest

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec

import test_enrollment as fixtures
from fingerprinto.core import Receipt
from fingerprinto.enrollment_v3 import intake,possession_root,retain_accepted
from fingerprinto.trust_v3 import TrustStore


class EnrollmentV3Tests(unittest.TestCase):
    def setUp(self):
        self.fixture=fixtures.EnrollmentTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)

    def test_possession_then_intake_then_retained_decision(self):
        t=self.fixture
        fields=Receipt(t.public,t.chain(),"example.photo",1,t.signer,"synthetic","synthetic",0,t.challenge,0,b"x"*8)
        signature=t.leafkey.sign(possession_root(fields),ec.ECDSA(hashes.SHA256()))
        args=dict(policy=t.policy,requests=t.requests,revocations=t.revocations,now=t.now)
        with self.assertRaises(InvalidSignature):
            intake(replace(fields,sessionChallenge=b"x"*32),signature,**args)
        invented=replace(fields,versionCode=99)
        invented_signature=t.leafkey.sign(possession_root(invented),ec.ECDSA(hashes.SHA256()))
        with self.assertRaises(ValueError):
            intake(invented,invented_signature,**args)
        # Failure above did not consume the original challenge.
        accepted=intake(fields,signature,**args)
        self.assertEqual(accepted.enrollment.status,"accepted")
        authority=ec.generate_private_key(ec.SECP256R1())
        public=authority.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        store=TrustStore(Path(t.tmp.name)/"accepted.db",authority_spki=public)
        retain_accepted(fields,accepted,authority_private_key=authority,store=store,sequence=1,
                        not_before=int(t.now.timestamp())-1,expires=int(t.now.timestamp())+300)
        self.assertEqual(store.lookup(fields,now=t.now)[1],"enrollment_record_valid")
        self.assertEqual(intake(fields,signature,**args).enrollment.code,"enrollment_challenge_unavailable")
