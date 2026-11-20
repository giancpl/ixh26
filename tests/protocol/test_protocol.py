import hashlib
import json
from pathlib import Path

import pytest

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
