"""Machine-readable command line interface."""
import argparse
import json
from typing import Sequence

from .exceptions import SettlementFailure
from .models import Diagnostic, SettlementResult, Status
from .ots_backend import OTSConfig
from .service import SettlementService


class _Parser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise SettlementFailure("usage_error", message)


def main(argv: Sequence[str] | None = None) -> int:
    parser = _Parser(description="OpenTimestamps Bitcoin settlement for opaque files")
    sub = parser.add_subparsers(dest="command", required=True)
    for command in ("stamp", "upgrade", "verify"):
        child = sub.add_parser(command)
        if command in ("stamp", "verify"):
            child.add_argument("payload")
        if command in ("upgrade", "verify"):
            child.add_argument("proof")
        if command == "stamp":
            child.add_argument("--out", required=True)
        child.add_argument("--timeout", type=int, default=10)
        child.add_argument("--network", choices=("mainnet", "regtest"), default="mainnet")
        child.add_argument("--bitcoin-conf", help="Path to Bitcoin Core configuration (cookie auth supported)")
        if command in ("stamp", "upgrade"):
            child.add_argument("--calendar", action="append", help="Explicitly authorized calendar; HTTP allowed only on regtest (repeatable)")
    selected_network = "mainnet"
    try:
        args = parser.parse_args(argv)
        selected_network = args.network
        options = {"timeout": args.timeout, "bitcoin_conf": args.bitcoin_conf, "network": args.network}
        if getattr(args, "calendar", None):
            options["calendars"] = tuple(args.calendar)
        service = SettlementService(OTSConfig(**options))
        if args.command == "stamp":
            result = service.stamp_file(args.payload, args.out)
        elif args.command == "upgrade":
            result = service.upgrade_proof(args.proof)
        else:
            result = service.verify_file(args.payload, args.proof)
        if result.status is Status.PENDING:
            code = 2 if args.command == "verify" else 0
        else:
            code = {Status.CONFIRMED: 0, Status.INVALID: 3,
                    Status.UNVERIFIABLE: 4, Status.ERROR: 1}[result.status]
    except (SettlementFailure, ValueError) as exc:
        result = SettlementResult(Status.ERROR, network=selected_network,
                                  diagnostics=[Diagnostic("usage_error", str(exc))])
        code = 1
    print(json.dumps(result.to_dict()))
    return code
