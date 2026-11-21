"""HTTP + WebSocket server combining Nostr relay, photo media store, Android Camera bridge, and OpenTimestamps."""
from __future__ import annotations

import hashlib
import io
import os
from pathlib import Path
import re
import socket
import time
from typing import Any

from aiohttp import web
from PIL import Image

from .anchoring import AnchorManager
from .nostr_relay import NostrRelay
from .storage import SocialStore

STORE_KEY = web.AppKey("store", SocialStore)
RELAY_KEY = web.AppKey("relay", NostrRelay)
ANCHORS_KEY = web.AppKey("anchors", AnchorManager)

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
MAX_UPLOAD_BYTES = 20 * 1024 * 1024
MIME_TO_EXT = {
    "image/jpeg": ("jpg", "JPEG"),
    "image/png": ("png", "PNG"),
    "image/webp": ("webp", "WEBP"),
}
PIL_TO_MIME = {"JPEG": ("jpg", "image/jpeg"), "PNG": ("png", "image/png"), "WEBP": ("webp", "image/webp")}


def _lan_ips() -> list[str]:
    ips: list[str] = []
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.connect(("8.8.8.8", 80))
            ip = sock.getsockname()[0]
            if ip and not ip.startswith("127."):
                ips.append(ip)
        finally:
            sock.close()
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip and not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    return ips


@web.middleware
async def cors_middleware(request: web.Request, handler: Any) -> web.StreamResponse:
    if request.method == "OPTIONS":
        resp = web.Response(status=204)
    else:
        resp = await handler(request)
    if request.path.startswith("/api/"):
        resp.headers["Access-Control-Allow-Origin"] = "*"
        resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
    return resp


def _photo_payload(row: dict[str, Any], anchor: dict[str, Any] | None) -> dict[str, Any]:
    sha = row["sha256"]
    return {
        "sha256": sha,
        "url": f"/api/media/{sha}",
        "mime": row["mime"],
        "size_bytes": row["size_bytes"],
        "width": row["width"],
        "height": row["height"],
        "source": row["source"],
        "uploader_pubkey": row["uploader_pubkey"],
        "title": row["title"],
        "description": row["description"],
        "board": row["board"],
        "captured_at": row["captured_at"],
        "created_at": row["created_at"],
        "inbox_acked": bool(row["inbox_acked"]),
        "published_event_id": row["published_event_id"],
        "anchor": anchor
        or {
            "sha256": sha,
            "status": row.get("anchor_status") or "UNANCHORED",
            "network": row.get("anchor_network") or "mainnet",
            "proof_url": f"/api/proofs/{sha}.ots" if row.get("proof_file") else None,
            "block_height": row.get("block_height"),
            "block_time": row.get("block_time"),
            "diagnostics": [],
        },
    }


def _enrich_anchor(anchor: dict[str, Any] | None) -> dict[str, Any] | None:
    if not anchor:
        return None
    sha = anchor["sha256"]
    return {
        **anchor,
        "proof_url": f"/api/proofs/{sha}.ots" if anchor.get("proof_file") else None,
    }


def create_app(
    *,
    data_dir: Path | None = None,
    dist_dir: Path | None = None,
    network: str = "mainnet",
    bitcoin_conf: str | None = None,
    calendars: tuple[str, ...] | None = None,
    auto_confirm_regtest: bool | None = None,
) -> web.Application:
    root = Path(__file__).resolve().parent.parent
    data_root = data_dir or Path(os.environ.get("IXH26_DATA_DIR", root / "data"))
    web_dist = dist_dir or Path(os.environ.get("IXH26_WEB_DIST", root / "web" / "dist"))

    store = SocialStore(data_root)
    relay = NostrRelay(store)
    anchors = AnchorManager(store, network=network, bitcoin_conf=bitcoin_conf,
                            calendars=calendars, auto_confirm_regtest=auto_confirm_regtest)

    app = web.Application(middlewares=[cors_middleware], client_max_size=MAX_UPLOAD_BYTES + 1024 * 1024)
    app[STORE_KEY] = store
    app[RELAY_KEY] = relay
    app[ANCHORS_KEY] = anchors

    async def on_cleanup(_app: web.Application) -> None:
        store.close()

    app.on_cleanup.append(on_cleanup)

    async def info(request: web.Request) -> web.Response:
        port = request.url.port or (443 if request.scheme == "https" else 80)
        lan = [f"http://{ip}:{port}" for ip in _lan_ips()]
        return web.json_response(
            {
                "ok": True,
                "service": "photoanchor-nostr-web",
                "network": anchors.network,
                "settlement_package": anchors.has_settlement_pkg,
                "lan_urls": lan,
                "host": request.host,
            }
        )

    async def upload_photo(request: web.Request) -> web.Response:
        reader = await request.multipart()
        file_bytes: bytes | None = None
        fields: dict[str, str] = {}
        while True:
            part = await reader.next()
            if part is None:
                break
            if part.name == "file":
                chunks = []
                total = 0
                while True:
                    chunk = await part.read_chunk(65536)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > MAX_UPLOAD_BYTES:
                        raise web.HTTPRequestEntityTooLarge(max_size=MAX_UPLOAD_BYTES, actual_size=total)
                    chunks.append(chunk)
                file_bytes = b"".join(chunks)
            elif part.name:
                fields[part.name] = (await part.text()).strip()

        if not file_bytes:
            raise web.HTTPBadRequest(text="Missing image file.")

        sha256 = hashlib.sha256(file_bytes).hexdigest()
        declared = fields.get("sha256", "").lower()
        if declared and declared != sha256:
            raise web.HTTPBadRequest(text="Uploaded bytes do not match declared SHA-256.")

        try:
            with Image.open(io.BytesIO(file_bytes)) as img:
                fmt = (img.format or "").upper()
                width, height = img.size
                img.verify()
        except Exception:
            raise web.HTTPBadRequest(text="File must be a valid JPEG, PNG, or WebP image.")

        if fmt not in PIL_TO_MIME:
            raise web.HTTPBadRequest(text="Only JPEG, PNG, and WebP images are supported.")
        ext, mime = PIL_TO_MIME[fmt]

        uploader_pubkey = fields.get("pubkey", "").lower() or None
        if uploader_pubkey and not re.match(r"^[0-9a-f]{64}$", uploader_pubkey):
            raise web.HTTPBadRequest(text="Invalid pubkey format.")

        source = fields.get("source") or "upload"
        if source not in ("android-photoanchor", "web-camera", "upload"):
            source = "upload"

        try:
            captured_at = int(fields.get("captured_at") or time.time())
        except ValueError:
            captured_at = int(time.time())

        # Persist original bytes verbatim so SHA-256 and .ots proof stay exact.
        media_path = store.media_dir / f"{sha256}.{ext}"
        if not media_path.exists():
            tmp = store.media_dir / f".tmp-{sha256}"
            tmp.write_bytes(file_bytes)
            tmp.replace(media_path)

        photo_row = store.save_photo(
            sha256=sha256,
            ext=ext,
            mime=mime,
            size_bytes=len(file_bytes),
            width=width,
            height=height,
            source=source,
            uploader_pubkey=uploader_pubkey,
            title=fields.get("title", "")[:120],
            description=fields.get("description", "")[:1000],
            board=fields.get("board", "")[:60],
            captured_at=captured_at,
        )

        auto_anchor = fields.get("auto_anchor", "1") != "0"
        if auto_anchor:
            anchor = await anchors.stamp(sha256, media_path)
        else:
            anchor = store.get_anchor(sha256) or store.upsert_anchor(
                sha256=sha256,
                status="UNANCHORED",
                network=anchors.network,
                proof_file=None,
                block_height=None,
                block_time=None,
                diagnostics=[{"code": "not_anchored", "message": "Photo stored without OpenTimestamps anchoring yet."}],
            )

        return web.json_response(_photo_payload(photo_row, _enrich_anchor(anchor)))

    async def get_media(request: web.Request) -> web.StreamResponse:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        row = store.get_photo(sha)
        if not row:
            raise web.HTTPNotFound(text="Photo not found.")
        path = store.media_dir / f"{sha}.{row['ext']}"
        if not path.is_file():
            raise web.HTTPNotFound(text="Media file missing.")
        return web.FileResponse(
            path,
            headers={
                "Content-Type": row["mime"],
                "ETag": f'"{sha}"',
                "Cache-Control": "public, max-age=31536000, immutable",
            },
        )

    async def camera_inbox(request: web.Request) -> web.Response:
        pubkey = request.query.get("pubkey", "").lower() or None
        rows = store.get_inbox(pubkey=pubkey)
        items = [_photo_payload(r, _enrich_anchor(store.get_anchor(r["sha256"]))) for r in rows]
        return web.json_response({"items": items})

    async def ack_inbox(request: web.Request) -> web.Response:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        event_id = None
        try:
            body = await request.json()
            if isinstance(body, dict):
                event_id = body.get("event_id")
        except Exception:
            pass
        ok = store.ack_inbox(sha, event_id=event_id)
        if not ok:
            raise web.HTTPNotFound(text="Photo not found.")
        return web.json_response({"ok": True})

    async def anchor_stamp(request: web.Request) -> web.Response:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        row = store.get_photo(sha)
        if not row:
            raise web.HTTPNotFound(text="Photo not found.")
        path = store.media_dir / f"{sha}.{row['ext']}"
        anchor = await anchors.stamp(sha, path)
        return web.json_response(_enrich_anchor(anchor))

    async def anchor_upgrade(request: web.Request) -> web.Response:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        row = store.get_photo(sha)
        if not row:
            raise web.HTTPNotFound(text="Photo not found.")
        path = store.media_dir / f"{sha}.{row['ext']}"
        anchor = await anchors.upgrade_and_verify(sha, path)
        return web.json_response(_enrich_anchor(anchor))

    async def anchor_status(request: web.Request) -> web.Response:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        anchor = store.get_anchor(sha)
        if not anchor:
            return web.json_response(
                {
                    "sha256": sha,
                    "status": "UNANCHORED",
                    "network": anchors.network,
                    "proof_url": None,
                    "block_height": None,
                    "block_time": None,
                    "diagnostics": [],
                }
            )
        return web.json_response(_enrich_anchor(anchor))

    async def get_proof(request: web.Request) -> web.StreamResponse:
        sha = request.match_info["sha256"].lower()
        if not SHA256_RE.match(sha):
            raise web.HTTPBadRequest(text="Invalid SHA-256.")
        proof_path = anchors.proof_path(sha)
        if not proof_path.is_file() or proof_path.is_symlink():
            raise web.HTTPNotFound(text="OTS proof not found.")
        return web.FileResponse(
            proof_path,
            headers={
                "Content-Type": "application/octet-stream",
                "Content-Disposition": f'attachment; filename="{sha[:16]}.ots"',
            },
        )

    app.router.add_get("/nostr", relay.handle_ws)
    app.router.add_get("/api/info", info)
    app.router.add_post("/api/photos", upload_photo)
    app.router.add_get("/api/media/{sha256}", get_media)
    app.router.add_get("/api/camera/inbox", camera_inbox)
    app.router.add_post("/api/camera/inbox/{sha256}/ack", ack_inbox)
    app.router.add_post("/api/anchor/{sha256}", anchor_stamp)
    app.router.add_post("/api/anchor/{sha256}/upgrade", anchor_upgrade)
    app.router.add_get("/api/anchor/{sha256}", anchor_status)
    app.router.add_get("/api/proofs/{sha256}.ots", get_proof)

    async def verify_upload(request: web.Request) -> web.Response:
        reader = await request.multipart()
        fields: dict[str, Any] = {}
        async for part in reader:
            if part.name not in ("file", "proof", "expected_sha256", "sha256"):
                raise web.HTTPBadRequest(text="Unsupported verification field.")
            key = "expected_sha256" if part.name == "sha256" else part.name
            if key in fields:
                raise web.HTTPBadRequest(text="Duplicate verification field.")
            limit = MAX_UPLOAD_BYTES if key == "file" else 1024 * 1024 if key == "proof" else 64
            data = bytearray()
            while chunk := await part.read_chunk(65536):
                if len(data) + len(chunk) > limit:
                    raise web.HTTPRequestEntityTooLarge(max_size=limit, actual_size=len(data) + len(chunk))
                data.extend(chunk)
            fields[key] = bytes(data)
        if not fields.get("file"):
            raise web.HTTPBadRequest(text="Missing file to verify.")
        expected = fields.get("expected_sha256")
        if expected is not None:
            try:
                expected = expected.decode("ascii")
            except UnicodeError:
                raise web.HTTPBadRequest(text="Invalid SHA-256.") from None
            if not SHA256_RE.fullmatch(expected):
                raise web.HTTPBadRequest(text="Invalid SHA-256.")
        result = await anchors.verify_bytes(fields["file"], expected_sha256=expected,
                                             proof_bytes=fields.get("proof"))
        return web.json_response(result)

    app.router.add_post("/api/verify", verify_upload)

    if web_dist.is_dir():
        assets_dir = web_dist / "assets"
        if assets_dir.is_dir():
            app.router.add_static("/assets", assets_dir)
        brand_dir = web_dist / "brand"
        if brand_dir.is_dir():
            app.router.add_static("/brand", brand_dir)
        lab_assets = web_dist / "lab-assets"
        if lab_assets.is_dir():
            app.router.add_static("/lab-assets", lab_assets)

        async def spa_fallback(request: web.Request) -> web.StreamResponse:
            if request.path.startswith("/api/") or request.path == "/nostr":
                raise web.HTTPNotFound()
            rel = request.path.lstrip("/")
            if rel:
                candidate = (web_dist / rel).resolve()
                if str(candidate).startswith(str(web_dist.resolve())) and candidate.is_file():
                    return web.FileResponse(candidate)
            index = web_dist / "index.html"
            if index.is_file():
                return web.FileResponse(index)
            raise web.HTTPNotFound()

        app.router.add_get("/{tail:.*}", spa_fallback)

    return app
