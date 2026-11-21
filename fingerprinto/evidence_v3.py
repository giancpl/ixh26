"""Shared v3 decision rules on independently verified findings, never receipt claims."""

from dataclasses import dataclass
from enum import Enum


class Outcome(str, Enum):
    UNAVAILABLE = "unavailable"
    INCONCLUSIVE = "inconclusive"
    PASS = "pass"
    FAIL = "fail"
    PENDING = "pending"


class Required(str, Enum):
    VALID = "valid"
    INVALID = "invalid"
    PENDING = "pending"


class Anchor(str, Enum):
    ABSENT = "absent"
    PENDING = "pending"  # A calendar promise / unconfirmed transaction, not a failed check.
    VERIFIED = "verified"
    UNRESOLVED = "unresolved"  # Cannot yet check an anchor claimed by the bundle.
    FAILED = "failed"


@dataclass(frozen=True)
class Decision:
    state: str
    level: str | None
    label: str
    can_issue: bool
    can_offer_choice: bool
    failed_checks: tuple[str, ...]
    pending_checks: tuple[str, ...]


def guarded_prnu(outcome: Outcome, *, trusted_reference: bool, supported_geometry: bool,
                 adequate_quality: bool, acceptance_calibrated: bool, rejection_calibrated: bool) -> Outcome:
    """Trusted verifier policy/quality inputs only; these flags authenticate no data."""
    if not isinstance(outcome, Outcome):
        raise ValueError("Expected an independent PRNU outcome")
    flags = (trusted_reference, supported_geometry, adequate_quality, acceptance_calibrated, rejection_calibrated)
    if any(type(flag) is not bool for flag in flags):
        raise ValueError("Expected explicit policy/quality booleans")
    if outcome not in (Outcome.PASS, Outcome.FAIL):
        return outcome
    enabled = acceptance_calibrated if outcome is Outcome.PASS else rejection_calibrated
    return outcome if all(flags[:3]) and enabled else Outcome.INCONCLUSIVE


def reduce_evidence(manufacturer: Outcome, prnu: Outcome, *, enrollment: Required,
                    binding: Required, signature: Required | None, anchor: Anchor,
                    issuance: bool = False) -> Decision:
    """None signature is allowed only for pre-signing issuance evaluation.

    Both origin checks are required inputs. Completed unavailable/inconclusive
    assessments permit the no-verification level; unfinished checks do not.
    Returned eligibility is not a signing/enrollment/validation operation.
    """
    if (not isinstance(manufacturer, Outcome) or not isinstance(prnu, Outcome)
            or not isinstance(enrollment, Required) or not isinstance(binding, Required)
            or not isinstance(anchor, Anchor) or type(issuance) is not bool
            or (issuance and signature is not None)
            or (not issuance and not isinstance(signature, Required))):
        raise ValueError("Invalid evidence evaluation inputs")
    checks = [("manufacturer", manufacturer), ("prnu", prnu),
              ("enrollment", enrollment), ("binding", binding)]
    if signature is not None:
        checks.append(("signature", signature))
    failed = tuple(name for name, status in checks if status in (Outcome.FAIL, Required.INVALID))
    if anchor is Anchor.FAILED:
        failed += ("anchor",)
    pending = tuple(name for name, status in checks if status in (Outcome.PENDING, Required.PENDING))
    if anchor is Anchor.UNRESOLVED:
        pending += ("anchor",)
    if failed:
        return Decision("failed", None, "Verifica fallita", False, False, failed, pending)
    if pending:
        return Decision("pending", None, "Verifica in corso", False, False, (), pending)
    level, label = ("manufacturer", "Verifica del produttore") if manufacturer is Outcome.PASS else (
        ("prnu", "Verifica PRNU") if prnu is Outcome.PASS else ("none", "Nessuna verifica"))
    return Decision("complete", level, label, issuance, not issuance, (), ())
