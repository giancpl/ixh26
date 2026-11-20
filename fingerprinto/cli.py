"""JSON CLI for local evidence transport and independent verification."""

import argparse
import json
from pathlib import Path
import stat
from typing import Sequence

from .bundle import EvidenceBundle, LIMITS, export_bundle, import_bundle
from .core import Receipt


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def _read(path: str | None, limit: int) -> bytes | None:
    if path is None:
        return None
    with Path(path).open("rb") as stream:
        import os
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
            raise ValueError("Input must be a regular file within the profile limit")
        data = stream.read(limit + 1)
        if len(data) > limit:
            raise ValueError("Input exceeds the profile limit")
        return data


def _verification_exit(result) -> int:
    checks = (result.profile, result.origin, result.installation_signature,
              result.enrollment, result.anchor)
    if any(check.status in ("invalid", "rejected", "failed") for check in checks):
        return 3
    if any(check.status in ("indeterminate", "absent", "pending") for check in checks):
        return 2
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = _Parser(description="FingerprinTO v1 evidence bundles and verification")
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("pack", help="Create a bundle from existing binary evidence")
    pack.add_argument("--receipt", required=True)
    pack.add_argument("--asset", required=True)
    pack.add_argument("--credential")
    pack.add_argument("--ots-proof")
    pack.add_argument("--anchor-locator")
    pack.add_argument("--out", required=True)
    capture = commands.add_parser("check-capture", help="Check an unsigned Android capture diagnostic export")
    capture.add_argument("archive")
    for name in ("inspect", "verify", "verify-ots"):
        command = commands.add_parser(name)
        command.add_argument("bundle")
        if name == "verify-ots":
            command.add_argument("--network", required=True, choices=("mainnet", "regtest"))
            command.add_argument("--bitcoin-conf")
            command.add_argument("--timeout", type=int, default=10)
    try:
        args = parser.parse_args(argv)
        if args.command == "check-capture":
            from .capture import check_capture
            output = check_capture(args.archive)
            code = 3 if output["file_binding"]["status"] == "invalid" else 2
        elif args.command == "pack":
            bundle = EvidenceBundle(
                _read(args.receipt, LIMITS["receipt.cbor"]),
                _read(args.asset, LIMITS["asset.bin"]),
                _read(args.credential, LIMITS["credential.bin"]),
                _read(args.ots_proof, LIMITS["commitment.bin.ots"]),
                _read(args.anchor_locator, LIMITS["anchor.json"]))
            path = export_bundle(bundle, args.out)
            output = {"operation": "pack", "status": "stored", "bundle": str(path.absolute()),
                      "trust_evaluated": False}
            code = 0
        else:
            bundle = import_bundle(args.bundle)
            if args.command == "inspect":
                receipt = Receipt.from_bytes(bundle.receipt)
                files = bundle.files()
                output = {"operation": "inspect", "status": "readable", "receipt_version": 1,
                          "placement": receipt.placement, "files": {k: len(v) for k, v in files.items()},
                          "commitment": files["commitment.bin"].hex(), "trust_evaluated": False}
                code = 0
            else:
                if args.command == "verify":
                    from .bundle import verify_bundle
                    result = verify_bundle(bundle)
                else:
                    from .bundle import verify_bundle_ots
                    from settlement import OTSConfig
                    result = verify_bundle_ots(bundle, config=OTSConfig(
                        network=args.network, bitcoin_conf=args.bitcoin_conf, timeout=args.timeout))
                output = result.to_dict()
                code = _verification_exit(result)
    except ImportError:
        output = {"status": "error", "code": "dependency_unavailable",
                  "message": "Install the fingerprinto extra; Bitcoin/OTS also requires the POSIX settlement environment."}
        code = 1
    except (OSError, ValueError, TypeError):
        output = {"status": "error", "code": "input_or_operation_failed",
                  "message": "Invalid arguments, malformed evidence, conflicting output or local I/O failure."}
        code = 1
    print(json.dumps(output))
    return code
