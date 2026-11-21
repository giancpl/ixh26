"""Offline v3 orchestration on immutable bytes and verifier-owned dependencies.

No UI, SDK, upload or ledger operations. Both validators always run. Default
manufacturer adapter recognises presence only, and never produces a pass.
"""

from dataclasses import asdict, dataclass
from datetime import datetime

from cryptography.exceptions import InvalidSignature

from .bundle import EvidenceBundle
from .core import Receipt, commitments, digest, domain
from .evidence_v3 import Anchor, Outcome, Required, reduce_evidence
from .prnu import score
from .prnu_image import decode_green
from .prnu_records import REFERENCE_LIMIT, json_bytes, read_reference
from .receipt_v3 import Evidence, ReceiptV3, RESULTS, json_object
from .enrollment_requests import instant


@dataclass(frozen=True)
class Finding:
    status: Outcome
    reason: str
    details: dict

    def __post_init__(self):
        if type(self.status) is not Outcome or type(self.reason) is not str or not self.reason or type(self.details) is not dict:
            raise ValueError("Invalid independent validator finding")

    def report(self):
        return {"status": self.status.value, "reason": self.reason, "details": self.details}


def embedded_candidate(asset):
    """Recognise JPEG JUMBF/C2PA candidates, never authenticate their claims."""
    if not asset.startswith(b"\xff\xd8"):
        return None
    offset = 2
    while offset < len(asset):
        if asset[offset] != 255:
            return None
        while offset < len(asset) and asset[offset] == 255:
            offset += 1
        if offset >= len(asset):
            return None
        marker = asset[offset]; offset += 1
        if marker in (0xDA, 0xD9):
            return False
        if marker in (0x01, *range(0xD0, 0xD9)):
            continue
        if offset + 2 > len(asset):
            return None
        size = int.from_bytes(asset[offset:offset+2], "big")
        if size < 2 or offset + size > len(asset):
            return None
        payload = asset[offset+2:offset+size]
        if marker == 0xEB and payload.startswith(b"JP"):
            return True
        if marker == 0xE1 and b"c2pa" in payload.lower():
            return True
        offset += size
    return None


def manufacturer_unconfigured(asset, credential, placement):
    candidate = embedded_candidate(asset)
    if candidate is True and placement != 0:
        return Finding(Outcome.FAIL, "ambiguous_or_hidden_embedded_evidence", {})
    if placement == 1 or candidate is True:
        return Finding(Outcome.INCONCLUSIVE, "manufacturer_validator_unconfigured", {})
    if placement == 0:
        return Finding(Outcome.INCONCLUSIVE, "embedded_evidence_not_supported", {})
    return Finding(Outcome.UNAVAILABLE if candidate is False else Outcome.INCONCLUSIVE,
                   "manufacturer_evidence_absent" if candidate is False else "asset_inspection_unsupported", {})


def _enrollment(fields, store, now):
    if store is None:
        return None, Required.PENDING, "enrollment_store_unconfigured"
    record, reason = store.lookup(fields, now=now)
    if record is not None:
        return record, Required.VALID, reason
    status = Required.INVALID if reason in ("installation_revoked", "enrolled_release_mismatch") else Required.PENDING
    return None, status, reason


def assess(fields, asset, credential, placement, policy, ref_hash, *, now, store=None,
           reference_loader=None, manufacturer_validator=None):
    """All configuration/dependencies are owned by the caller, never the bundle."""
    instant(now)
    policy_object = json_object(policy)
    record, enrollment, enrollment_reason = _enrollment(fields, store, now)
    # Even with unconfigured validators, check visible evidence selection first.
    m = manufacturer_unconfigured(asset, credential, placement)
    if manufacturer_validator is not None:
        checked = manufacturer_validator(asset, credential, placement)
        if type(checked) is not Finding:
            raise ValueError("Manufacturer adapter must return a Finding")
        if m.status is not Outcome.FAIL:
            m = checked
    p = Finding(Outcome.UNAVAILABLE, "reference_not_selected", {})
    if ref_hash is not None:
        association = next((r for r in record["references"] if r["hash"] == ref_hash.hex()), None) if record else None
        if reference_loader is None:
            p = Finding(Outcome.UNAVAILABLE, "reference_unavailable", {})
        else:
            raw = reference_loader(ref_hash)
            if raw is None:
                p = Finding(Outcome.UNAVAILABLE, "reference_unavailable", {})
            elif type(raw) is not bytes or len(raw) > REFERENCE_LIMIT or digest(raw) != ref_hash:
                p = Finding(Outcome.FAIL, "reference_digest_mismatch", {})
            else:
                ref = read_reference(raw)
                if association is None:
                    p = Finding(Outcome.INCONCLUSIVE, "reference_association_untrusted", {})
                elif association["geometry"] != asdict(ref.geometry):
                    p = Finding(Outcome.FAIL, "reference_association_geometry_mismatch", {})
                else:
                    try:
                        values = decode_green(asset, ref.geometry, source_width=ref.geometry.source_width,
                                              source_height=ref.geometry.source_height)
                        result = score(values, ref.geometry, ref, asset_digest=digest(asset))
                    except ImportError:
                        p = Finding(Outcome.UNAVAILABLE, "prnu_decoder_unavailable", {})
                    except (ValueError, OSError):
                        p = Finding(Outcome.INCONCLUSIVE, "unsupported_image_route", {})
                    else:
                        details = {"method": "fingerprinto/prnu-haar-green/1", "geometry": asdict(ref.geometry),
                                   "score": result.value, "r0": result.r0, "validFraction": result.valid_fraction,
                                   "accept": association["accept"], "reject": association["reject"],
                                   "calibrationHash": association["calibrationHash"]}
                        outcome, reason = Outcome.INCONCLUSIVE, result.reason
                        expected_config = {"method": details["method"], "geometry": asdict(ref.geometry),
                                           "accept": association["accept"], "reject": association["reject"],
                                           "calibrationHash": association["calibrationHash"]}
                        actual_config = policy_object.get("prnu")
                        if (association["policyHash"] != digest(policy).hex()
                                or policy_object.get("schema") != "fingerprinto/policy-v3/1"
                                or actual_config != expected_config
                                or any(type(actual_config.get(k)) is bool for k in ("accept", "reject"))):
                            reason = "supplied_policy_not_approved_for_reference"
                        elif result.value is not None:
                            if association["accept"] is not None and result.value >= association["accept"]:
                                outcome, reason = Outcome.PASS, "calibrated_reference_match"
                            elif association["reject"] is not None and result.value <= association["reject"]:
                                outcome, reason = Outcome.FAIL, "calibrated_reference_mismatch"
                            else:
                                reason = "calibration_unavailable_or_intermediate_score"
                        p = Finding(outcome, reason, details)
    return m, p, enrollment, enrollment_reason


def verify_v3(bundle, *, now: datetime, store=None, reference_loader=None,
              manufacturer_validator=None, anchor: Anchor | None = None):
    if bundle.profile != "evidence-v3":
        raise ValueError("Explicit evidence-v3 profile required")
    bundle.files()
    receipt = ReceiptV3.from_bytes(bundle.receipt)
    m, p, enrollment, reason = assess(receipt.fields, bundle.asset, bundle.credential,
                                    receipt.evidence.placement, bundle.policy, receipt.evidence.ref_hash,
                                    now=now, store=store, reference_loader=reference_loader,
                                    manufacturer_validator=manufacturer_validator)
    signature = Required.VALID
    try:
        roots = receipt.verify_signature(bundle.asset, bundle.credential)
    except (InvalidSignature, ValueError):
        signature = Required.INVALID
        roots = receipt.roots(bundle.asset, bundle.credential)
    # Failed-origin receipts are diagnostics, not receipts eligible for acceptance.
    binding = Required.INVALID if 3 in (receipt.evidence.manufacturer_result, receipt.evidence.prnu_result) else Required.VALID
    if anchor is None:
        anchor = Anchor.UNRESOLVED if bundle.ots_proof is not None or bundle.anchor_locator is not None else Anchor.ABSENT
    decision = reduce_evidence(m.status, p.status, enrollment=enrollment, binding=binding,
                               signature=signature, anchor=anchor)
    return {"profile": "evidence-v3", "evaluatedAt": now.isoformat(),
            "decision": asdict(decision), "manufacturer": m.report(), "prnu": p.report(),
            "signature": signature.value, "binding": binding.value,
            "enrollment": {"status": enrollment.value, "reason": reason},
            "anchor": anchor.value, "signedClaims": {"manufacturer": RESULTS[receipt.evidence.manufacturer_result],
            "prnu": RESULTS[receipt.evidence.prnu_result]}, "computed": {k: v.hex() for k, v in roots.items()}}


def issue_v3(fields: Receipt, asset: bytes, *, placement: int, credential: bytes | None,
             policy: bytes, reference_hash: bytes | None, now: datetime, store,
             journal, operation: str, sign_root, reference_loader=None, manufacturer_validator=None):
    """Validate both checks, then journal one verified signature before transport.

    sign_root receives R and must use the enrolled installation key. Hardware
    key invocation on Android uses the same byte profile; this Python path is
    the independently testable orchestration, not a claim of hardware execution.
    """
    # Fail malformed inputs before any validator/signing callback.
    commitments(fields, asset)
    if credential is not None and (type(credential) is not bytes or not 1 <= len(credential) <= 16*1024*1024):
        raise ValueError("Invalid detached credential")
    Evidence(placement, digest(credential) if type(credential) is bytes else None,
             0, 0, reference_hash, digest(policy), b"\0"*32)
    m, p, enrollment, _ = assess(fields, asset, credential, placement, policy, reference_hash,
                                now=now, store=store, reference_loader=reference_loader,
                                manufacturer_validator=manufacturer_validator)
    decision = reduce_evidence(m.status, p.status, enrollment=enrollment, binding=Required.VALID,
                               signature=None, anchor=Anchor.ABSENT, issuance=True)
    if not decision.can_issue:
        raise ValueError("Issuance blocked by failed or pending mandatory evidence")
    report = json_bytes({"schema": "fingerprinto/report-v3/1", "manufacturer": m.report(), "prnu": p.report(),
                         "referenceHash": reference_hash.hex() if reference_hash else None, "policyHash": digest(policy).hex()})
    evidence = Evidence(placement, digest(credential) if credential is not None else None,
                        RESULTS.index(m.status.value), RESULTS.index(p.status.value), reference_hash, digest(policy), digest(report))
    # Reject report schema/size disagreement before invoking the signing key or
    # committing a journal record that could never be exported.
    evidence.attachments(policy, report)
    draft = ReceiptV3(fields, evidence)
    EvidenceBundle(draft.with_signature(b"\0" * 72).to_bytes(), asset, credential, profile="evidence-v3",
                   policy=policy, report=report).files()
    root = draft.roots(asset, credential)["R"]
    def produce():
        signed = draft.with_signature(sign_root(root))
        signed.verify_signature(asset, credential)
        return signed.to_bytes()
    raw = journal.sign_once(operation, domain("issuance/evidence-v3", root), produce)
    signed = ReceiptV3.from_bytes(raw)
    if signed.roots(asset, credential)["R"] != root:
        raise ValueError("Stored receipt does not bind the current request")
    signed.verify_signature(asset, credential)
    bundle = EvidenceBundle(raw, asset, credential, profile="evidence-v3", policy=policy, report=report)
    bundle.files()
    return bundle
