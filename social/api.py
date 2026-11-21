"""Local HTTP adapter. No relay/calendars are contacted in request publication."""
from io import BytesIO
import hashlib
import json
import re
import warnings
import zipfile

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, Response
from PIL import Image, UnidentifiedImageError
from starlette.exceptions import HTTPException
from starlette.concurrency import run_in_threadpool

from settlement import SettlementService
from upp_protocol import MAX_MEDIA_BYTES, MAX_POST_BYTES, ProtocolError, validate_photo_event
from .config import Config
from .storage import Store, canonical, publish_bytes

HEX = re.compile(r"[0-9a-f]{64}\Z")


class APIError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def jpeg_dimensions(data):
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(data)) as image:
                if image.format != "JPEG":
                    raise ValueError("not JPEG")
                size = image.size
                image.verify()
            # JPEG verify() checks framing only. Decode to reject truncated scans.
            with Image.open(BytesIO(data)) as image:
                image.load()
                if image.size != size:
                    raise ValueError("inconsistent image")
            return size
    except (OSError, ValueError, UnidentifiedImageError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        raise APIError(400, "invalid_jpeg", "A complete, decodable JPEG is required.") from None


async def bounded_body(request, limit):
    length = request.headers.get("content-length")
    if length is not None:
        try:
            if int(length) < 0:
                raise ValueError()
            if int(length) > limit:
                raise APIError(413, "request_too_large", "The request exceeds the allowed size.")
        except ValueError:
            raise APIError(400, "invalid_request", "Invalid request length.") from None
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > limit:
            raise APIError(413, "request_too_large", "The request exceeds the allowed size.")
        body.extend(chunk)
    return bytes(body)


def create_app(config=None, *, service=None):
    config = config or Config.from_env()
    store = Store(config)
    service = service or SettlementService(config.ots_config())
    app = FastAPI(title="PhoTO local MVP", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store, app.state.service, app.state.config = store, service, config

    @app.exception_handler(APIError)
    async def api_error(request, exc):
        return JSONResponse({"error": {"code": exc.code, "message": exc.message}}, status_code=exc.status)

    @app.exception_handler(ProtocolError)
    async def protocol_error(request, exc):
        return JSONResponse({"error": {"code": exc.code, "message": exc.message}}, status_code=400)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        return JSONResponse({"error": {"code": "invalid_request", "message": "Invalid request parameters."}}, status_code=400)

    @app.exception_handler(HTTPException)
    async def http_error(request, exc):
        return JSONResponse({"error": {"code": "not_found" if exc.status_code == 404 else "invalid_request",
                                       "message": "Endpoint not found." if exc.status_code == 404 else "The request is not supported."}},
                            status_code=exc.status_code)

    @app.exception_handler(Exception)
    async def operational_error(request, exc):
        return JSONResponse({"error": {"code": "operation_failed", "message": "The local operation could not be completed."}}, status_code=500)

    def require_post(event_id):
        if not HEX.fullmatch(event_id) or store.get(event_id) is None:
            raise APIError(404, "post_not_found", "The post was not found.")
        return store.snapshot(event_id)

    def require_proof(event_id, status=404):
        require_post(event_id)
        path = store.proof_path(event_id)
        if not path.is_file() or path.is_symlink():
            raise APIError(status, "proof_not_ready", "No proof is available yet.")
        return path

    @app.get("/healthz")
    def health():
        try:
            store.health()
        except Exception:
            raise APIError(503, "database_unavailable", "The database is not accessible.") from None
        return {"status": "ok"}

    @app.post("/v1/media")
    async def upload_media(request: Request):
        if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "image/jpeg":
            raise APIError(415, "unsupported_media_type", "Content-Type image/jpeg is required.")
        data = await bounded_body(request, MAX_MEDIA_BYTES)
        await run_in_threadpool(jpeg_dimensions, data)
        digest = hashlib.sha256(data).hexdigest()
        created = await run_in_threadpool(publish_bytes, store.media / (digest + ".jpg"), data)
        return JSONResponse({"sha256": digest, "url": config.public_url + "/v1/media/" + digest,
                             "mime": "image/jpeg", "size": len(data)}, status_code=201 if created else 200)

    @app.get("/v1/media/{sha256}")
    def get_media(sha256: str):
        path = store.media / (sha256 + ".jpg")
        if not HEX.fullmatch(sha256) or not path.is_file() or path.is_symlink():
            raise APIError(404, "media_not_found", "The JPEG was not found.")
        return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "public, max-age=31536000, immutable"})

    def accept_post(body):
        try:
            document = json.loads(body)
        except (ValueError, UnicodeError):
            raise APIError(400, "invalid_json", "A valid JSON request is required.") from None
        if not isinstance(document, dict) or set(document) != {"event"}:
            raise APIError(400, "invalid_request", "Expected a single signed event field.")
        event = document["event"]
        # Conflicting retries must be diagnosed before signature checks, since an
        # incompatible signature can legitimately retain the original event ID.
        if isinstance(event, dict) and isinstance(event.get("id"), str):
            existing = store.get(event["id"])
            if existing and canonical(event) != existing["event"]:
                raise APIError(409, "event_conflict", "This event ID is already registered with different content.")
        photo = validate_photo_event(event)
        expected_url = config.public_url + "/v1/media/" + photo.photo_sha256
        validate_photo_event(event, expected_media_url=expected_url)
        path = store.media / (photo.photo_sha256 + ".jpg")
        if not path.is_file() or path.is_symlink():
            raise APIError(400, "media_not_found", "Upload the referenced JPEG first.")
        data = path.read_bytes()
        validate_photo_event(event, expected_sha256=hashlib.sha256(data).hexdigest())
        if jpeg_dimensions(data) != (photo.width, photo.height):
            raise APIError(400, "photo_dimensions_mismatch", "The signed dimensions do not match the stored JPEG.")
        try:
            created = store.accept(photo.event)
        except ValueError:
            raise APIError(409, "event_conflict", "This event ID is already registered with different content.") from None
        return JSONResponse(store.snapshot(photo.event_id), status_code=202 if created else 200)

    @app.post("/v1/posts")
    async def create_post(request: Request):
        body = await bounded_body(request, MAX_POST_BYTES)
        return await run_in_threadpool(accept_post, body)

    @app.get("/v1/posts/{event_id}")
    def get_post(event_id: str):
        return require_post(event_id)

    @app.post("/v1/posts/{event_id}/verify")
    def verify(event_id: str):
        proof = require_proof(event_id)
        event = require_post(event_id)["event"]
        # The payload is rebuilt from the authenticated event, never a receipt.
        payload = store.payload_path(event)
        result = service.verify_file(payload, proof)
        store.settlement(event_id, result)
        return store.snapshot(event_id)

    @app.get("/v1/posts/{event_id}/proof")
    def proof(event_id: str):
        return Response(require_proof(event_id).read_bytes(), media_type="application/octet-stream",
                        headers={"Content-Disposition": f'attachment; filename="{event_id}.ots"'})

    @app.get("/v1/posts/{event_id}/bundle")
    def bundle(event_id: str):
        proof = require_proof(event_id, status=409)
        snapshot = require_post(event_id)
        event = snapshot["event"]
        photo = validate_photo_event(event)
        payload = store.payload_path(event)
        archive = BytesIO()
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
            bundle.write(store.media / (photo.photo_sha256 + ".jpg"), "photo.jpg")
            bundle.writestr("event.json", canonical(event).encode("utf-8"))
            bundle.writestr("payload.bin", payload.read_bytes())
            bundle.writestr("proof.ots", proof.read_bytes())
            bundle.writestr("README.txt", "PhoTO verification bundle. Receipts are untrusted metadata.\n"
                            "From a PhoTO checkout, install the adapter: python -m pip install '.[protocol]'\n"
                            "Then verify the exported bundle:\n"
                            "python -m upp_protocol.verify_bundle bundle.zip --network " + config.network +
                            " --bitcoin-conf /path/to/bitcoin.conf\n"
                            "A regtest confirmation refers only to the local development chain.\n"
                            "Author metadata does not establish scene authenticity or capture time.\n")
            if snapshot["settlement"] is not None:
                bundle.writestr("receipt.json", canonical(snapshot["settlement"]).encode("utf-8"))
        return Response(archive.getvalue(), media_type="application/zip",
                        headers={"Content-Disposition": f'attachment; filename="{event_id}.zip"'})

    return app


# Uvicorn loads one application using the configured persistent data directory.
app = create_app()
