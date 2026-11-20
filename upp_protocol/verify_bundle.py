"""Independent bundle verifier: signed event + original JPEG + standard OTS."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
from typing import Any
import zipfile
import zlib

from settlement import OTSConfig, SettlementService

from .protocol import MAX_MEDIA_BYTES, MAX_POST_BYTES, ProtocolError, build_payload, verify_photo_bytes

_LIMITS = {"photo.jpg": MAX_MEDIA_BYTES, "event.json": MAX_POST_BYTES,
           "payload.bin": 1024, "proof.ots": 1024 * 1024}
_EXITS = {"CONFIRMED": 0, "PENDING": 2, "INVALID": 3, "UNVERIFIABLE": 4, "ERROR": 1}


def verify_bundle(bundle_path: str | Path, config: OTSConfig | None = None) -> dict[str, Any]:
    """Read expected ZIP members in memory; never extract paths or trust a receipt.

    The returned settlement result comes from a fresh verification against the
    configured node. A valid pending proof needs no Bitcoin RPC connection.
    """
    try:
        with zipfile.ZipFile(bundle_path) as archive:
            names = archive.namelist()
            if any(names.count(name) != 1 for name in _LIMITS):
                raise ProtocolError("invalid_bundle", "The bundle must contain each required member once.")
            members = {}
            for name, limit in _LIMITS.items():
                info = archive.getinfo(name)
                if not 0 < info.file_size <= limit:
                    raise ProtocolError("invalid_bundle", "A required bundle member has an invalid size.")
                if info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                    raise ProtocolError("invalid_bundle", "The ZIP compression method is unsupported.")
                # A ZIP header is untrusted input. Bound decompression even when
                # it declares a small size, and check the actual decoded bytes.
                with archive.open(info) as member:
                    data = member.read(limit + 1)
                if len(data) != info.file_size or len(data) > limit:
                    raise ProtocolError("invalid_bundle", "A required bundle member has an invalid size.")
                members[name] = data
    except (zipfile.BadZipFile, zlib.error, RuntimeError, NotImplementedError, EOFError):
        raise ProtocolError("invalid_bundle", "The ZIP bundle is malformed or unsupported.") from None
    try:
        event = json.loads(members["event.json"])
    except (ValueError, UnicodeError):
        raise ProtocolError("invalid_event", "The bundled event is not valid JSON.") from None
    photo = verify_photo_bytes(event, members["photo.jpg"])
    expected_payload = build_payload(event)
    if expected_payload != members["payload.bin"]:
        raise ProtocolError("payload_mismatch", "The bundled payload does not match the verified signed event.")
    with tempfile.TemporaryDirectory(prefix="photoanchor-verify-") as directory:
        payload = Path(directory) / "payload.bin"
        proof = Path(directory) / "proof.ots"
        payload.write_bytes(expected_payload)
        proof.write_bytes(members["proof.ots"])
        settlement = SettlementService(config).verify_file(payload, proof).to_dict()
    # The temporary path no longer exists; retain the portable member name.
    settlement["proof_file"] = "proof.ots"
    return {"event_id": photo.event_id, "pubkey": photo.pubkey,
            "signature_valid": True, "integrity_valid": True,
            "photo_sha256": photo.photo_sha256,
            "payload_sha256": hashlib.sha256(expected_payload).hexdigest(),
            "settlement": settlement}


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError("Invalid command arguments; use --help for usage.")


def main(argv: list[str] | None = None) -> int:
    parser = _Parser(description="Verify a PhotoAnchor bundle independently of its HTTP receipt.")
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--network", choices=("mainnet", "regtest"), default="mainnet")
    parser.add_argument("--bitcoin-conf")
    parser.add_argument("--timeout", type=int, default=10)
    try:
        args = parser.parse_args(argv)
        result = verify_bundle(args.bundle, OTSConfig(network=args.network,
                               bitcoin_conf=args.bitcoin_conf, timeout=args.timeout))
        code = _EXITS[result["settlement"]["status"]]
    except ProtocolError as exc:
        result = {"status": "INVALID", "error": {"code": exc.code, "message": exc.message}}
        code = 3
    except (OSError, ValueError):
        result = {"status": "ERROR", "error": {"code": "local_error",
                  "message": "Could not read the bundle or configure verification."}}
        code = 1
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
