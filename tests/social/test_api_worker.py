"""Offline HTTP/storage tests; only relay/calendar/Bitcoin boundaries are fake."""
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
import hashlib
import json
from pathlib import Path
import threading
import time
import zipfile

import asyncio
import httpx
from nostr_sdk import EventBuilder, Keys, Kind, Tag
import pytest

from settlement import BitcoinEvidence, SettlementResult, Status
from social.api import create_app
from social.config import Config
from social.storage import Store
from social.worker import Worker
from upp_protocol import MAX_MEDIA_BYTES, MAX_POST_BYTES, build_payload

class TestClient:
    """In-process ASGI transport; no socket or portal thread is needed."""
    __test__ = False

    def __init__(self, app, **kwargs):
        self.app = app

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def request(self, method, path, **kwargs):
        async def run():
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, raise_app_exceptions=False), base_url="http://testserver") as client:
                return await client.request(method, path, **kwargs)
        return asyncio.run(run())

    def get(self, path, **kwargs):
        return self.request("GET", path, **kwargs)

    def post(self, path, **kwargs):
        return self.request("POST", path, **kwargs)


FIXTURES = Path(__file__).resolve().parents[2] / "contracts" / "fixtures"
PHOTO = (FIXTURES / "photo.jpg").read_bytes()
EVENT = json.loads((FIXTURES / "event.json").read_text())


def signed(*, dim="64x48", url=None, digest=None, content="Test Ω"):
    digest = digest or hashlib.sha256(PHOTO).hexdigest()
    url = url or "http://127.0.0.1:8080/v1/media/" + digest
    return json.loads(EventBuilder(Kind(20), content).tags([
        Tag.parse(["title", content or "Photo"]),
        Tag.parse(["imeta", "url " + url, "m image/jpeg", "x " + digest, "dim " + dim]),
        Tag.parse(["m", "image/jpeg"]), Tag.parse(["x", digest]),
    ]).finalize(Keys.generate()).as_json())


@pytest.fixture
def config(tmp_path):
    return Config(data_dir=tmp_path, network="regtest", calendars=("http://calendar:14788",), retry_seconds=.01, lease_seconds=1)


@pytest.fixture
def app(config):
    return create_app(config)


@pytest.fixture
def client(app):
    with TestClient(app, raise_server_exceptions=False) as value:
        yield value


def upload(client):
    return client.post("/v1/media", content=PHOTO, headers={"Content-Type": "image/jpeg"})


def accept(client, event=EVENT):
    assert upload(client).status_code in (200, 201)
    return client.post("/v1/posts", json={"event": event})


def force_due(store):
    with store.connection() as db:
        db.execute("UPDATE jobs SET due=0")


def test_upload_original_duplicate_and_concurrent(client):
    with ThreadPoolExecutor(max_workers=6) as executor:
        replies = list(executor.map(lambda _: upload(client), range(6)))
    assert sorted(x.status_code for x in replies) == [200] * 5 + [201]
    media = replies[0].json()
    assert media == {"sha256": hashlib.sha256(PHOTO).hexdigest(), "url": EVENT["tags"][1][1][4:], "mime": "image/jpeg", "size": len(PHOTO)}
    assert client.get(media["url"]).content == PHOTO


def test_limits_mime_and_invalid_jpeg(client):
    assert client.post("/v1/media", content=PHOTO).status_code == 415
    assert client.post("/v1/media", content=b"\xff\xd8\xffbad", headers={"Content-Type": "image/jpeg"}).status_code == 400
    assert client.post("/v1/media", content=PHOTO[:-10], headers={"Content-Type": "image/jpeg"}).status_code == 400
    assert client.post("/v1/media", content=b"x", headers={"Content-Type": "image/jpeg", "Content-Length": str(MAX_MEDIA_BYTES + 1)}).status_code == 413
    assert client.post("/v1/posts", content=b" " * (MAX_POST_BYTES + 1)).status_code == 413


@pytest.mark.parametrize("body", [b"{", b"[]", b'{"event":null}', b'{"event":{},"secret":"no"}'])
def test_malformed_json_is_sanitized(client, body):
    response = client.post("/v1/posts", content=body)
    assert response.status_code == 400
    assert set(response.json()) == {"error"}
    assert set(response.json()["error"]) == {"code", "message"}


def test_accept_idempotent_conflicting_retry_restart(config, client, app):
    accepted = accept(client)
    assert accepted.status_code == 202
    assert accepted.json() == {"event": EVENT, "publication_status": "QUEUED", "settlement": None}
    assert client.post("/v1/posts", json={"event": dict(reversed(list(EVENT.items())))}).status_code == 200
    conflict = dict(EVENT, sig="0" * 128)
    assert client.post("/v1/posts", json={"event": conflict}).status_code == 409
    with TestClient(create_app(config)) as restarted:
        assert restarted.get("/v1/posts/" + EVENT["id"]).json() == accepted.json()
        assert restarted.post("/v1/posts", json={"event": EVENT}).status_code == 200
    with app.state.store.connection() as db:
        assert db.execute("SELECT count(*) FROM posts").fetchone()[0] == 1
        assert db.execute("SELECT count(*) FROM jobs").fetchone()[0] == 2


def test_concurrent_post_acceptance(client):
    upload(client)
    with ThreadPoolExecutor(max_workers=5) as executor:
        replies = list(executor.map(lambda _: client.post("/v1/posts", json={"event": EVENT}), range(5)))
    assert sorted(r.status_code for r in replies) == [200] * 4 + [202]


def test_signature_media_url_hash_and_dimensions(client, app):
    assert client.post("/v1/posts", json={"event": EVENT}).json()["error"]["code"] == "media_not_found"
    upload(client)
    for name, expected in [("signature", "invalid_signature"), ("metadata", "invalid_event_id")]:
        event = json.loads((FIXTURES / "negative" / (name + ".json")).read_text())
        assert client.post("/v1/posts", json={"event": event}).json()["error"]["code"] == expected
    for event, expected in [(signed(url="http://169.254.169.254/latest"), "external_media_url"),
                            (signed(dim="10x10"), "photo_dimensions_mismatch"),
                            (signed(digest="0" * 64), "media_not_found")]:
        assert client.post("/v1/posts", json={"event": event}).json()["error"]["code"] == expected
    (app.state.store.media / (hashlib.sha256(PHOTO).hexdigest() + ".jpg")).write_bytes(PHOTO + b"modified")
    assert client.post("/v1/posts", json={"event": EVENT}).json()["error"]["code"] == "photo_hash_mismatch"


def test_routes_proof_not_ready_and_sanitized_errors(client, app):
    assert client.get("/healthz").json() == {"status": "ok"}
    assert client.get("/unknown").json()["error"]["code"] == "not_found"
    assert client.get("/v1/media/not-a-digest").status_code == 404
    assert client.get("/v1/posts/" + "0" * 64).status_code == 404
    accept(client)
    for suffix, code in [("proof", 404), ("bundle", 409)]:
        response = client.get("/v1/posts/" + EVENT["id"] + "/" + suffix)
        assert response.status_code == code
        assert response.json()["error"]["code"] == "proof_not_ready"
    assert client.post("/v1/posts/" + EVENT["id"] + "/verify").status_code == 404
    app.state.store.health = lambda: (_ for _ in ()).throw(RuntimeError("rpcpassword=SECRET"))
    response = client.get("/healthz")
    assert response.status_code == 503 and "SECRET" not in response.text


def fake_stamp(monkeypatch):
    # Create real standard OTS proofs by replacing only remote submissions.
    from opentimestamps.core.notary import PendingAttestation
    from opentimestamps.core.op import OpSHA256
    from opentimestamps.core.serialize import BytesSerializationContext
    from opentimestamps.core.timestamp import DetachedTimestampFile, Timestamp

    def stamp(self, fd):
        timestamp = Timestamp(hashlib.sha256(fd.read()).digest())
        timestamp.attestations.add(PendingAttestation("http://calendar:14788"))
        proof = DetachedTimestampFile(OpSHA256(), timestamp)
        context = BytesSerializationContext()
        proof.serialize(context)
        return context.getbytes()
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.stamp", stamp)


class Relay:
    def __init__(self, fail=False):
        self.fail, self.events = fail, []

    def publish(self, event):
        if self.fail:
            raise RuntimeError("credentials must never appear in a response")
        self.events.append(event)


def test_independent_jobs_calendar_failure_does_not_block_relay(client, app, config, monkeypatch):
    accept(client)
    def fail(self, fd):
        raise RuntimeError("sensitive calendar exception")
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.stamp", fail)
    relay = Relay()
    worker = Worker(config, relay=relay)
    worker.tick()
    worker.tick()
    snapshot = app.state.store.snapshot(EVENT["id"])
    assert snapshot["publication_status"] == "PUBLISHED"
    assert relay.events == [EVENT]
    assert snapshot["settlement"]["status"] == "ERROR"
    assert "sensitive" not in json.dumps(snapshot)
    assert "proof_file" not in snapshot["settlement"]


def test_relay_failure_retry_and_worker_restart_preserve_event(client, app, config, monkeypatch):
    fake_stamp(monkeypatch)
    accept(client)
    relay = Relay(fail=True)
    worker = Worker(config, relay=relay)
    worker.tick()
    worker.tick()
    snapshot = app.state.store.snapshot(EVENT["id"])
    assert snapshot["publication_status"] == "ERROR"
    assert snapshot["settlement"]["status"] == "PENDING"
    original = app.state.store.proof_path(EVENT["id"]).read_bytes()
    force_due(app.state.store)
    relay.fail = False
    restarted = Worker(config, relay=relay)
    restarted.tick()
    assert relay.events == [EVENT]
    assert app.state.store.snapshot(EVENT["id"])["publication_status"] == "PUBLISHED"
    assert app.state.store.proof_path(EVENT["id"]).read_bytes() == original


def test_fresh_verify_and_bundle_ignore_cached_receipt(client, app, config, monkeypatch):
    fake_stamp(monkeypatch)
    accept(client)
    worker = Worker(config, relay=Relay())
    worker.tick()
    worker.tick()
    proof = app.state.store.proof_path(EVENT["id"])
    cached = SettlementResult(Status.CONFIRMED, proof_file=str(proof), network="regtest",
                              bitcoin=BitcoinEvidence(99, 1700000000))
    app.state.store.settlement(EVENT["id"], cached)
    calls = []
    def unavailable(self, data):
        calls.append(data)
        return Status.UNVERIFIABLE, BitcoinEvidence(), []
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.verify", unavailable)
    monkeypatch.setattr("settlement.ots_backend.OTSBackend.upgrade", lambda *args: pytest.fail("Verify must not call a calendar"))
    response = client.post("/v1/posts/" + EVENT["id"] + "/verify")
    receipt = response.json()["settlement"]
    assert receipt["status"] == "UNVERIFIABLE" and receipt["binding_checked"] is True and len(calls) == 1
    assert receipt["bitcoin"] == {"block_height": None, "block_time": None}
    assert receipt["proof_url"].endswith("/" + EVENT["id"] + "/proof")
    assert "proof_file" not in receipt and str(config.data_dir) not in response.text
    archive = client.get("/v1/posts/" + EVENT["id"] + "/bundle")
    assert archive.status_code == 200
    with zipfile.ZipFile(BytesIO(archive.content)) as bundle:
        assert {"photo.jpg", "event.json", "payload.bin", "proof.ots", "README.txt"} <= set(bundle.namelist())
        assert bundle.read("photo.jpg") == PHOTO
        assert json.loads(bundle.read("event.json")) == EVENT
        assert bundle.read("payload.bin") == build_payload(EVENT)
        assert bundle.read("proof.ots") == proof.read_bytes()


def test_claim_excludes_concurrent_same_event_and_recovers_crash(config):
    store = Store(config)
    store.accept(EVENT)
    first = store.claim(now=10000000000)
    assert first is not None
    assert Store(config).claim(now=10000000000) is None
    recovered = Store(config).claim(now=10000000002)
    assert recovered and recovered["owner"] != first["owner"]
    store.finish(first, done=True, published=True)
    assert store.get(EVENT["id"])["publication_status"] == "QUEUED"
    store.finish(recovered, done=True, published=True)
    assert store.get(EVENT["id"])["publication_status"] == "PUBLISHED"


def test_live_worker_renews_lease(config):
    store = Store(config)
    store.accept(EVENT)
    started, release = threading.Event(), threading.Event()
    class SlowRelay:
        def publish(self, event):
            started.set()
            assert release.wait(4)
    worker = Worker(config, relay=SlowRelay())
    thread = threading.Thread(target=worker.tick)
    thread.start()
    assert started.wait(2)
    time.sleep(1.1)
    assert Store(config).claim() is None
    release.set()
    thread.join(3)
    assert not thread.is_alive()


def test_configuration_environment_defaults_and_regtest(monkeypatch, tmp_path):
    monkeypatch.setenv("PHOTOANCHOR_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("PHOTOANCHOR_NETWORK", "regtest")
    monkeypatch.setenv("PHOTOANCHOR_CALENDARS", "http://calendar:14788")
    config = Config.from_env()
    assert config.network == "regtest" and config.calendars == ("http://calendar:14788",)
    assert Config().network == "mainnet"
    with pytest.raises(ValueError):
        Config(public_url="http://user:password@localhost")


def test_lost_heartbeat_and_expired_lease_cannot_overlap_execution(config):
    store = Store(config)
    store.accept(EVENT)
    started, release = threading.Event(), threading.Event()
    calls = []

    class SlowRelay:
        def publish(self, event):
            calls.append(event)
            started.set()
            assert release.wait(4)

    first = Worker(config, store=store, relay=SlowRelay())
    # Simulate the failed heartbeat after a temporary database outage.
    first.store.renew = lambda job: False
    thread = threading.Thread(target=first.tick)
    thread.start()
    assert started.wait(2)
    time.sleep(1.1)
    second_relay = Relay()
    second = Worker(config, store=Store(config), relay=second_relay)
    assert second.tick() is True  # Takes expired SQLite lease, cannot get flock.
    assert second_relay.events == []
    release.set()
    thread.join(3)
    assert not thread.is_alive() and calls == [EVENT]
    assert store.snapshot(EVENT["id"])["publication_status"] == "QUEUED"
    force_due(store)
    assert second.tick() is True
    assert second_relay.events == [EVENT]
    assert store.snapshot(EVENT["id"])["publication_status"] == "PUBLISHED"


def test_stale_lease_owner_cannot_replace_settlement_metadata(config):
    store = Store(config)
    store.accept(EVENT)
    first = store.claim(now=10000000000)
    replacement = store.claim(now=10000000002)
    assert replacement["owner"] != first["owner"]
    store.settlement(EVENT["id"], SettlementResult(Status.ERROR), job=first)
    assert store.snapshot(EVENT["id"])["settlement"] is None
    store.settlement(EVENT["id"], SettlementResult(Status.PENDING), job=replacement)
    assert store.snapshot(EVENT["id"])["settlement"]["status"] == "PENDING"


def test_stream_limit_without_content_length(client, monkeypatch):
    monkeypatch.setattr("social.api.MAX_MEDIA_BYTES", len(PHOTO) - 1)
    async def stream():
        yield PHOTO[:10]
        yield PHOTO[10:]
    response = client.post("/v1/media", content=stream(), headers={"Content-Type": "image/jpeg"})
    assert response.status_code == 413


def test_default_application_does_not_submit_to_public_calendars():
    assert Config().ots_config().calendars == ()


def test_calendar_and_node_outages_resume_to_real_proof_confirmation(client, app, config, monkeypatch):
    from bitcoin.core import CBlockHeader
    from opentimestamps.core.notary import BitcoinBlockHeaderAttestation
    from opentimestamps.core.timestamp import Timestamp
    fake_stamp(monkeypatch)
    accept(client)
    worker = Worker(config, relay=Relay())
    worker.tick()
    worker.tick()
    original = app.state.store.proof_path(EVENT["id"]).read_bytes()
    state = {"calendar": False, "node": False}

    class Calendar:
        url = "http://calendar:14788"
        def get_timestamp(self, commitment, timeout=None):
            if not state["calendar"]:
                raise OSError("calendar offline")
            timestamp = Timestamp(commitment)
            timestamp.attestations.add(BitcoinBlockHeaderAttestation(105))
            return timestamp

    class Node:
        def _call(self, method):
            return {"chain": "regtest", "initialblockdownload": False, "blocks": 110, "headers": 110}
        def getblockhash(self, height):
            assert height == 105
            return b"\x01" * 32
        def getblockheader(self, block_hash):
            return CBlockHeader(hashMerkleRoot=hashlib.sha256(build_payload(EVENT)).digest(), nTime=1700000100)
        def close(self):
            pass

    def proxy(self):
        if not state["node"]:
            raise OSError("node offline")
        return Node()

    monkeypatch.setattr("settlement.ots_backend.remote_calendar", lambda uri: Calendar())
    monkeypatch.setattr("settlement.ots_backend.OTSBackend._bitcoin_proxy", proxy)
    force_due(app.state.store)
    worker.tick()
    pending = app.state.store.snapshot(EVENT["id"])["settlement"]
    assert pending["status"] == "PENDING"
    assert "calendar_unavailable" in {d["code"] for d in pending["diagnostics"]}
    assert app.state.store.proof_path(EVENT["id"]).read_bytes() == original
    state["calendar"] = True
    force_due(app.state.store)
    worker.tick()
    unavailable = app.state.store.snapshot(EVENT["id"])["settlement"]
    assert unavailable["status"] == "UNVERIFIABLE"
    assert unavailable["bitcoin"]["block_height"] is None
    state["node"] = True
    force_due(app.state.store)
    Worker(config, relay=Relay()).tick()
    confirmed = app.state.store.snapshot(EVENT["id"])["settlement"]
    assert confirmed["status"] == "CONFIRMED"
    assert confirmed["network"] == "regtest" and confirmed["binding_checked"]
    assert confirmed["bitcoin"] == {"block_height": 105, "block_time": 1700000100}
    assert "regtest_only" in {d["code"] for d in confirmed["diagnostics"]}


def test_write_errors_are_sanitized_without_partial_media(client, app, monkeypatch):
    def fail(*args):
        raise OSError("/private/internal/path rpcpassword=secret")
    monkeypatch.setattr("social.api.publish_bytes", fail)
    response = upload(client)
    assert response.status_code == 500
    assert response.json()["error"]["code"] == "operation_failed"
    assert "secret" not in response.text and "internal/path" not in response.text
    assert list(app.state.store.media.iterdir()) == []
