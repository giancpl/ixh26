"""Real legacy image-byte HTTP/Core test; no camera-origin or Nostr claim."""
import argparse
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import time
import uuid

import httpx
from PIL import Image
from settlement import OTSConfig, SettlementService
from settlement.ots_backend import OTSBackend


def run_demo(mode='manual', root=Path('/artifacts')):
    directory = Path(root) / ('server-regtest-' + mode + '-' + uuid.uuid4().hex)
    directory.mkdir(parents=True)
    image = Image.new('RGB', (64, 48), tuple(os.urandom(3)))
    buffer = BytesIO()
    image.save(buffer, format='JPEG')
    data = buffer.getvalue()
    digest = hashlib.sha256(data).hexdigest()
    payload = directory / (digest + '.jpg')
    payload.write_bytes(data)
    config = OTSConfig(network='regtest', bitcoin_conf='/config/client.conf', calendars=('http://calendar:14788',))
    node = OTSBackend(config)._bitcoin_proxy(wallet='ots')
    started = time.monotonic()
    deadline = started + 120

    def wait(operation, accept):
        while time.monotonic() < deadline:
            result = operation()
            if accept(result): return result
            time.sleep(.25)
        raise TimeoutError('The real server regtest lifecycle exceeded 120 seconds.')

    try:
        initial_height = node._call('getblockcount')
        mempool = set(node._call('getrawmempool'))
        with httpx.Client(base_url=os.environ.get('IXH26_API_URL', 'http://server:8787'), timeout=130) as client:
            response = client.post('/api/photos', files={'file': ('photo.jpg', data, 'image/jpeg')},
                                   data={'sha256': digest, 'source': 'web-camera', 'auto_anchor': '1'})
            response.raise_for_status()
            photo = response.json()
            assert photo['anchor']['status'] == ('PENDING' if mode == 'manual' else 'CONFIRMED'), photo
            proof_bytes = client.get(photo['anchor']['proof_url']).content
            proof = directory / 'proof.ots'
            proof.write_bytes(proof_bytes)
            assert OTSBackend(config).digest(proof_bytes).hex() == digest
            if mode == 'manual':
                before = client.post('/api/verify', files={'file': ('photo.jpg', data)}, data={'expected_sha256': digest})
                before.raise_for_status()
                assert before.json()['status'] == 'PENDING'
                assert node._call('getblockcount') == initial_height
                wait(lambda: set(node._call('getrawmempool')) - mempool, bool)
                node._call('generatetoaddress', 2, node._call('getnewaddress'))
                def upgrade():
                    result = client.post('/api/anchor/' + digest + '/upgrade')
                    result.raise_for_status()
                    return result.json()
                wait(upgrade, lambda result: result['status'] == 'CONFIRMED')
            verified = client.post('/api/verify', files={'file': ('photo.jpg', data)}, data={'expected_sha256': digest})
            verified.raise_for_status()
            report = verified.json()
            assert report['status'] == 'CONFIRMED' and report['network'] == 'regtest' and report['binding_checked']
            assert report['block_height'] is not None and report['block_time'] is not None
            assert any(d['code'] == 'regtest_only' for d in report['diagnostics'])
            confirmed_bytes = client.get(photo['anchor']['proof_url']).content
            proof.write_bytes(confirmed_bytes)
            independent = SettlementService(config).verify_file(payload, proof).to_dict()
            assert independent['status'] == 'CONFIRMED' and independent['binding_checked']
            height = node._call('getblockcount')
            altered = client.post('/api/verify', files={'file': ('changed.jpg', data + b'changed')},
                                  data={'expected_sha256': digest})
            altered.raise_for_status()
            assert altered.json()['status'] == 'INVALID'
            malformed = client.post('/api/verify', files={'file': ('photo.jpg', data),
                'proof': ('fake.ots', b'prefix' + bytes.fromhex(digest) + b'suffix')})
            malformed.raise_for_status()
            assert malformed.json()['status'] == 'INVALID' and not malformed.json()['binding_checked']
            assert node._call('getblockcount') == height
            summary = {'mode': mode, 'network': 'regtest', 'status': report['status'], 'sha256': digest,
                'binding_checked': report['binding_checked'], 'bitcoin': report['bitcoin'],
                'blocks_mined': height - initial_height, 'modified_photo': 'INVALID', 'fake_proof': 'INVALID',
                'independent_verification': independent['status'], 'artifacts': str(directory)}
            assert summary['blocks_mined'] == 2
            assert time.monotonic() < deadline
            for name, value in [('summary.json', summary), ('verified.json', report), ('independent.json', independent)]:
                (directory / name).write_text(json.dumps(value, indent=2) + '\n')
            return summary
    finally:
        node.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=('manual', 'auto'), default='manual')
    print(json.dumps(run_demo(parser.parse_args().mode), indent=2))
