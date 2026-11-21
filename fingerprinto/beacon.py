"""Experimental external optical token. A valid token is NOT physical presence.

Independent of receipt v3 and its reducer. Run ``python -m fingerprinto.beacon
--help`` for the explicit offline CLI. No network, clock or trust discovery.
"""

import argparse
from io import BytesIO
import json
from pathlib import Path
import warnings

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.exceptions import InvalidSignature

from .cbor import encode, decode
from .core import digest, domain
from .verification import load_installation_key, validate_signature_encoding

PROFILE = "fingerprinto/beacon/1"
DECODER = "fingerprinto/optical-grid64-pillow13/1"
SLOT_SECONDS = 10
PACKET_LIMIT = 400
MAGIC = b"FPB1"
LIMITATIONS = ["splicing_not_detected", "relay_not_detected", "recorded_code_replay_not_detected",
               "no_sensor_identity", "no_physical_presence_proof", "no_exposure_time_proof"]


def integer(value, minimum=0, maximum=2**53-1):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Invalid bounded integer")
    return value


def session_binding(challenge, installation_id):
    """Both values come from the verifier's session, not the submitted image."""
    if any(type(v) is not bytes or len(v) != 32 for v in (challenge, installation_id)):
        raise ValueError("Expected 32-byte challenge and installation identity")
    return domain("beacon-session/1", challenge, installation_id)


def spki(key):
    return key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


def issue(key, *, beacon_time, challenge, installation_id):
    """Call on the EXTERNAL beacon only, using its trusted clock/release process."""
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("Beacon requires P-256")
    payload = [PROFILE, digest(spki(key.public_key())), integer(beacon_time) // SLOT_SECONDS,
               session_binding(challenge, installation_id)]
    root = domain("beacon-code/1", payload)
    signature = key.sign(root, ec.ECDSA(hashes.SHA256()))
    return encode([payload, signature])


def parse(packet):
    if type(packet) is not bytes or not 1 <= len(packet) <= PACKET_LIMIT:
        raise ValueError("Invalid optical packet size")
    fields = decode(packet)
    if (type(fields) is not list or len(fields) != 2 or type(fields[0]) is not list
            or len(fields[0]) != 4 or fields[0][0] != PROFILE):
        raise ValueError("Invalid beacon schema")
    payload, signature = fields
    if any(type(payload[i]) is not bytes or len(payload[i]) != 32 for i in (1, 3)):
        raise ValueError("Invalid identity/binding")
    integer(payload[2], maximum=(2**53-1)//SLOT_SECONDS)
    validate_signature_encoding(signature)
    if encode(fields) != packet:
        raise ValueError("Noncanonical beacon packet")
    return payload, signature


def verify_token(packet, *, trusted_keys, challenge, installation_id, now, max_age=120,
                 anchor_upper_bound=None):
    """Verify software token only. Times/keys/session must be verifier-owned.

    anchor_upper_bound is an independently justified time bound, NOT an OTS
    proof verifier or an arbitrary block timestamp accepted as exact UTC.
    """
    integer(now); integer(max_age, maximum=86400)
    binding = session_binding(challenge, installation_id)
    if anchor_upper_bound is not None:
        integer(anchor_upper_bound)
        if anchor_upper_bound > now:
            raise ValueError("Anchor upper bound is in the future")
    report = {"schema": PROFILE + "/report", "status": "fail", "check": "signed_beacon_token",
              "evidenceClass": "S", "claimedPhysicalSource": "external_beacon",
              "trustRoot": "verifier_pinned_beacon_spki", "physicalOriginVerified": False,
              "limitations": list(LIMITATIONS), "conditionalContentWindow": None}
    try:
        payload, signature = parse(packet)
        report["beaconId"] = payload[1].hex()
        public = trusted_keys.get(payload[1])
        if public is None:
            report["reason"] = "unregistered_beacon"
            return report
        key = load_installation_key(public)
        if digest(public) != payload[1]:
            raise ValueError("Pinned key/identity disagreement")
        key.verify(signature, domain("beacon-code/1", payload), ec.ECDSA(hashes.SHA256()))
        if payload[3] != binding:
            report["reason"] = "wrong_session"
            return report
        start = payload[2] * SLOT_SECONDS
        if start > now or now - start > max_age:
            report["reason"] = "future_or_stale_slot"
            return report
        if anchor_upper_bound is not None and anchor_upper_bound < start:
            report["reason"] = "slot_after_anchor_bound"
            return report
        report.update(status="pass", reason="signature_session_and_slot_valid", slot=payload[2],
                      packetHash=digest(packet).hex())
        if anchor_upper_bound is not None:
            report["conditionalContentWindow"] = {
                "notBefore": start, "notAfter": anchor_upper_bound,
                "assumptions": ["honest_beacon_clock", "no_early_code_release", "uncompromised_key",
                                "independently_verified_anchor_time_bound"],
                "appliesTo": "token-bearing_content_not_physical_capture"}
    except (ValueError, TypeError, InvalidSignature):
        report["reason"] = "invalid_token_or_signature"
    return report


def pillow():
    from PIL import Image, __version__
    if __version__ != "13.0.0":
        raise ValueError("Unsupported decoder version")
    return Image


def render(packet, *, scale=8):
    """Fixed-grid baseline, NOT QR: no error correction, localisation or rotation."""
    parse(packet)
    integer(scale, 4, 16)
    image = pillow().new("L", (72, 72), 255)
    data = MAGIC + len(packet).to_bytes(2, "big") + packet
    data += bytes(512 - len(data))
    for bit in range(4096):
        image.putpixel((4 + bit % 64, 4 + bit // 64), 0 if data[bit//8] & (128 >> (bit%8)) else 255)
    image = image.resize((72*scale, 72*scale), resample=pillow().Resampling.NEAREST)
    out = BytesIO(); image.save(out, format="PNG")
    return out.getvalue()


def verify_image(asset, *, roi=None, **options):
    """Decode only from original image bytes, never from a supplied token claim.

    roi=(x,y,width,height) is an operator-selected axis-aligned square including
    the four-cell quiet zone. The crop is sampled, never written into the asset.
    """
    if type(asset) is not bytes or not 1 <= len(asset) <= 16*1024*1024:
        raise ValueError("Invalid image size")
    Image = pillow()
    base = {"schema": PROFILE + "/report", "status": "inconclusive", "evidenceClass": "S",
            "decoder": DECODER, "assetHash": digest(asset).hex(), "physicalOriginVerified": False,
            "claimedPhysicalSource": "external_beacon", "limitations": list(LIMITATIONS)}
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(asset), formats=["PNG", "JPEG"]) as image:
                if image.width*image.height > 16*1024*1024 or image.mode not in ("L", "RGB"):
                    raise ValueError("Unsupported image geometry/mode")
                if image.getexif().get(274, 1) != 1 or image.info.get("icc_profile"):
                    raise ValueError("Unsupported orientation/colour profile")
                x,y,w,h = (0,0,*image.size) if roi is None else roi
                for value in (x,y,w,h): integer(value)
                if w != h or not 288 <= w <= 2048 or x+w > image.width or y+h > image.height:
                    raise ValueError("Unsupported optical region")
                image = image.crop((x,y,x+w,y+h)).convert("L")
                data = bytearray(512)
                for bit in range(4096):
                    value = image.getpixel((int((4+bit%64+.5)*w/72), int((4+bit//64+.5)*h/72)))
                    if 80 < value < 175: raise ValueError("Low optical contrast")
                    if value <= 80: data[bit//8] |= 128 >> (bit%8)
                if bytes(data[:4]) != MAGIC:
                    return dict(base, status="unavailable", reason="code_not_found")
                length = int.from_bytes(data[4:6], "big")
                if not 1 <= length <= PACKET_LIMIT or any(data[6+length:]):
                    raise ValueError("Invalid optical framing")
                result = verify_token(bytes(data[6:6+length]), **options)
                return dict(result, decoder=DECODER, assetHash=base["assetHash"], roi=[x,y,w,h])
    except (ValueError, OSError, Image.DecompressionBombWarning, Image.DecompressionBombError):
        return dict(base, reason="unsupported_or_undecodable_image")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for command in ("issue", "verify"):
        p = sub.add_parser(command)
        for arg in ("challenge", "installation-id", "time"):
            p.add_argument("--"+arg, required=True, type=int if arg == "time" else str)
        if command == "issue":
            p.add_argument("--private-key", required=True, help="External beacon's PEM key; never the phone key")
            p.add_argument("--out", required=True)
        else:
            p.add_argument("image")
            p.add_argument("--public-key", required=True, help="Verifier-pinned DER SPKI, outside submitted bundle")
            p.add_argument("--roi", nargs=4, type=int)
            p.add_argument("--max-age", type=int, default=120)
    args = parser.parse_args()
    try:
        challenge, installation = bytes.fromhex(args.challenge), bytes.fromhex(args.installation_id)
        if args.command == "issue":
            key = serialization.load_pem_private_key(Path(args.private_key).read_bytes(), password=None)
            image = render(issue(key, beacon_time=args.time, challenge=challenge, installation_id=installation))
            with Path(args.out).open("xb") as out: out.write(image)
            result = {"status": "created", "physicalOriginVerified": False}
        else:
            public = Path(args.public_key).read_bytes()
            load_installation_key(public)
            with Path(args.image).open("rb") as image:
                asset = image.read(16*1024*1024+1)
            result = verify_image(asset, roi=args.roi, trusted_keys={digest(public):public}, challenge=challenge,
                                  installation_id=installation, now=args.time, max_age=args.max_age)
        print(json.dumps(result, allow_nan=False))
        return 0 if result["status"] in ("pass", "created") else 3 if result["status"] == "fail" else 2
    except (ValueError, TypeError, OSError, ImportError):
        print(json.dumps({"status":"error", "reason":"invalid_input_or_dependency", "physicalOriginVerified":False}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
