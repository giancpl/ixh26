"""Explicitly selected v1/v2 evidence transport. No extraction or trust from import.

The ZIP layout is a transport convention, not a change to the signed protocol.
Network hints are untrusted; verification configuration belongs to the caller.
"""

from dataclasses import dataclass
import json
import os
from pathlib import Path
import stat
import tempfile
import zipfile
import zlib

from .core import Receipt, commitments

MiB = 1024 * 1024
MAX_TOTAL = 160 * MiB
MAX_ARCHIVE = MAX_TOTAL + MiB
LIMITS = {"receipt.cbor": 256 * 1024, "asset.bin": 128 * MiB,
          "credential.bin": 16 * MiB, "commitment.bin": 32,
          "commitment.bin.ots": 16 * MiB, "anchor.json": 4096,
          "policy.json": MiB, "report.json": MiB}


def _locator(value: bytes) -> dict:
    def unique(pairs):
        result = {}
        for key, val in pairs:
            if key in result:
                raise ValueError("Duplicate locator field")
            result[key] = val
        return result

    locator = json.loads(value.decode("utf-8"), object_pairs_hook=unique)
    if type(locator) is not dict or set(locator) - {"backend", "network", "transaction_hash"}:
        raise ValueError("Invalid anchor locator fields")
    backend, network = locator.get("backend"), locator.get("network")
    if type(network) is not str or not 1 <= len(network.encode()) <= 255:
        raise ValueError("Invalid locator network")
    if backend == "opentimestamps-bitcoin":
        if network not in ("mainnet", "regtest") or "transaction_hash" in locator:
            raise ValueError("Invalid Bitcoin locator")
    elif backend == "xrpl":
        tx = locator.get("transaction_hash")
        if type(tx) is not str or len(tx) != 64 or any(c not in "0123456789abcdefABCDEF" for c in tx):
            raise ValueError("Invalid XRPL transaction hash")
    else:
        raise ValueError("Unsupported locator backend")
    return locator


@dataclass(frozen=True)
class EvidenceBundle:
    receipt: bytes
    asset: bytes
    credential: bytes | None = None
    ots_proof: bytes | None = None
    anchor_locator: bytes | None = None
    profile: str = "v1"
    policy: bytes | None = None
    report: bytes | None = None

    def files(self) -> dict[str, bytes]:
        """Validate transport/schema limits and reconstruct C, without trust checks."""
        files = {"receipt.cbor": self.receipt, "asset.bin": self.asset}
        if self.profile == "evidence-v3":
            files.update({"policy.json": self.policy, "report.json": self.report})
        elif self.policy is not None or self.report is not None:
            raise ValueError("v3 attachments require explicit evidence-v3 profile")
        for name, value in (("credential.bin", self.credential),
                            ("commitment.bin.ots", self.ots_proof),
                            ("anchor.json", self.anchor_locator)):
            if value is not None:
                files[name] = value
        for name, data in files.items():
            if type(data) is not bytes or len(data) > LIMITS[name]:
                raise ValueError(f"Invalid or oversized {name}")
        if self.anchor_locator is not None:
            try:
                locator = _locator(self.anchor_locator)
            except RecursionError as exc:
                raise ValueError("Excessive locator nesting") from exc
            if self.ots_proof is not None and locator["backend"] != "opentimestamps-bitcoin":
                raise ValueError("Conflicting anchor backend")
        if self.profile == "v1":
            receipt = Receipt.from_bytes(self.receipt)
            files["commitment.bin"] = commitments(receipt, self.asset, self.credential)["C"]
        elif self.profile == "hybrid-v2":
            from cryptography.exceptions import InvalidSignature
            from .hybrid_receipt import HybridReceipt
            try:
                receipt = HybridReceipt.from_bytes(self.receipt)
            except InvalidSignature as exc:
                raise ValueError("Invalid hybrid hardware binding") from exc
            files["commitment.bin"] = receipt.commitment(self.asset, self.credential)
        elif self.profile == "evidence-v3":
            from .receipt_v3 import ReceiptV3
            receipt = ReceiptV3.from_bytes(self.receipt)
            receipt.evidence.attachments(self.policy, self.report)
            files["commitment.bin"] = receipt.roots(self.asset, self.credential)["C"]
        else:
            raise ValueError("Unsupported bundle profile")
        if sum(map(len, files.values())) > MAX_TOTAL:
            raise ValueError("Oversized evidence bundle")
        return files


def export_bundle(bundle: EvidenceBundle, destination: str | Path) -> Path:
    """Atomically publish a new ZIP without replacing any existing destination.

    Preserve all evidence verbatim. A malformed signature can be transported
    for diagnosis: export success is never a verification result.
    """
    files = bundle.files()
    destination = Path(destination)
    fd, temporary = tempfile.mkstemp(prefix=".fingerprinto-", dir=destination.parent)
    try:
        with os.fdopen(fd, "w+b") as stream:
            with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_STORED) as archive:
                for name, data in files.items():
                    archive.writestr(name, data)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, destination)
    finally:
        os.unlink(temporary)
    return destination


def import_bundle(source: str | Path, *, profile: str = "v1") -> EvidenceBundle:
    """Read only the fixed allowlisted names; never extract archive paths.

    Size checks apply before and during decompression. CRC errors, encrypted
    members, links, duplicate/unknown names and unsupported compression fail.
    Inputs are bounded but kept in memory; use a worker for large bundles.
    """
    try:
        with Path(source).open("rb") as stream:
            if os.fstat(stream.fileno()).st_size > MAX_ARCHIVE:
                raise ValueError("Oversized archive")
            with zipfile.ZipFile(stream) as archive:
                members = archive.infolist()
                if not 3 <= len(members) <= len(LIMITS):
                    raise ValueError("Invalid bundle member count")
                seen = set()
                total = 0
                for info in members:
                    name = info.filename
                    mode = info.external_attr >> 16
                    if (name not in LIMITS or info.orig_filename != name or name in seen
                            or info.is_dir() or stat.S_IFMT(mode) not in (0, stat.S_IFREG)
                            or info.flag_bits & 1
                            or info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)):
                        raise ValueError("Unsupported or duplicate bundle member")
                    seen.add(name)
                    if info.file_size > LIMITS[name]:
                        raise ValueError("Oversized bundle member")
                    total += info.file_size
                if total > MAX_TOTAL:
                    raise ValueError("Oversized evidence bundle")
                if not {"receipt.cbor", "asset.bin", "commitment.bin"} <= seen:
                    raise ValueError("Missing required evidence")
                files = {}
                consumed = 0
                for info in members:
                    data = bytearray()
                    with archive.open(info) as member:
                        while chunk := member.read(min(65536, LIMITS[info.filename] - len(data) + 1)):
                            data.extend(chunk)
                            consumed += len(chunk)
                            if len(data) > LIMITS[info.filename] or consumed > MAX_TOTAL:
                                raise ValueError("Decompressed evidence exceeds limits")
                    if len(data) != info.file_size:
                        raise ValueError("Incorrect bundle member length")
                    files[info.filename] = bytes(data)
        bundle = EvidenceBundle(files["receipt.cbor"], files["asset.bin"],
                                files.get("credential.bin"), files.get("commitment.bin.ots"),
                                files.get("anchor.json"), profile,
                                files.get("policy.json"), files.get("report.json"))
        if bundle.files()["commitment.bin"] != files["commitment.bin"]:
            raise ValueError("Bundled commitment does not match evidence")
        return bundle
    except (zipfile.BadZipFile, EOFError, NotImplementedError, RuntimeError, zlib.error) as exc:
        raise ValueError("Invalid evidence archive") from exc


def verify_bundle(bundle: EvidenceBundle):
    """Offline signature check; anchor hints/evidence are never trusted."""
    from .verification import verify_receipt
    if bundle.profile != "v1":
        raise ValueError("Use the explicit hybrid verifier and a trusted device ID")
    bundle.files()
    evidence = bundle.ots_proof if bundle.ots_proof is not None else bundle.anchor_locator
    return verify_receipt(bundle.receipt, bundle.asset, bundle.credential,
                          anchor_evidence=evidence)


def verify_bundle_ots(bundle: EvidenceBundle, *, config):
    """Explicit Bitcoin RPC verification using verifier-owned network config."""
    from .anchoring import verify_receipt_ots
    if bundle.profile != "v1":
        raise ValueError("Use the explicit hybrid verifier and a trusted device ID")
    bundle.files()
    if bundle.ots_proof is None and bundle.anchor_locator is not None:
        # A locator is evidence to resolve, not a proof of absence or inclusion.
        return verify_bundle(bundle)
    return verify_receipt_ots(bundle.receipt, bundle.asset, bundle.credential,
                              proof=bundle.ots_proof, config=config)


def verify_bundle_hybrid(bundle: EvidenceBundle, *, expected_device_id: bytes):
    from .hybrid_receipt import verify_hybrid_receipt
    if bundle.profile != "hybrid-v2":
        raise ValueError("Expected an explicitly selected hybrid-v2 bundle")
    bundle.files()
    evidence = bundle.ots_proof if bundle.ots_proof is not None else bundle.anchor_locator
    return verify_hybrid_receipt(bundle.receipt, bundle.asset, bundle.credential,
                                 expected_device_id=expected_device_id, anchor_evidence=evidence)


def verify_bundle_hybrid_ots(bundle: EvidenceBundle, *, expected_device_id: bytes, config):
    from .anchoring import verify_hybrid_receipt_ots
    if bundle.profile != "hybrid-v2":
        raise ValueError("Expected an explicitly selected hybrid-v2 bundle")
    bundle.files()
    if bundle.ots_proof is None and bundle.anchor_locator is not None:
        return verify_bundle_hybrid(bundle, expected_device_id=expected_device_id)
    return verify_hybrid_receipt_ots(bundle.receipt, bundle.asset, bundle.credential,
                                     expected_device_id=expected_device_id, proof=bundle.ots_proof, config=config)
