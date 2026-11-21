"""Real OTS serialization/service; only calendar and Core RPC boundaries are fake."""
import asyncio
import hashlib
from pathlib import Path
from urllib.error import URLError

from bitcoin.core import CBlockHeader
from opentimestamps.core.notary import BitcoinBlockHeaderAttestation
from opentimestamps.core.timestamp import Timestamp
import pytest

from conftest import proof_bytes
from server.anchoring import AnchorManager
from server.storage import SocialStore

CALENDAR = 'https://alice.btc.calendar.opentimestamps.org'


@pytest.fixture
def manager(tmp_path):
    store = SocialStore(tmp_path / 'store')
    manager = AnchorManager(store, network='regtest', calendars=(CALENDAR,))
    yield manager
    store.close()


def pending(manager, payload):
    digest = hashlib.sha256(payload.read_bytes()).hexdigest()
    proof = manager.proof_path(digest)
    proof.write_bytes(proof_bytes(payload.read_bytes()))
    return digest, proof


@pytest.fixture
def rpc(monkeypatch, payload):
    state = {'chain': 'regtest', 'sync': True, 'mempool': ['tx'], 'mined': 0, 'calls': []}
    class Node:
        def _call(self, method, *args):
            state['calls'].append(method)
            if method == 'getblockchaininfo':
                return {'chain': state['chain'], 'initialblockdownload': not state['sync'], 'blocks': 1000, 'headers': 1000}
            if method == 'getrawmempool': return state['mempool']
            if method == 'getnewaddress': return 'development-address'
            if method == 'generatetoaddress':
                assert args[0] == 2
                state['mined'] += 2
                return ['a', 'b']
            raise AssertionError(method)
        def getblockhash(self, height):
            assert height == 100
            return b'\x01' * 32
        def getblockheader(self, block):
            return CBlockHeader(hashMerkleRoot=hashlib.sha256(payload.read_bytes()).digest(), nTime=1700000000)
        def close(self): pass
    monkeypatch.setattr('settlement.ots_backend.OTSBackend._bitcoin_proxy', lambda self, wallet=None: Node())
    return state


def calendar_confirm(monkeypatch, rpc):
    def upgrade(calendar, msg, timeout):
        assert rpc['mined'] == 2
        result = Timestamp(msg)
        result.attestations.add(BitcoinBlockHeaderAttestation(100))
        return result
    monkeypatch.setattr('settlement.ots_backend.get_timestamp_within', upgrade)


def forbidden(*args, **kwargs):
    raise AssertionError('Unexpected external operation')


def test_defaults_and_explicit_configuration(tmp_path, monkeypatch):
    store = SocialStore(tmp_path / 'store')
    monkeypatch.delenv('IXH26_CALENDARS', raising=False)
    monkeypatch.delenv('IXH26_AUTO_CONFIRM_REGTEST', raising=False)
    try:
        assert not AnchorManager(store).auto_confirm_regtest
        assert AnchorManager(store).calendars == ()
        assert not AnchorManager(store, network='regtest').auto_confirm_regtest
        with pytest.raises(ValueError): AnchorManager(store, auto_confirm_regtest=True)
        with pytest.raises(ValueError): AnchorManager(store, network='testnet')
        with pytest.raises(ValueError): AnchorManager(store, calendars=('http://calendar:14788',))
        assert AnchorManager(store, network='regtest', calendars=('http://calendar:14788',)).calendars
        monkeypatch.setenv('IXH26_AUTO_CONFIRM_REGTEST', '1')
        assert AnchorManager(store, network='regtest').auto_confirm_regtest
    finally:
        store.close()


def test_verify_is_local_readonly_and_never_mines(manager, payload, monkeypatch):
    manager.auto_confirm_regtest = True
    digest, proof = pending(manager, payload)
    before = proof.read_bytes(), proof.stat().st_mtime_ns
    calls = []
    def unexpected(*args, **kwargs):
        calls.append('external operation')
        raise AssertionError('Verification contacted an external service')
    monkeypatch.setattr(manager, '_node', unexpected)
    monkeypatch.setattr('settlement.ots_backend.get_timestamp_within', unexpected)
    result = asyncio.run(manager.verify_bytes(payload.read_bytes(), expected_sha256=digest))
    assert result['status'] == 'PENDING' and result['binding_checked']
    assert result['block_height'] is None and result['block_time'] is None
    assert (proof.read_bytes(), proof.stat().st_mtime_ns) == before
    assert 'proof_file' not in result
    assert calls == []


@pytest.mark.parametrize('proof', [b'bad', b'prefix' + hashlib.sha256(b'bytes').digest() + b'suffix'])
def test_digest_membership_is_not_proof(manager, proof):
    result = asyncio.run(manager.verify_bytes(b'bytes', proof_bytes=proof))
    assert result['status'] == 'INVALID'
    assert not result['binding_checked']


def test_missing_proof_and_altered_payload(manager, payload):
    assert asyncio.run(manager.verify_bytes(b'no proof'))['status'] == 'UNVERIFIABLE'
    digest, proof = pending(manager, payload)
    before = proof.read_bytes()
    result = asyncio.run(manager.verify_bytes(payload.read_bytes() + b'changed', expected_sha256=digest))
    assert result['status'] == 'INVALID' and not result['binding_checked']
    assert proof.read_bytes() == before


def test_stamping_keeps_service_filename_and_retries_without_overwrite(manager, payload, fake_calendars):
    digest = hashlib.sha256(payload.read_bytes()).hexdigest()
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'PENDING'
    proof = manager.proof_path(digest)
    assert proof.name == payload.name + '.ots'
    before = proof.read_bytes(), proof.stat().st_mtime_ns
    assert asyncio.run(manager.stamp(digest, payload))['status'] == 'PENDING'
    assert (proof.read_bytes(), proof.stat().st_mtime_ns) == before
    assert not (manager.store.proofs_dir / (digest + '.ots')).exists()


def test_existing_malformed_proof_is_preserved(manager, payload):
    digest = hashlib.sha256(payload.read_bytes()).hexdigest()
    proof = manager.proof_path(digest)
    proof.write_bytes(b'incompatible proof')
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'INVALID' and proof.read_bytes() == b'incompatible proof'


def test_cached_confirmation_cannot_promote_pending_proof(manager, payload):
    digest, proof = pending(manager, payload)
    manager.store.upsert_anchor(sha256=digest, status='CONFIRMED', network='regtest',
        proof_file=proof.name, block_height=42, block_time=123, diagnostics=[])
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'PENDING'
    assert result['block_height'] is None and result['block_time'] is None


def test_upgrade_does_not_modify_a_proof_for_different_bytes(manager, payload, monkeypatch):
    digest = hashlib.sha256(payload.read_bytes()).hexdigest()
    proof = manager.proof_path(digest)
    original = proof_bytes(b'different opaque payload')
    proof.write_bytes(original)
    calls = []
    def unexpected(*args, **kwargs):
        calls.append('calendar')
        raise AssertionError('An incompatible proof was upgraded')
    monkeypatch.setattr('settlement.ots_backend.get_timestamp_within', unexpected)
    result = asyncio.run(manager.upgrade_and_verify(digest, payload))
    assert result['status'] == 'INVALID'
    assert proof.read_bytes() == original
    assert calls == []


@pytest.mark.parametrize('chain,sync', [('main', True), ('regtest', False)])
def test_wrong_or_unsynced_node_cannot_mine(manager, payload, rpc, chain, sync):
    digest, proof = pending(manager, payload)
    manager.auto_confirm_regtest = True
    rpc.update(chain=chain, sync=sync)
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'PENDING' and rpc['mined'] == 0
    assert any(d['code'] == 'auto_mining_unavailable' for d in result['diagnostics'])


def test_explicit_mining_requires_real_verified_attestation(manager, payload, rpc, monkeypatch):
    digest, proof = pending(manager, payload)
    manager.auto_confirm_regtest = True
    calendar_confirm(monkeypatch, rpc)
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'CONFIRMED' and rpc['mined'] == 2
    assert result['block_height'] == 100 and result['block_time'] == 1700000000
    assert any(d['code'] == 'regtest_only' for d in result['diagnostics'])


def test_timeout_preserves_pending_and_retry_succeeds(manager, payload, rpc, monkeypatch):
    digest, proof = pending(manager, payload)
    manager.auto_confirm_regtest, manager.mining_timeout = True, 0.001
    rpc['mempool'] = []
    original = proof.read_bytes()
    result = asyncio.run(manager.stamp(digest, payload))
    assert result['status'] == 'PENDING' and rpc['mined'] == 0 and proof.read_bytes() == original
    manager.mining_timeout = 5
    rpc['mempool'] = ['tx']
    calendar_confirm(monkeypatch, rpc)
    assert asyncio.run(manager.stamp(digest, payload))['status'] == 'CONFIRMED'


def test_calendar_failure_preserves_proof_and_reports_pending(manager, payload, monkeypatch):
    digest, proof = pending(manager, payload)
    original = proof.read_bytes()
    def unavailable(*args): raise URLError('credentials must not escape')
    monkeypatch.setattr('settlement.ots_backend.get_timestamp_within', unavailable)
    result = asyncio.run(manager.upgrade_and_verify(digest, payload))
    assert result['status'] == 'PENDING' and proof.read_bytes() == original
    assert any(d['code'] == 'calendar_unavailable' for d in result['diagnostics'])
    assert 'credentials' not in str(result)


def test_upgrade_disk_failure_does_not_overwrite_proof(manager, payload, monkeypatch, rpc):
    digest, proof = pending(manager, payload)
    original = proof.read_bytes()
    rpc['mined'] = 2
    calendar_confirm(monkeypatch, rpc)
    def fail(*args): raise OSError('disk failure')
    monkeypatch.setattr('settlement.service.os.replace', fail)
    assert asyncio.run(manager.upgrade_and_verify(digest, payload))['status'] == 'ERROR'
    assert proof.read_bytes() == original
