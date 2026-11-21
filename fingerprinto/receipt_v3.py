"""Explicit evidence-v3 byte profile. Signed findings are claims, never trust."""

from dataclasses import dataclass, replace
import json
import math

from .cbor import decode, encode
from .core import Receipt, commitments, digest, domain, node

JSON_LIMIT = 1024 * 1024
RESULTS = ("unavailable", "inconclusive", "pass", "fail")


def json_object(raw: bytes, *, limit: int = JSON_LIMIT) -> dict:
    """Bounded UTF-8 JSON object, rejecting duplicate keys/nonfinite numbers."""
    if type(raw) is not bytes or not 1 <= len(raw) <= limit:
        raise ValueError("Invalid JSON attachment size")

    def unique(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise ValueError("Duplicate JSON key")
            obj[key] = value
        return obj

    def walk(value, depth=0):
        if depth > 16:
            raise ValueError("Excessive JSON depth")
        if isinstance(value, float) and not math.isfinite(value):
            raise ValueError("Nonfinite JSON number")
        if isinstance(value, str):
            value.encode("utf-8", errors="strict")
        elif isinstance(value, dict):
            for key, item in value.items():
                walk(key, depth + 1)
                walk(item, depth + 1)
        elif isinstance(value, list):
            for item in value:
                walk(item, depth + 1)

    try:
        result = json.loads(raw.decode("utf-8"), object_pairs_hook=unique)
        walk(result)
    except (RecursionError, UnicodeError) as exc:
        raise ValueError("Invalid JSON encoding/depth") from exc
    if type(result) is not dict:
        raise ValueError("JSON attachment must be an object")
    return result


@dataclass(frozen=True)
class Evidence:
    placement: int
    detached_hash: bytes | None
    manufacturer_result: int
    prnu_result: int
    ref_hash: bytes | None
    policy_hash: bytes
    report_hash: bytes

    def __post_init__(self):
        for name, allowed in (("placement", range(3)), ("manufacturer_result", range(4)),
                              ("prnu_result", range(4))):
            value = getattr(self, name)
            if type(value) is not int or value not in allowed:
                raise ValueError(f"Invalid {name}")
        for name in ("detached_hash", "ref_hash", "policy_hash", "report_hash"):
            value = getattr(self, name)
            optional = name == "ref_hash" or (name == "detached_hash" and self.placement != 1)
            if value is None and optional:
                continue
            if type(value) is not bytes or len(value) != 32:
                raise ValueError(f"Invalid {name}")
        if self.placement != 1 and self.detached_hash is not None:
            raise ValueError("Unexpected detached digest")
        if self.prnu_result in (2, 3) and self.ref_hash is None:
            raise ValueError("Decisive PRNU claim needs a reference")
        if self.placement == 2 and self.manufacturer_result in (2, 3):
            raise ValueError("Decisive manufacturer claim needs evidence")

    def array(self):
        return [self.placement, self.detached_hash, self.manufacturer_result,
                self.prnu_result, self.ref_hash, self.policy_hash, self.report_hash]

    def attachments(self, policy: bytes, report: bytes, reference: bytes | None = None):
        # Validate lengths and both digests before parsing either attachment.
        for raw, expected in ((policy, self.policy_hash), (report, self.report_hash)):
            if type(raw) is not bytes or not 1 <= len(raw) <= JSON_LIMIT or digest(raw) != expected:
                raise ValueError("Missing, oversized or modified signed attachment")
        policy_obj, report_obj = json_object(policy), json_object(report)
        if report_obj.get("schema") != "fingerprinto/report-v3/1":
            raise ValueError("Unsupported diagnostic report schema")
        for name, code in (("manufacturer", self.manufacturer_result), ("prnu", self.prnu_result)):
            finding = report_obj.get(name)
            if (type(finding) is not dict or finding.get("status") != RESULTS[code]
                    or type(finding.get("reason")) is not str or not finding["reason"]):
                raise ValueError("Report/receipt finding disagreement")
        for name, expected in (("referenceHash", self.ref_hash), ("policyHash", self.policy_hash)):
            if report_obj.get(name) != (None if expected is None else expected.hex()):
                raise ValueError("Report/receipt identifier disagreement")
        if reference is not None and (self.ref_hash is None or digest(reference) != self.ref_hash):
            raise ValueError("Reference digest mismatch")
        return policy_obj, report_obj


@dataclass(frozen=True)
class ReceiptV3:
    # Inherited metadata validation only. The internal v1 placement is always 0;
    # all v3 placement semantics live exclusively in evidence.
    fields: Receipt
    evidence: Evidence

    def __post_init__(self):
        if type(self.fields) is not Receipt or self.fields.placement != 0 or type(self.evidence) is not Evidence:
            raise ValueError("Invalid v3 metadata/evidence")

    def to_bytes(self):
        fields = decode(self.fields.to_bytes())
        fields[0], fields[10] = 3, self.evidence.array()
        return encode(fields)

    @classmethod
    def from_bytes(cls, raw):
        f = decode(raw)
        if (type(f) is not list or len(f) != 12 or type(f[0]) is not int or f[0] != 3
                or type(f[2]) is not list or type(f[10]) is not list or len(f[10]) != 7):
            raise ValueError("Unsupported evidence-v3 receipt")
        evidence = Evidence(*f[10])
        return cls(Receipt(f[1], tuple(f[2]), *f[3:10], 0, f[11]), evidence)

    def roots(self, asset: bytes, credential: bytes | None = None):
        e = self.evidence
        if e.placement == 1:
            if type(credential) is not bytes or not 1 <= len(credential) <= 16 * 1024 * 1024:
                raise ValueError("Missing or oversized detached credential")
            if digest(credential) != e.detached_hash:
                raise ValueError("Detached credential digest mismatch")
        elif credential is not None:
            raise ValueError("Unexpected detached credential")
        r = commitments(self.fields, asset)
        r["F_P"] = domain("photo/evidence-v3", r["F_I"], r["SHA256_J"], e.array())
        r["N_IP"] = node(r["F_I"], r["F_P"])
        r["T"] = node(r["N_IP"], r["F_D"])
        r["R"] = domain("fingerprinto/evidence-v3", r["T"])
        r["C"] = domain("receipt", r["R"], self.fields.sigma)
        r["OTS_SHA256_C"] = digest(r["C"])
        if credential is not None:
            r["SHA256_A"] = digest(credential)
        return r

    def verify_signature(self, asset, credential=None):
        from .verification import verify_installation_signature
        roots = self.roots(asset, credential)
        verify_installation_signature(self.fields.pk_I, self.fields.sigma, roots["R"])
        return roots

    def with_signature(self, signature):
        """Byte construction only. Authorized issuance belongs to the orchestrator."""
        return replace(self, fields=replace(self.fields, sigma=signature))
