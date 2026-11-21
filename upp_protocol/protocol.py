"""Strict Nostr envelope and NIP-68 media contract; cryptography uses the SDK."""
from dataclasses import dataclass
import hashlib
import json
import re
from typing import Any, Mapping

from nostr_sdk import Event

MAX_MEDIA_BYTES = 20 * 1024 * 1024
MAX_POST_BYTES = 16 * 1024
_HEX64 = re.compile(r"[0-9a-f]{64}\Z")
_HEX128 = re.compile(r"[0-9a-f]{128}\Z")
_DIM = re.compile(r"([1-9][0-9]{0,9})x([1-9][0-9]{0,9})\Z")
_FIELDS = {"id", "pubkey", "created_at", "kind", "tags", "content", "sig"}


class ProtocolError(ValueError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True)
class PhotoEvent:
    event_id: str
    pubkey: str
    signature: str
    photo_sha256: str
    photo_url: str
    mime: str
    width: int
    height: int
    event: dict[str, Any]


def _signed_event(event: Mapping[str, Any], kind: int | None = None) -> dict[str, Any]:
    if not isinstance(event, Mapping) or set(event) != _FIELDS:
        raise ProtocolError("invalid_event", "Expected a standard signed Nostr event.")
    result = dict(event)
    if not all(isinstance(result[k], str) and regex.fullmatch(result[k])
               for k, regex in (("id", _HEX64), ("pubkey", _HEX64), ("sig", _HEX128))):
        raise ProtocolError("invalid_event", "Event identifiers and signature must be lowercase hex.")
    if (type(result["created_at"]) is not int or result["created_at"] < 0
            or type(result["kind"]) is not int or not 0 <= result["kind"] <= 65535
            or not isinstance(result["content"], str)
            or not isinstance(result["tags"], list)
            or any(not isinstance(t, list) or not t or any(not isinstance(v, str) for v in t)
                   for t in result["tags"])):
        raise ProtocolError("invalid_event", "Malformed Nostr event fields.")
    if kind is not None and result["kind"] != kind:
        raise ProtocolError("unsupported_kind", f"Expected Nostr kind {kind}.")
    try:
        parsed = Event.from_json(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        valid_id = parsed.verify_id()
        valid_signature = parsed.verify_signature()
    except Exception:
        raise ProtocolError("invalid_event", "The SDK could not parse this Nostr event.") from None
    if not valid_id:
        raise ProtocolError("invalid_event_id", "The event ID does not match its signed fields.")
    if not valid_signature:
        raise ProtocolError("invalid_signature", "The Nostr signature is invalid.")
    return result


def validate_photo_event(event: Mapping[str, Any], *, expected_media_url: str | None = None,
                         expected_sha256: str | None = None) -> PhotoEvent:
    """Validate the signature and the single JPEG reference, without fetching URLs."""
    event = _signed_event(event, 20)
    tags = event["tags"]
    evidence = [t for t in tags if t[0] == "photo-evidence"]
    if evidence and (len(evidence) != 1 or len(evidence[0]) != 3
                     or evidence[0][1] != "fingerprinto/evidence-v3" or not _HEX64.fullmatch(evidence[0][2])):
        raise ProtocolError("invalid_evidence_tag", "Expected one evidence-v3 commitment tag.")

    def single(name: str) -> list[str]:
        found = [t for t in tags if t[0] == name]
        if len(found) != 1:
            raise ProtocolError("invalid_photo_tags", f"Expected exactly one {name} tag.")
        return found[0]

    title, mime, digest, imeta = (single(k) for k in ("title", "m", "x", "imeta"))
    if title != ["title", event["content"] or "Photo"]:
        raise ProtocolError("invalid_photo_tags", "The title must equal the caption or Photo.")
    if mime != ["m", "image/jpeg"] or len(digest) != 2 or not _HEX64.fullmatch(digest[1]):
        raise ProtocolError("invalid_photo_tags", "A JPEG MIME and SHA-256 hash are required.")
    entries: dict[str, str] = {}
    for item in imeta[1:]:
        key, separator, value = item.partition(" ")
        if not separator or key in entries or key not in {"url", "m", "x", "dim"}:
            raise ProtocolError("invalid_photo_tags", "Invalid or duplicate imeta field.")
        entries[key] = value
    if set(entries) != {"url", "m", "x", "dim"} or not entries["url"]:
        raise ProtocolError("invalid_photo_tags", "The imeta fields url, m, x and dim are required.")
    dim = _DIM.fullmatch(entries["dim"])
    if entries["m"] != "image/jpeg" or entries["x"] != digest[1] or not dim:
        raise ProtocolError("invalid_photo_tags", "Inconsistent JPEG metadata.")
    width, height = int(dim[1]), int(dim[2])
    if width > 2**31 - 1 or height > 2**31 - 1:
        raise ProtocolError("invalid_photo_tags", "Photo dimensions must be positive 32-bit integers.")
    if expected_media_url is not None and entries["url"] != expected_media_url:
        raise ProtocolError("external_media_url", "The photo URL must reference this service's media archive.")
    if expected_sha256 is not None and digest[1] != expected_sha256:
        raise ProtocolError("photo_hash_mismatch", "The photo hash does not match the stored JPEG.")
    return PhotoEvent(event["id"], event["pubkey"], event["sig"], digest[1], entries["url"],
                      "image/jpeg", width, height, event)


def build_payload(event: Mapping[str, Any]) -> bytes:
    """Revalidate the signed photo and encode the exact OTS application payload."""
    photo = validate_photo_event(event)
    return json.dumps(["upp-nostr-photo/1", photo.event_id, photo.pubkey, photo.signature],
                      ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def verify_photo_bytes(event: Mapping[str, Any], photo: bytes) -> PhotoEvent:
    return validate_photo_event(event, expected_sha256=hashlib.sha256(photo).hexdigest())


def validate_profile_event(event: Mapping[str, Any]) -> dict[str, Any]:
    result = _signed_event(event, 0)
    try:
        metadata = json.loads(result["content"])
    except (ValueError, TypeError):
        raise ProtocolError("invalid_profile", "Profile content must be a JSON object.") from None
    if not isinstance(metadata, dict):
        raise ProtocolError("invalid_profile", "Profile content must be a JSON object.")
    return result


def evidence_commitment(event):
    photo = validate_photo_event(event)
    return next((t[2] for t in photo.event["tags"] if t[0] == "photo-evidence"), None)
