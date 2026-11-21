"""Explicit v3 and experimental PRNU commands. No implicit network actions."""

from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path

from cryptography.exceptions import InvalidSignature

from .bundle import EvidenceBundle, LIMITS, export_bundle, import_bundle
from .cli import _Parser, _read
from .core import digest
from .prnu import Estimator, Geometry, score
from .prnu_image import decode_green
from .prnu_records import REFERENCE_LIMIT, evaluate_samples, read_reference, save_reference
from .receipt_v3 import ReceiptV3, json_object
from .trust_v3 import TrustStore
from .workflow_v3 import verify_v3


def main(argv=None):
    parser = _Parser(description="Explicit evidence-v3 and experimental PRNU tools")
    sub = parser.add_subparsers(dest="command", required=True)
    pack = sub.add_parser("pack")
    for name in ("receipt", "asset", "policy", "report", "out"):
        pack.add_argument("--" + name, required=True)
    for name in ("credential", "ots-proof", "anchor-locator"):
        pack.add_argument("--" + name)
    inspect = sub.add_parser("inspect")
    inspect.add_argument("bundle")
    for name in ("verify", "verify-ots", "stamp"):
        p = sub.add_parser(name)
        p.add_argument("bundle")
        p.add_argument("--trust-db")
        p.add_argument("--authority-spki")
        p.add_argument("--reference-dir")
        if name != "verify":
            p.add_argument("--network", choices=("mainnet", "regtest"), required=True)
            p.add_argument("--bitcoin-conf")
            p.add_argument("--timeout", type=int, default=10)
        if name == "stamp":
            p.add_argument("--out", required=True)
            p.add_argument("--calendar", action="append")
    trust = sub.add_parser("trust-import")
    for name in ("record", "trust-db", "authority-spki"):
        trust.add_argument("--" + name, required=True)
    enroll = sub.add_parser("prnu-enroll")
    enroll.add_argument("--geometry", required=True)
    enroll.add_argument("--out", required=True)
    enroll.add_argument("images", nargs="+")
    query = sub.add_parser("prnu-score")
    query.add_argument("--reference", required=True)
    query.add_argument("--asset", required=True)
    evaluation = sub.add_parser("prnu-evaluate")
    evaluation.add_argument("--data", required=True, help="JSON object: samples, enrollment, development")
    evaluation.add_argument("--accept", type=float)
    evaluation.add_argument("--reject", type=float)
    try:
        args = parser.parse_args(argv)
        now = datetime.now(timezone.utc)
        code = 0
        if args.command == "pack":
            bundle = EvidenceBundle(_read(args.receipt,LIMITS["receipt.cbor"]),_read(args.asset,LIMITS["asset.bin"]),
                _read(args.credential,LIMITS["credential.bin"]),_read(args.ots_proof,LIMITS["commitment.bin.ots"]),
                _read(args.anchor_locator,LIMITS["anchor.json"]),"evidence-v3",
                _read(args.policy,LIMITS["policy.json"]),_read(args.report,LIMITS["report.json"]))
            output = {"status":"stored","bundle":str(export_bundle(bundle,args.out)),"trust_evaluated":False}
        elif args.command == "trust-import":
            store=TrustStore(args.trust_db,authority_spki=_read(args.authority_spki,91))
            store.retain(_read(args.record,1024*1024))
            output={"status":"retained","authority_authenticated":True}
        elif args.command == "prnu-enroll":
            geometry=Geometry(**json_object(_read(args.geometry,4096)))
            estimator=Estimator(geometry)
            for name in args.images:
                asset=_read(name,LIMITS["asset.bin"])
                values=decode_green(asset,geometry,source_width=geometry.source_width,source_height=geometry.source_height)
                estimator.add(values,asset_digest=digest(asset))
            raw=save_reference(estimator.finish(),args.out)
            output={"status":"experimental_reference","referenceHash":digest(raw).hex(),
                    "accepted_frames":len(args.images),"trusted_association":False,"calibration_approved":False}
        elif args.command == "prnu-score":
            ref=read_reference(_read(args.reference,REFERENCE_LIMIT))
            asset=_read(args.asset,LIMITS["asset.bin"])
            values=decode_green(asset,ref.geometry,source_width=ref.geometry.source_width,source_height=ref.geometry.source_height)
            output={"status":"inconclusive","experimental":True,**asdict(score(values,ref.geometry,ref,asset_digest=digest(asset)))}
            code=2
        elif args.command == "prnu-evaluate":
            data=json_object(_read(args.data,1024*1024))
            output=evaluate_samples(data["samples"],enrollment_ids=data["enrollment"],development_ids=data["development"],
                                    accept=args.accept,reject=args.reject)
        else:
            bundle=import_bundle(args.bundle,profile="evidence-v3")
            if args.command == "inspect":
                r=ReceiptV3.from_bytes(bundle.receipt)
                output={"profile":"evidence-v3","evidence":asdict(r.evidence),
                        "files":{k:len(v) for k,v in bundle.files().items()},"trust_evaluated":False}
                output["evidence"]={k:v.hex() if type(v) is bytes else v for k,v in output["evidence"].items()}
            else:
                if bool(args.trust_db) != bool(args.authority_spki):
                    raise ValueError("Both trust-db and authority-spki are required")
                store=TrustStore(args.trust_db,authority_spki=_read(args.authority_spki,91)) if args.trust_db else None
                def loader(identity):
                    if not args.reference_dir:
                        return None
                    path=Path(args.reference_dir)/(identity.hex()+".json")
                    return _read(str(path),REFERENCE_LIMIT) if path.exists() else None
                options=dict(now=now,store=store,reference_loader=loader)
                if args.command == "verify":
                    output=verify_v3(bundle,**options)
                else:
                    from settlement import OTSConfig
                    from .anchoring_v3 import stamp_v3,verify_v3_ots
                    config_options=dict(network=args.network,bitcoin_conf=args.bitcoin_conf,timeout=args.timeout)
                    if args.command == "stamp" and args.calendar:
                        config_options["calendars"]=tuple(args.calendar)
                    config=OTSConfig(**config_options)
                    if args.command == "stamp":
                        submitted=stamp_v3(bundle,args.out,config=config,**options)
                        output=submitted.settlement.to_dict()
                        output["commitment_file"]=str(submitted.commitment_file)
                        code=0 if output["status"]=="PENDING" else 1
                    else:
                        output=verify_v3_ots(bundle,config=config,**options)
                if args.command != "stamp":
                    state=output["decision"]["state"]
                    code=3 if state=="failed" else 2 if state=="pending" else 0
        print(json.dumps(output,allow_nan=False))
        return code
    except ImportError:
        output={"status":"error","code":"dependency_unavailable","message":"Install prnu/fingerprinto extras; OTS requires the POSIX settlement environment."}
        code=1
    except InvalidSignature:
        output={"status":"error","code":"authority_signature_invalid"}
        code=3
    except (ValueError,TypeError,KeyError,OSError):
        output={"status":"error","code":"invalid_evidence_or_operation"}
        code=3
    print(json.dumps(output))
    return code
