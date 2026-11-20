"""Bitcoin/OTS transport for already-signed FingerprinTO receipts (POSIX).

This module does not issue receipts or accept camera/enrollment trust. Network
and calendars are selected by the caller's OTSConfig, never by bundle metadata.
"""

from dataclasses import dataclass, replace
import os
from pathlib import Path
import tempfile

from settlement import OTSConfig, SettlementService
from settlement.exceptions import InvalidProof
from settlement.models import SettlementResult, Status
from settlement.ots_backend import OTSBackend

from .verification import Check, VerificationResult, verify_receipt


# Local transport limit, in addition to the receipt schema limits.
MAX_OTS_BYTES = 16 * 1024 * 1024


def verify_receipt_ots(
    receipt_bytes: bytes,
    asset: bytes | None,
    credential: bytes | None = None,
    *,
    proof: bytes | None,
    config: OTSConfig,
) -> VerificationResult:
    """Reconstruct C and verify SHA256(C) through the existing Bitcoin verifier.

    Verification does not fetch calendars, upgrade proofs or write files. Only
    Bitcoin RPC may be contacted. The signature and anchor outcomes remain
    independent; a verified anchor does not make a bad signature valid.
    """
    result = verify_receipt(receipt_bytes, asset, credential, anchor_evidence=proof)
    details = {"backend": "opentimestamps-bitcoin", "network": config.network}
    result = replace(result, anchor_details=details,
                     verifier_profile="fingerprinto/v1-bitcoin-ots")
    if proof is None:
        return result
    if type(proof) is not bytes or len(proof) > MAX_OTS_BYTES:
        return replace(result, anchor=Check("failed", "ots_profile_invalid", "OTS proof exceeds the transport limit or has an invalid type."))
    if "OTS_SHA256_C" not in result.computed:
        return replace(result, anchor=Check("indeterminate", "commitment_unavailable", "Cannot reconstruct C from the supplied receipt evidence."))
    backend = OTSBackend(config)
    try:
        actual = backend.digest(proof)
    except InvalidProof:
        return replace(result, anchor=Check("failed", "ots_proof_invalid", "Malformed or incompatible SHA-256 OTS proof."))
    if actual.hex() != result.computed["OTS_SHA256_C"]:
        return replace(result, anchor=Check("failed", "ots_commitment_mismatch", "OTS proof is not bound to SHA256(C)."))
    status, bitcoin, diagnostics = backend.verify(proof)
    details = {**details, "binding_checked": True,
               "block_height": bitcoin.block_height, "block_time": bitcoin.block_time,
               "diagnostics": [{"code": d.code, "message": d.message} for d in diagnostics]}
    checks = {
        Status.CONFIRMED: Check("verified", "bitcoin_anchor_verified", "Commitment inclusion verified against the selected node's active chain."),
        Status.PENDING: Check("pending", "ots_pending", "Calendar receipt has no Bitcoin attestation yet."),
        Status.INVALID: Check("failed", "bitcoin_anchor_invalid", "Bitcoin attestation does not match the active chain."),
        Status.UNVERIFIABLE: Check("indeterminate", "bitcoin_anchor_unverifiable", "Bitcoin verification could not be completed."),
    }
    check = checks.get(status, Check("indeterminate", "bitcoin_anchor_error", "Bitcoin verification failed operationally."))
    return replace(result, anchor=check, anchor_details=details)


def _publish_commitment(path: Path, value: bytes) -> None:
    """Atomic no-overwrite publication; repeat only if exact bytes agree."""
    fd, temporary = tempfile.mkstemp(prefix=".commitment-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError:
            if path.is_symlink() or not path.is_file():
                raise ValueError("Conflicting commitment path") from None
            with path.open("rb") as existing:
                if existing.read(33) != value:
                    raise ValueError("Existing commitment has different bytes") from None
    finally:
        os.unlink(temporary)


def export_commitment(
    receipt_bytes: bytes, asset: bytes, output_dir: str | Path,
    credential: bytes | None = None,
) -> Path:
    """Export <output_dir>/<C hex>/commitment.bin; never sign or accept origin.

    The existing receipt must pass encoding and installation signature checks.
    The output is only an anchor payload, not an exported evidence bundle.
    Use an app-owned directory and keep original receipt/asset evidence.
    """
    result = verify_receipt(receipt_bytes, asset, credential)
    if result.profile.status != "accepted" or result.installation_signature.status != "valid":
        raise ValueError("A well-formed receipt with a valid installation signature is required")
    value = bytes.fromhex(result.computed["C"])
    directory = Path(output_dir) / value.hex()
    if directory.is_symlink():
        raise ValueError("Commitment directory must not be a symlink")
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "commitment.bin"
    _publish_commitment(path, value)
    return path


@dataclass(frozen=True)
class AnchorSubmission:
    commitment_file: Path
    settlement: SettlementResult


def stamp_receipt(
    receipt_bytes: bytes, asset: bytes, output_dir: str | Path,
    credential: bytes | None = None, *, config: OTSConfig,
) -> AnchorSubmission:
    """Explicit network action; submit an existing signed receipt, not a photo.

    Reuses the settlement service's locks, conflict handling and idempotence.
    Success is PENDING even if a proof was already upgraded. This operation
    makes no origin/enrollment claim and does not automatically verify Bitcoin.
    """
    path = export_commitment(receipt_bytes, asset, output_dir, credential)
    settlement = SettlementService(config).stamp_file(path, path.parent)
    return AnchorSubmission(path, settlement)
