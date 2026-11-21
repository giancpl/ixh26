"""Real Docker end-to-end check. Does not claim Android device acceptance."""
import asyncio
from datetime import timedelta
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
import zipfile

import httpx
from nostr_sdk import Client, Event, EventBuilder, EventId, Filter, Keys, Kind, RelayUrl, ReqTarget, Tag
from PIL import Image

from settlement import OTSConfig
from settlement.ots_backend import OTSBackend
from upp_protocol import build_payload, verify_photo_bytes


async def relay_profile_and_read(profile, event_id=None):
    client = Client()
    url = RelayUrl.parse(os.environ.get("PHOTOANCHOR_RELAY_URL", "ws://relay:8080"))
    await client.add_relay(url)
    await client.connect(timedelta(seconds=5))
    try:
        if profile is not None:
            output = await client.send_event(profile, ok_timeout=timedelta(seconds=5))
            if not output.success:
                raise RuntimeError("The local relay did not acknowledge the signed profile.")
        if event_id is not None:
            return await client.fetch_events(ReqTarget.single(url, [Filter().kind(Kind(20)).id(EventId.parse(event_id))]),
                                             timeout=timedelta(seconds=5))
        return []
    finally:
        await client.shutdown()


def run_demo(root=Path("/artifacts")):
    root = Path(root)
    directory = root / ("social-" + uuid.uuid4().hex)
    directory.mkdir(parents=True)
    api = os.environ.get("PHOTOANCHOR_API_URL", "http://api:8080")
    deadline = time.monotonic() + 120

    def wait_for(operation, accept, description):
        while True:
            result = operation()
            if accept(result):
                return result
            if time.monotonic() >= deadline:
                raise TimeoutError(description + " did not complete within 120 seconds.")
            time.sleep(1)

    def save(name, result):
        (directory / name).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")

    def bundle_check(path, expected):
        result = subprocess.run([sys.executable, "-m", "upp_protocol.verify_bundle", str(path),
                                 "--network", "regtest", "--bitcoin-conf", "/config/client.conf"],
                                capture_output=True, timeout=20)
        value = json.loads(result.stdout)
        if result.returncode != expected:
            raise RuntimeError("Independent bundle verification failed: " + json.dumps(value))
        return value

    backend = OTSBackend(OTSConfig(network="regtest", bitcoin_conf="/config/client.conf", timeout=30))
    node = backend._bitcoin_proxy(wallet="ots")
    try:
        initial_mempool = set(node._call("getrawmempool"))
        alice, bob = Keys.generate(), Keys.generate()
        assert alice.public_key().to_hex() != bob.public_key().to_hex()
        profile = EventBuilder(Kind(0), json.dumps({"name": "Alice local demo"})).finalize(alice)
        asyncio.run(relay_profile_and_read(profile))
        asyncio.run(relay_profile_and_read(EventBuilder(Kind(0), '{"name":"Bob local demo"}').finalize(bob)))
        image = Image.new("RGB", (64, 48), tuple(os.urandom(3)))
        buffer = BytesIO()
        image.save(buffer, format="JPEG")
        photo = buffer.getvalue()
        digest = hashlib.sha256(photo).hexdigest()
        (directory / "photo.jpg").write_bytes(photo)
        with httpx.Client(base_url=api, timeout=15) as client:
            response = client.post("/v1/media", content=photo, headers={"Content-Type": "image/jpeg"})
            assert response.status_code in (200, 201), response.text
            media = response.json()
            assert media["sha256"] == digest and media["size"] == len(photo)
            caption = "Alice → Bob local demo " + directory.name
            signed = EventBuilder(Kind(20), caption).tags([
                Tag.parse(["title", caption]),
                Tag.parse(["imeta", "url " + media["url"], "m image/jpeg", "x " + digest, "dim 64x48"]),
                Tag.parse(["m", "image/jpeg"]), Tag.parse(["x", digest])]).finalize(alice)
            event = json.loads(signed.as_json())
            save("event.json", event)
            (directory / "payload.bin").write_bytes(build_payload(event))
            response = client.post("/v1/posts", json={"event": event})
            assert response.status_code == 202, response.text
            retry = client.post("/v1/posts", json={"event": event})
            assert retry.status_code == 200, retry.text
            post_url = "/v1/posts/" + event["id"]
            before = wait_for(lambda: client.get(post_url).json(),
                              lambda r: r["publication_status"] == "PUBLISHED" and r["settlement"] is not None
                              and r["settlement"]["status"] == "PENDING",
                              "Relay publication and OTS submission")
            save("before-mining.json", before)
            relay_events = asyncio.run(relay_profile_and_read(None, event["id"]))
            assert any(e.as_json() and e.id().to_hex() == event["id"] for e in relay_events)
            original = client.get("/v1/media/" + digest).content
            assert original == photo
            verify_photo_bytes(event, original)
            # Profile declarations can change without changing the old signature.
            asyncio.run(relay_profile_and_read(EventBuilder(Kind(0), '{"name":"Alice renamed"}').finalize(alice)))
            verify_photo_bytes(event, original)
            fresh = client.post(post_url + "/verify")
            assert fresh.status_code == 200, fresh.text
            save("pending-verification.json", fresh.json())
            assert fresh.json()["settlement"]["status"] == "PENDING"
            pending_zip = client.get(post_url + "/bundle")
            assert pending_zip.status_code == 200, pending_zip.text
            (directory / "pending.zip").write_bytes(pending_zip.content)
            save("pending-bundle-check.json", bundle_check(directory / "pending.zip", 2))
            wait_for(lambda: set(node._call("getrawmempool")) - initial_mempool, bool, "Calendar transaction")
            node._call("generatetoaddress", 2, node._call("getnewaddress"))
            confirmed = wait_for(lambda: client.post(post_url + "/verify").json(),
                                 lambda r: r.get("settlement", {}).get("status") == "CONFIRMED",
                                 "Fresh regtest verification")
            receipt = confirmed["settlement"]
            assert receipt["network"] == "regtest" and receipt["binding_checked"]
            assert receipt["payload_sha256"] == hashlib.sha256(build_payload(event)).hexdigest()
            assert receipt["bitcoin"]["block_height"] is not None and receipt["bitcoin"]["block_time"] is not None
            assert any(d["code"] == "regtest_only" for d in receipt["diagnostics"])
            save("confirmed.json", confirmed)
            response = client.get(post_url + "/bundle")
            assert response.status_code == 200
            (directory / "bundle.zip").write_bytes(response.content)
            bundle = bundle_check(directory / "bundle.zip", 0)
            save("bundle-check.json", bundle)
            (directory / "proof.ots").write_bytes(client.get(post_url + "/proof").content)
            # Repack a changed photo, preserving the standard proof and event.
            with zipfile.ZipFile(directory / "bundle.zip") as source, zipfile.ZipFile(directory / "modified.zip", "w") as changed:
                for member in source.namelist():
                    data = source.read(member)
                    changed.writestr(member, data + b"changed" if member == "photo.jpg" else data)
            invalid = bundle_check(directory / "modified.zip", 3)
            save("modified-check.json", invalid)
            summary = {"network": "regtest", "event_id": event["id"], "publication_status": "PUBLISHED",
                       "before_mining": "PENDING", "after_mining": "CONFIRMED", "modified_photo": "INVALID",
                       "bitcoin": receipt["bitcoin"], "artifacts": str(directory), "android_devices_tested": False}
            save("summary.json", summary)
            return summary
    finally:
        node.close()


if __name__ == "__main__":
    print(json.dumps(run_demo(), indent=2))
