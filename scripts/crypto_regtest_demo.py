"""Synthetic v3 receipt with real local calendar/Core. No hardware-origin claim."""
from dataclasses import replace
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from PIL import Image

from fingerprinto.anchoring_v3 import stamp_v3, verify_v3_ots
from fingerprinto.bundle import export_bundle
from fingerprinto.core import Receipt, digest
from fingerprinto.receipt_journal import ReceiptJournal
from fingerprinto.trust_v3 import TrustStore, sign_record
from fingerprinto.workflow_v3 import issue_v3
from settlement import OTSConfig, SettlementService, Status
from settlement.ots_backend import OTSBackend


def run_demo():
    directory = Path('/artifacts') / ('crypto-' + uuid.uuid4().hex)
    directory.mkdir(parents=True)
    deadline = time.monotonic() + 120
    now = datetime.now(timezone.utc)
    options = OTSConfig(network='regtest', bitcoin_conf='/config/client.conf',
                        calendars=('http://calendar:14788',), timeout=3)
    service = SettlementService(options)
    key, authority = ec.generate_private_key(ec.SECP256R1()), ec.generate_private_key(ec.SECP256R1())
    def public(k):
        return k.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    (directory / 'authority.der').write_bytes(public(authority))
    # This administration fixture is intentionally not Android enrollment evidence.
    fields = Receipt(public(key), (b'synthetic chain, not hardware attestation',), 'example.crypto.demo',
                     1, digest(b'synthetic APK'), 'Synthetic', 'Fixture', 0, None, 0, b'x' * 8)
    store = TrustStore(directory / 'trust.db', authority_spki=public(authority))
    record = dict(schema='fingerprinto/enrollment-v3/1', installation=digest(fields.pk_I).hex(),
                  package=fields.packageName, signer=fields.signerDigest.hex(), minimumVersion=1,
                  sequence=1, notBefore=int(now.timestamp())-1, expires=int(now.timestamp())+3600,
                  status='active', attestationPolicy=digest(b'synthetic demo policy').hex(), references=[])
    store.retain(sign_record(record, authority))
    image_path = directory / 'synthetic.jpg'
    Image.new('RGB', (64, 48), (31, 87, 143)).save(image_path, format='JPEG')
    asset = image_path.read_bytes()
    bundle = issue_v3(fields, asset, placement=2, credential=None, policy=b'{"demo":"synthetic"}',
                      reference_hash=None, now=now, store=store,
                      journal=ReceiptJournal(directory / 'journal.db'), operation=uuid.uuid4().hex,
                      sign_root=lambda root: key.sign(root, ec.ECDSA(hashes.SHA256())))
    node = OTSBackend(options)._bitcoin_proxy(wallet='ots')
    def save(name, result):
        (directory / name).write_text(json.dumps(result, indent=2) + '\n')
    def remaining():
        value = deadline - time.monotonic()
        if value <= 0:
            raise TimeoutError('Crypto regtest demo exceeded 120 seconds')
        return value
    try:
        baseline = set(node._call('getrawmempool'))
        submitted = stamp_v3(bundle, directory / 'anchors', config=options, now=now, store=store)
        assert submitted.settlement.status is Status.PENDING
        proof = Path(submitted.settlement.proof_file)
        bundle = replace(bundle, ots_proof=proof.read_bytes())
        pending = verify_v3_ots(bundle, config=options, now=now, store=store)
        assert pending['anchor'] == 'pending' and pending['decision']['level'] == 'none'
        save('pending.json', pending)
        export_bundle(bundle, directory / 'pending.zip')
        while not set(node._call('getrawmempool')) - baseline:
            remaining(); time.sleep(.25)
        node._call('generatetoaddress', 2, node._call('getnewaddress'))
        while True:
            remaining()
            assert service.upgrade_proof(proof).status is Status.PENDING
            bundle = replace(bundle, ots_proof=proof.read_bytes())
            verified = verify_v3_ots(bundle, config=options, now=datetime.now(timezone.utc), store=store)
            if verified['anchor'] == 'verified':
                break
            assert verified['anchor'] == 'pending', verified
            time.sleep(.25)
        assert verified['decision']['level'] == 'none'
        assert verified['anchorDetails']['network'] == 'regtest'
        assert verified['anchorDetails']['block_height'] is not None
        assert verified['anchorDetails']['block_time'] is not None
        save('verified.json', verified)
        archive = export_bundle(bundle, directory / 'evidence.zip')
        command = [sys.executable, '-m', 'fingerprinto', 'v3', 'verify-ots', str(archive),
                   '--trust-db', str(directory / 'trust.db'), '--authority-spki', str(directory / 'authority.der'),
                   '--network', 'regtest', '--bitcoin-conf', '/config/client.conf', '--timeout', '3']
        cli = subprocess.run(command, capture_output=True, text=True, timeout=remaining())
        assert cli.returncode == 0, cli.stderr
        cli_result = json.loads(cli.stdout)
        assert cli_result['anchor'] == 'verified' and cli_result['decision']['level'] == 'none'
        save('cli.json', cli_result)
        changed = verify_v3_ots(replace(bundle, asset=asset+b'changed'), config=options,
                                now=datetime.now(timezone.utc), store=store)
        assert changed['signature'] == 'invalid' and changed['anchor'] == 'failed'
        save('modified.json', changed)
        summary = dict(artifacts=str(directory), network='regtest', before='pending', after='verified',
                       origin='none', enrollment='synthetic authority fixture', photo_sha256=hashlib.sha256(asset).hexdigest(),
                       block_height=verified['anchorDetails']['block_height'],
                       block_time=verified['anchorDetails']['block_time'], modified='failed', hardware_tested=False)
        save('summary.json', summary)
        return summary
    finally:
        node.close()


if __name__ == '__main__':
    print(json.dumps(run_demo(), indent=2))
