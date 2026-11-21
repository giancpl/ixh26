"""Synthetic attestation certificates: no claim of genuine Android hardware."""

from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import datetime, timedelta, timezone
import hashlib
from pathlib import Path
import tempfile
import unittest

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID, ObjectIdentifier

from fingerprinto import android_attestation as der
from fingerprinto.enrollment import ATTESTATION, EnrollmentPolicy, RevocationSnapshot, verify_enrollment
from fingerprinto.enrollment_requests import EnrollmentRequests
from fingerprinto.core import Receipt, commitments
from fingerprinto.enrollment import enroll_receipt


def tlv(tag, body):
    size = len(body)
    length = bytes([size]) if size < 128 else bytes([0x80 + (size.bit_length()+7)//8]) + size.to_bytes((size.bit_length()+7)//8, "big")
    return tag + length + body


def integer(n, tag=b"\x02"):
    value = n.to_bytes(max(1, (n.bit_length()+7)//8), "big")
    if value[0] & 128:
        value = b"\x00" + value
    return tlv(tag, value)


def seq(*fields):
    return tlv(b"\x30", b"".join(fields))


def octets(value):
    return tlv(b"\x04", value)


def aset(*values):
    return tlv(b"\x31", b"".join(sorted(values)))


def explicit(tag, value):
    groups = [tag & 127]
    tag >>= 7
    while tag:
        groups.insert(0, 128 | (tag & 127))
        tag >>= 7
    encoded = b"\xbf" + bytes(groups) if len(groups) > 1 or groups[0] >= 31 else bytes([0xa0 + groups[0]])
    return tlv(encoded, value)


class EnrollmentTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.now = datetime(2026, 11, 20, tzinfo=timezone.utc)
        self.rootkey = ec.generate_private_key(ec.SECP256R1())
        self.leafkey = ec.generate_private_key(ec.SECP256R1())
        self.rootname = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Synthetic test root")])
        self.root = self.certificate(self.rootkey, self.rootkey, self.rootname, self.rootname, ca=True, serial=1)
        self.signer = hashlib.sha256(b"test APK signer").digest()
        self.policy = EnrollmentPolicy("test-only", (self.root.public_bytes(serialization.Encoding.DER),),
                                       (("example.photo", 1),), frozenset({self.signer}))
        self.requests = EnrollmentRequests(Path(self.tmp.name) / "verifier.db")
        self.challenge = self.requests.issue(self.policy.digest, now=self.now)
        self.revocations = RevocationSnapshot(frozenset(), self.now - timedelta(seconds=1),
                                               self.now + timedelta(minutes=5), bytes(32))
        self.public = self.leafkey.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    def certificate(self, key, issuerkey, subject, issuer, *, ca=False, serial=2, extension=None, critical=False):
        builder = (x509.CertificateBuilder().subject_name(subject).issuer_name(issuer)
                   .public_key(key.public_key()).serial_number(serial)
                   .not_valid_before(self.now - timedelta(days=1)).not_valid_after(self.now + timedelta(days=1))
                   .add_extension(x509.BasicConstraints(ca=ca, path_length=2 if ca else None), critical=True)
                   .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
                       data_encipherment=False, key_agreement=False, key_cert_sign=ca, crl_sign=ca,
                       encipher_only=None, decipher_only=None), critical=True))
        if extension is not None:
            builder = builder.add_extension(x509.UnrecognizedExtension(ATTESTATION, extension), critical=critical)
        return builder.sign(issuerkey, hashes.SHA256())

    def attestation(self, *, challenge=None, level=1, keylevel=1, version=3, keyversion=4,
                    locked=True, bootstate=0, package="example.photo", signer=None, hw_change=None, sw_extra=None):
        app = seq(aset(seq(octets(package.encode()), integer(1))), aset(octets(signer or self.signer)))
        sw = {709: octets(app)}
        if sw_extra:
            sw.update(sw_extra)
        hw = {1: aset(integer(2)), 2: integer(3), 3: integer(256), 5: aset(integer(4)),
              10: integer(1), 702: integer(0),
              704: seq(octets(b"bootkey"), tlv(b"\x01", b"\xff" if locked else b"\x00"),
                       integer(bootstate, b"\x0a"), octets(b"boothash")),
              705: integer(130000), 706: integer(202611)}
        if hw_change:
            for tag, value in hw_change.items():
                if value is None:
                    hw.pop(tag, None)
                else:
                    hw[tag] = value
        return seq(integer(version), integer(level, b"\x0a"), integer(keyversion), integer(keylevel, b"\x0a"),
                   octets(self.challenge if challenge is None else challenge), octets(b""),
                   seq(*(explicit(t, v) for t, v in sorted(sw.items()))),
                   seq(*(explicit(t, v) for t, v in sorted(hw.items()))))

    def chain(self, extension=None, issuerkey=None, critical=False):
        leaf = self.certificate(self.leafkey, issuerkey or self.rootkey,
                               x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Synthetic installation")]),
                               self.rootname, extension=extension if extension is not None else self.attestation(), critical=critical)
        return (leaf.public_bytes(serialization.Encoding.DER), self.root.public_bytes(serialization.Encoding.DER))

    def verify(self, chain=None, **kwargs):
        options = dict(policy=self.policy, requests=self.requests, revocations=self.revocations, now=self.now)
        options.update(kwargs)
        return verify_enrollment(self.public, self.chain() if chain is None else chain, **options)

    def test_accept_and_reject_replay(self):
        chain = self.chain()
        result = self.verify(chain)
        self.assertEqual(result.enrollment.status, "accepted", result.to_dict())
        self.assertEqual(result.details["installation_id"], hashlib.sha256(self.public).hexdigest())
        self.assertEqual(result.details["attested_packages"], {"example.photo": 1})
        self.assertEqual(self.verify(chain).enrollment.code, "enrollment_challenge_unavailable")

    def test_missing_or_stale_evidence_never_consumes(self):
        for options, code in (({"policy": None}, "enrollment_policy_unconfigured"),
                              ({"requests": None}, "challenge_registry_missing"),
                              ({"revocations": None}, "revocation_evidence_missing"),
                              ({"revocations": replace(self.revocations, valid_until=self.now)}, "revocation_evidence_stale")):
            with self.subTest(code=code):
                result = self.verify(**options)
                self.assertEqual(result.enrollment.status, "indeterminate")
                self.assertEqual(result.enrollment.code, code)
        self.assertEqual(self.verify().enrollment.status, "accepted")

    def test_security_and_app_policy_rejections(self):
        variants = [dict(level=0), dict(keylevel=0), dict(locked=False), dict(bootstate=1),
                    dict(package="attacker.app"), dict(signer=bytes(32)), dict(challenge=bytes(32)),
                    dict(hw_change={2: integer(1)}), dict(hw_change={3: integer(384)}),
                    dict(hw_change={1: aset(integer(2), integer(3))}), dict(hw_change={5: aset(integer(0))}),
                    dict(hw_change={702: integer(2)}), dict(hw_change={10: None}),
                    dict(sw_extra={2: integer(3)})]
        for change in variants:
            with self.subTest(change=change):
                self.assertEqual(self.verify(self.chain(self.attestation(**change))).enrollment.status, "rejected")
        self.assertEqual(self.verify().enrollment.status, "accepted")

    def test_unknown_versions_critical_extensions_and_os_policy(self):
        self.assertEqual(self.verify(self.chain(self.attestation(version=999))).enrollment.status, "indeterminate")
        self.assertEqual(self.verify(self.chain(critical=True)).enrollment.status, "indeterminate")
        strict = replace(self.policy, minimum_os_patch=202612)
        self.assertEqual(self.verify(policy=strict).enrollment.code, "os_policy_rejected")
        strict = replace(self.policy, security_levels=frozenset({2}))
        self.assertEqual(self.verify(policy=strict).enrollment.status, "rejected")

    def test_path_signatures_roots_time_and_revocation(self):
        bogus = ec.generate_private_key(ec.SECP256R1())
        self.assertEqual(self.verify(self.chain(issuerkey=bogus)).enrollment.status, "rejected")
        self.assertEqual(self.verify(now=self.now + timedelta(days=2)).enrollment.status, "rejected")
        self.assertEqual(self.verify(revocations=replace(self.revocations, blocked_serials=frozenset({2}))).enrollment.code,
                         "attestation_certificate_revoked")
        root2 = self.certificate(bogus, bogus, self.rootname, self.rootname, ca=True, serial=3)
        policy = replace(self.policy, roots=(root2.public_bytes(serialization.Encoding.DER),))
        self.assertEqual(self.verify(policy=policy).enrollment.code, "attestation_root_untrusted")
        self.assertEqual(self.verify().enrollment.status, "accepted")

    def test_appended_attacker_certificate_is_rejected(self):
        # An attested key can sign a new certificate for an attacker's key.
        # Only the extension nearest the trust root is authoritative.
        attested_ca = self.certificate(self.leafkey, self.rootkey, self.rootname, self.rootname,
                                        ca=True, serial=5, extension=self.attestation())
        attackkey = ec.generate_private_key(ec.SECP256R1())
        attack = self.certificate(attackkey, self.leafkey, self.rootname, self.rootname, serial=6,
                                  extension=self.attestation())
        public = attackkey.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        chain = tuple(c.public_bytes(serialization.Encoding.DER) for c in (attack, attested_ca, self.root))
        # This restricted path refuses self-issued intermediates too. Use distinct
        # names so the authoritative-extension/key check itself is exercised.
        issuername = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Attested CA")])
        attested_ca = self.certificate(self.leafkey, self.rootkey, issuername, self.rootname,
                                        ca=True, serial=5, extension=self.attestation())
        attack = self.certificate(attackkey, self.leafkey, self.rootname, issuername, serial=6, extension=self.attestation())
        chain = tuple(c.public_bytes(serialization.Encoding.DER) for c in (attack, attested_ca, self.root))
        result = verify_enrollment(public, chain, policy=self.policy, requests=self.requests,
                                   revocations=self.revocations, now=self.now)
        self.assertEqual(result.enrollment.code, "attested_key_mismatch")

    def test_challenge_policy_scope_expiry_and_concurrency(self):
        changed = replace(self.policy, identifier="another-policy")
        self.assertEqual(self.verify(policy=changed).enrollment.code, "enrollment_challenge_unavailable")
        self.assertEqual(self.verify(now=self.now + timedelta(seconds=300)).enrollment.code, "revocation_evidence_stale")
        later_status = replace(self.revocations, valid_until=self.now + timedelta(hours=1))
        self.assertEqual(self.verify(now=self.now + timedelta(seconds=300), revocations=later_status).enrollment.code,
                         "enrollment_challenge_unavailable")
        chain = self.chain()
        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes = list(pool.map(lambda _: self.verify(chain).enrollment.status, range(2)))
        self.assertEqual(sorted(outcomes), ["accepted", "rejected"])

    def test_der_malformed_and_duplicate_tags(self):
        for raw in (b"", self.attestation() + b"trailing", b"\x30\x80\x00\x00", seq(integer(3)),
                    seq(integer(3), integer(1, b"\x0a"), integer(4), integer(1, b"\x0a"),
                        octets(self.challenge), octets(b""), seq(), seq(explicit(2, integer(3)), explicit(2, integer(3))))):
            with self.subTest(raw=raw[:16]):
                self.assertEqual(self.verify(self.chain(raw)).enrollment.status, "rejected")
        with self.assertRaises(ValueError):
            der.number(der.items(b"\x02\x02\x00\x01")[0])
        with self.assertRaises(ValueError):
            der.items(b"\xbf\x80\x01\x00")

    def test_ec_issuer_and_strongbox_profile(self):
        self.rootkey = ec.generate_private_key(ec.SECP384R1())
        self.root = self.certificate(self.rootkey, self.rootkey, self.rootname, self.rootname, ca=True, serial=8)
        self.policy = replace(self.policy, roots=(self.root.public_bytes(serialization.Encoding.DER),), security_levels=frozenset({2}))
        self.challenge = self.requests.issue(self.policy.digest, now=self.now)
        chain = self.chain(self.attestation(level=2, keylevel=2))
        self.assertEqual(self.verify(chain).enrollment.status, "accepted")

    def test_receipt_possession_checked_before_challenge_consumption(self):
        asset = b"synthetic asset, not an authorized camera capture"
        receipt = Receipt(self.public, self.chain(), "example.photo", 1, self.signer,
                          "Fixture", "Test", 0, None, 0, b"\x30\x06\x02\x01\x01\x02\x01\x01")
        options = dict(policy=self.policy, requests=self.requests, revocations=self.revocations, now=self.now)
        result = enroll_receipt(receipt.to_bytes(), asset, **options)
        self.assertEqual(result.enrollment.code, "enrollment_possession_unproven")
        signature = self.leafkey.sign(commitments(receipt, asset)["R"], ec.ECDSA(hashes.SHA256()))
        receipt = replace(receipt, sigma=signature)
        result = enroll_receipt(receipt.to_bytes(), asset, **options)
        self.assertEqual(result.installation_signature.status, "valid")
        self.assertEqual(result.enrollment.status, "accepted")
        self.assertEqual(result.origin.status, "indeterminate")
        self.assertEqual(result.enrollment_details["policy_sha256"], self.policy.digest.hex())

    def test_receipt_app_declarations_cannot_bypass_policy(self):
        asset = b"asset"
        receipt = Receipt(self.public, self.chain(), "wrong.app", 1, self.signer, "Fixture", "Test", 0, None, 0, bytes(8))
        receipt = replace(receipt, sigma=self.leafkey.sign(commitments(receipt, asset)["R"], ec.ECDSA(hashes.SHA256())))
        result = enroll_receipt(receipt.to_bytes(), asset, policy=self.policy, requests=self.requests,
                                revocations=self.revocations, now=self.now)
        self.assertEqual(result.enrollment.code, "receipt_app_policy_rejected")
        self.assertEqual(self.verify().enrollment.status, "accepted")

    def test_profile_limits_and_bad_key(self):
        result = verify_enrollment(bytes(91), self.chain(), policy=self.policy, requests=self.requests,
                                   revocations=self.revocations, now=self.now)
        self.assertEqual(result.enrollment.status, "rejected")
        with self.assertRaises(ValueError):
            EnrollmentRequests(":memory:")
        with self.assertRaises(ValueError):
            self.requests.issue(self.policy.digest, now=self.now, lifetime_seconds=True)
        with self.assertRaises(ValueError):
            self.verify(now=self.now.replace(tzinfo=None))


if __name__ == "__main__":
    unittest.main()
