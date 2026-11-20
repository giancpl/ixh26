"""End-to-end tests for server HTTP API, Android PhotoAnchor bridge, Nostr relay, and OTS anchoring."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

import aiohttp
from aiohttp.test_utils import AioHTTPTestCase
from PIL import Image

from server.app import create_app
from server.nostr_relay import compute_event_id


def make_jpeg_bytes(color: tuple[int, int, int] = (0, 28, 224)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (64, 48), color).save(buf, format="JPEG")
    return buf.getvalue()


class WebServerTestCase(AioHTTPTestCase):
    async def get_application(self) -> aiohttp.web.Application:
        self._tmp = tempfile.TemporaryDirectory()
        dist = Path(__file__).resolve().parent.parent / "web" / "dist"
        return create_app(data_dir=Path(self._tmp.name) / "data", dist_dir=dist)

    async def tearDownAsync(self) -> None:
        await super().tearDownAsync()
        self._tmp.cleanup()

    async def test_serves_built_frontend_spa(self) -> None:
        resp = await self.client.get("/")
        self.assertEqual(resp.status, 200)
        html = await resp.text()
        self.assertIn("BitPin", html)

    async def test_android_upload_and_inbox_flow(self) -> None:
        pubkey = "1" * 64

        info_resp = await self.client.get("/api/info")
        self.assertEqual(info_resp.status, 200)
        info = await info_resp.json()
        self.assertTrue(info["ok"])

        jpg = make_jpeg_bytes()
        sha = hashlib.sha256(jpg).hexdigest()

        # Tampered sha256 must be rejected
        bad_form = aiohttp.FormData()
        bad_form.add_field("file", jpg, filename="bad.jpg", content_type="image/jpeg")
        bad_form.add_field("sha256", "0" * 64)
        bad_resp = await self.client.post("/api/photos", data=bad_form)
        self.assertEqual(bad_resp.status, 400)

        # Direct Android camera upload without pairing (LAN / VPN flow)
        form = aiohttp.FormData()
        form.add_field("file", jpg, filename="photo_1.jpg", content_type="image/jpeg")
        form.add_field("sha256", sha)
        form.add_field("source", "android-photoanchor")
        form.add_field("auto_anchor", "0")
        up_resp = await self.client.post("/api/photos", data=form)
        self.assertEqual(up_resp.status, 200)
        uploaded = await up_resp.json()
        self.assertEqual(uploaded["sha256"], sha)
        self.assertEqual(uploaded["width"], 64)
        self.assertEqual(uploaded["height"], 48)
        self.assertEqual(uploaded["source"], "android-photoanchor")
        self.assertEqual(uploaded["anchor"]["status"], "UNANCHORED")

        # Media serves exact verbatim bytes
        media_resp = await self.client.get(f"/api/media/{sha}")
        self.assertEqual(media_resp.status, 200)
        served = await media_resp.read()
        self.assertEqual(served, jpg)

        # Inbox lists un-acked Android photo for any logged-in pubkey
        inbox_resp = await self.client.get(f"/api/camera/inbox?pubkey={pubkey}")
        self.assertEqual(inbox_resp.status, 200)
        inbox = await inbox_resp.json()
        self.assertEqual(len(inbox["items"]), 1)
        self.assertEqual(inbox["items"][0]["sha256"], sha)

        # Ack removes it from inbox
        ack_resp = await self.client.post(f"/api/camera/inbox/{sha}/ack", json={"event_id": "a" * 64})
        self.assertEqual(ack_resp.status, 200)
        inbox_after = await (await self.client.get(f"/api/camera/inbox?pubkey={pubkey}")).json()
        self.assertEqual(inbox_after["items"], [])

    async def test_nostr_relay_ws_event_broadcast_and_query(self) -> None:
        pubkey = "a" * 64
        event = {
            "pubkey": pubkey,
            "created_at": 1700000000,
            "kind": 1,
            "tags": [["t", "photoanchor"], ["x", "b" * 64]],
            "content": "Foto da relay",
            "sig": "c" * 128,
        }
        event["id"] = compute_event_id(event)

        async with self.client.ws_connect("/nostr") as ws1, self.client.ws_connect("/nostr") as ws2:
            await ws2.send_json(["REQ", "sub1", {"kinds": [1], "#t": ["photoanchor"]}])
            eose = await ws2.receive_json()
            self.assertEqual(eose, ["EOSE", "sub1"])

            # Publish invalid id -> rejected
            bad_ev = dict(event, id="0" * 64)
            await ws1.send_json(["EVENT", bad_ev])
            ok_bad = await ws1.receive_json()
            self.assertEqual(ok_bad[0], "OK")
            self.assertFalse(ok_bad[2])

            # Publish valid event -> stored and broadcast live to ws2
            await ws1.send_json(["EVENT", event])
            ok_good = await ws1.receive_json()
            self.assertEqual(ok_good[:3], ["OK", event["id"], True])

            pushed = await ws2.receive_json()
            self.assertEqual(pushed[0], "EVENT")
            self.assertEqual(pushed[1], "sub1")
            self.assertEqual(pushed[2]["id"], event["id"])

            # Fresh REQ retrieves persisted event
            await ws1.send_json(["REQ", "sub2", {"ids": [event["id"]]}])
            msg = await ws1.receive_json()
            self.assertEqual(msg[0], "EVENT")
            self.assertEqual(msg[2]["id"], event["id"])
            eose2 = await ws1.receive_json()
            self.assertEqual(eose2, ["EOSE", "sub2"])

    async def test_ots_calendar_stamp_and_proof_download(self) -> None:
        # Spin up a local test OTS calendar HTTP server simulating /digest
        fake_cal = aiohttp.web.Application()
        fake_commitment = b"\x00\x83\xdf\xe3\x0d\x2e\xf9\x0c\x8e\x2c" + b"https://example.ots"

        async def digest_handler(request: aiohttp.web.Request) -> aiohttp.web.Response:
            body = await request.read()
            self.assertEqual(len(body), 32)
            return aiohttp.web.Response(body=fake_commitment)

        fake_cal.router.add_post("/digest", digest_handler)
        runner = aiohttp.web.AppRunner(fake_cal)
        await runner.setup()
        site = aiohttp.web.TCPSite(runner, "127.0.0.1", 0)
        await site.start()
        try:
            port = site._server.sockets[0].getsockname()[1]  # type: ignore[union-attr]
            self.app[self.app_anchors_key()].calendars = (f"http://127.0.0.1:{port}",)

            jpg = make_jpeg_bytes((12, 34, 56))
            sha = hashlib.sha256(jpg).hexdigest()
            form = aiohttp.FormData()
            form.add_field("file", jpg, filename="ots.jpg", content_type="image/jpeg")
            form.add_field("sha256", sha)
            form.add_field("source", "web-camera")
            form.add_field("auto_anchor", "1")
            resp = await self.client.post("/api/photos", data=form)
            self.assertEqual(resp.status, 200)
            payload = await resp.json()
            self.assertEqual(payload["anchor"]["status"], "PENDING")
            self.assertEqual(payload["anchor"]["proof_url"], f"/api/proofs/{sha}.ots")

            proof_resp = await self.client.get(payload["anchor"]["proof_url"])
            self.assertEqual(proof_resp.status, 200)
            proof_bytes = await proof_resp.read()
            self.assertTrue(proof_bytes.startswith(b"\x00OpenTimestamps\x00\x00Proof\x00"))
            self.assertIn(bytes.fromhex(sha), proof_bytes)
            self.assertTrue(proof_bytes.endswith(fake_commitment))
        finally:
            await runner.cleanup()

    def app_anchors_key(self) -> aiohttp.web.AppKey:
        from server.app import ANCHORS_KEY
        return ANCHORS_KEY


if __name__ == "__main__":
    unittest.main()
