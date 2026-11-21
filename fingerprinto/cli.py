"""JSON CLI for local evidence transport and independent verification."""

import argparse
import json
import re
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
    parser = _Parser(description="FingerprinTO explicit v1/hybrid-v2 evidence tools")
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("pack", "pack-hybrid"):
        pack = commands.add_parser(name, help="Create a bundle from existing binary evidence")
        pack.add_argument("--receipt", required=True)
        pack.add_argument("--asset", required=True)
        pack.add_argument("--credential")
        pack.add_argument("--ots-proof")
        pack.add_argument("--anchor-locator")
        pack.add_argument("--out", required=True)
    capture = commands.add_parser("check-capture", help="Check an unsigned Android capture diagnostic export")
    capture.add_argument("archive")
    for name in ("inspect", "verify", "verify-ots", "inspect-hybrid", "verify-hybrid", "verify-hybrid-ots", "stamp-hybrid"):
        command = commands.add_parser(name)
        command.add_argument("bundle")
        if name in ("verify-hybrid", "verify-hybrid-ots", "stamp-hybrid"):
            command.add_argument("--expected-device-id", required=True, help="64 hex digits from the trusted enrollment registry")
        if name in ("verify-ots", "verify-hybrid-ots", "stamp-hybrid"):
            command.add_argument("--network", required=True, choices=("mainnet", "regtest"))
            command.add_argument("--bitcoin-conf")
            command.add_argument("--timeout", type=int, default=10)
        if name == "stamp-hybrid":
            command.add_argument("--out", required=True)
            command.add_argument("--calendar", action="append")
    try:
        args = parser.parse_args(argv)
        hybrid = "hybrid" in args.command
        profile = "hybrid-v2" if hybrid else "v1"
        expected_id = None
        if hasattr(args, "expected_device_id"):
            if not re.fullmatch(r"[0-9a-fA-F]{64}", args.expected_device_id):
                raise ValueError("Expected exactly 32 bytes of device ID")
            expected_id = bytes.fromhex(args.expected_device_id)
        if args.command == "check-capture":
            from .capture import check_capture
            output = check_capture(args.archive)
            code = 3 if output["file_binding"]["status"] == "invalid" else 2
        elif args.command in ("pack", "pack-hybrid"):
            bundle = EvidenceBundle(
                _read(args.receipt, LIMITS["receipt.cbor"]),
                _read(args.asset, LIMITS["asset.bin"]),
                _read(args.credential, LIMITS["credential.bin"]),
                _read(args.ots_proof, LIMITS["commitment.bin.ots"]),
                _read(args.anchor_locator, LIMITS["anchor.json"]), profile)
            path = export_bundle(bundle, args.out)
            output = {"operation": args.command, "profile": profile, "status": "stored", "bundle": str(path.absolute()),
                      "trust_evaluated": False}
            code = 0
        else:
            bundle = import_bundle(args.bundle, profile=profile)
            if args.command in ("inspect", "inspect-hybrid"):
                if hybrid:
                    from .hybrid_receipt import HybridReceipt
                    receipt = HybridReceipt.from_bytes(bundle.receipt).fields
                else:
                    receipt = Receipt.from_bytes(bundle.receipt)
                files = bundle.files()
                output = {"operation": args.command, "status": "readable", "receipt_version": 2 if hybrid else 1,
                          "placement": receipt.placement, "files": {k: len(v) for k, v in files.items()},
                          "commitment": files["commitment.bin"].hex(), "trust_evaluated": False}
                if hybrid:
                    output["declared_hybrid_device_id"] = HybridReceipt.from_bytes(bundle.receipt).public.device_id.hex()
                code = 0
            else:
                if args.command == "stamp-hybrid":
                    from .anchoring import stamp_hybrid_receipt
                    from settlement import OTSConfig
                    options = dict(network=args.network, bitcoin_conf=args.bitcoin_conf, timeout=args.timeout)
                    if args.calendar:
                        options["calendars"] = tuple(args.calendar)
                    submission = stamp_hybrid_receipt(bundle.receipt, bundle.asset, args.out, bundle.credential,
                                                      expected_device_id=expected_id, config=OTSConfig(**options))
                    output = submission.settlement.to_dict()
                    output.update(operation=args.command, profile=profile, expected_hybrid_device_id=expected_id.hex(),
                                  commitment_file=str(submission.commitment_file))
                    code = 0 if output["status"] == "PENDING" else 1
                elif args.command == "verify-hybrid":
                    from .bundle import verify_bundle_hybrid
                    result = verify_bundle_hybrid(bundle, expected_device_id=expected_id)
                elif args.command == "verify-hybrid-ots":
                    from .bundle import verify_bundle_hybrid_ots
                    from settlement import OTSConfig
                    result = verify_bundle_hybrid_ots(bundle, expected_device_id=expected_id, config=OTSConfig(
                        network=args.network, bitcoin_conf=args.bitcoin_conf, timeout=args.timeout))
                elif args.command == "verify":
                    from .bundle import verify_bundle
                    result = verify_bundle(bundle)
                else:
                    from .bundle import verify_bundle_ots
                    from settlement import OTSConfig
                    result = verify_bundle_ots(bundle, config=OTSConfig(
                        network=args.network, bitcoin_conf=args.bitcoin_conf, timeout=args.timeout))
                if args.command != "stamp-hybrid":
                    output = result.to_dict()
                    code = _verification_exit(result)
    except ImportError:
        output = {"status": "error", "code": "dependency_unavailable",
                  "message": "Install the fingerprinto/hybrid extra as required; Bitcoin/OTS requires the POSIX settlement environment."}
        code = 1
    except (OSError, ValueError, TypeError):
        output = {"status": "error", "code": "input_or_operation_failed",
                  "message": "Invalid arguments, malformed evidence, conflicting output or local I/O failure."}
        code = 1
    print(json.dumps(output))
    return code
