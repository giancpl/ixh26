import hashlib
import json
from pathlib import Path

import pytest
from nostr_sdk import EventBuilder, Keys, Kind, Tag, Timestamp

from upp_protocol import (ProtocolError, build_payload, validate_photo_event,
                          validate_profile_event, verify_photo_bytes)

FIXTURES = Path(__file__).parents[2] / "contracts" / "fixtures"


def load(name):
    return json.loads((FIXTURES / name).read_text())


@pytest.mark.parametrize("vector", load("vectors.json"), ids=lambda v: v["name"])
def test_shared_vectors(vector):
    event = vector["event"]
    photo = validate_photo_event(event)
    assert photo.event_id == event["id"]
    assert photo.photo_sha256 == vector["photo_sha256"]
    payload = build_payload(event)
    assert payload == vector["payload_utf8"].encode("utf-8")
    assert hashlib.sha256(payload).hexdigest() == vector["payload_sha256"]
    assert not payload.endswith(b"\n")


def test_exact_basic_payload_and_jpeg():
    event = load("event.json")
    assert build_payload(event) == (FIXTURES / "payload.bin").read_bytes()
    assert verify_photo_bytes(event, (FIXTURES / "photo.jpg").read_bytes()).width == 64


@pytest.mark.parametrize("name,code", [("signature.json", "invalid_signature"),
                                      ("metadata.json", "invalid_event_id")])
def test_altered_signed_fields(name, code):
    with pytest.raises(ProtocolError) as exc:
        build_payload(load("negative/" + name))
    assert exc.value.code == code


def test_altered_photo():
    with pytest.raises(ProtocolError, match="hash"):
        verify_photo_bytes(load("event.json"), (FIXTURES / "negative/photo.jpg").read_bytes())


def test_local_media_contract():
    event = load("event.json")
    with pytest.raises(ProtocolError) as exc:
        validate_photo_event(event, expected_media_url="https://external.example/photo.jpg")
    assert exc.value.code == "external_media_url"


def test_profile_changes_do_not_change_old_photo_validity():
    first = validate_profile_event(load("profile.json"))
    updated = validate_profile_event(load("profile-updated.json"))
    photo = validate_photo_event(load("event.json"))
    assert first["pubkey"] == updated["pubkey"] == photo.pubkey
    assert first["content"] != updated["content"]
    assert build_payload(photo.event) == (FIXTURES / "payload.bin").read_bytes()


def sign_event(*, tags=None, kind=20, content=None):
    base = load("event.json")
    signed = (EventBuilder(Kind(kind), base["content"] if content is None else content)
              .tags([Tag.parse(t) for t in (base["tags"] if tags is None else tags)])
              .custom_created_at(Timestamp.from_secs(base["created_at"]))
              .finalize(Keys.parse("0" * 63 + "1")))
    return json.loads(signed.as_json())


@pytest.mark.parametrize("change", [
    lambda t: t + [t[0]],
    lambda t: [x for x in t if x[0] != "x"],
    lambda t: [["m", "image/png"] if x[0] == "m" else x for x in t],
    lambda t: [["title", "Different"] if x[0] == "title" else x for x in t],
    lambda t: [["imeta", *x[1:], "x " + "0" * 64] if x[0] == "imeta" else x for x in t],
    lambda t: [["imeta", *[v.replace("64x48", "0x48") for v in x[1:]]] if x[0] == "imeta" else x for x in t],
    lambda t: [["imeta", *[v for v in x[1:] if not v.startswith("dim ")]] if x[0] == "imeta" else x for x in t],
])
def test_sdk_signed_but_incompatible_photo_metadata(change):
    # Signatures are genuine: contract validation must run after cryptography.
    signed = sign_event(tags=change(load("event.json")["tags"]))
    with pytest.raises(ProtocolError) as exc:
        validate_photo_event(signed)
    assert exc.value.code == "invalid_photo_tags"


@pytest.mark.parametrize("change", [
    lambda e: dict(e, sig=e["sig"].upper()),
    lambda e: dict(e, created_at=True),
    lambda e: dict(e, tags=[["m", 1]]),
    lambda e: dict(e, unexpected="field"),
    lambda e: {k: v for k, v in e.items() if k != "pubkey"},
])
def test_malformed_envelope(change):
    with pytest.raises(ProtocolError) as exc:
        validate_photo_event(change(load("event.json")))
    assert exc.value.code == "invalid_event"


def test_photo_requires_kind_20_even_with_valid_signature():
    with pytest.raises(ProtocolError) as exc:
        validate_photo_event(sign_event(kind=1))
    assert exc.value.code == "unsupported_kind"
