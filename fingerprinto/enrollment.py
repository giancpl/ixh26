"""Independent enrollment intake for a restricted, pinned Android attestation path.

No roots, revocation snapshots or challenge records are accepted from the phone.
Accepted means a fresh enrollment intake, not historical re-verification or proof
of camera origin. Application/OS patch policy must be supplied by deployment.
"""

from dataclasses import asdict, dataclass, replace
from datetime import datetime
import hashlib
import json
import sqlite3

from cryptography import x509
from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import ObjectIdentifier, ExtensionOID

from . import android_attestation as der
from .enrollment_requests import EnrollmentRequests, instant
from .verification import Check, _certificate, load_installation_key

ATTESTATION = ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")
PROVISIONING = ObjectIdentifier("1.3.6.1.4.1.11129.2.1.30")
VERSIONS = {3: 4, 4: 41, 100: 100, 200: 200, 300: 300, 400: 400, 500: 500}


@dataclass(frozen=True)
class EnrollmentPolicy:
    identifier: str
    roots: tuple[bytes, ...]
    # Exact UID package set, with minimum enrollment-time version for each.
    packages: tuple[tuple[str, int], ...]
    signer_digests: frozenset[bytes]
    security_levels: frozenset[int] = frozenset({1, 2})
    minimum_os_version: int = 0
    minimum_os_patch: int = 0
    allowed_boot_keys: frozenset[bytes] | None = None

    def __post_init__(self):
        if (not self.identifier or not self.roots or not self.packages
                or len(dict(self.packages)) != len(self.packages)
                or any(not name or type(version) is not int or version < 0 for name, version in self.packages)
                or not self.signer_digests or any(type(d) is not bytes or len(d) != 32 for d in self.signer_digests)
                or not self.security_levels or not self.security_levels <= {1, 2}
                or self.minimum_os_version < 0 or self.minimum_os_patch < 0):
            raise ValueError("Invalid enrollment policy")
        for root in self.roots:
            _certificate(root)

    @property
    def digest(self) -> bytes:
        value = {"profile": "fingerprinto/android-enrollment/v1", "id": self.identifier,
                 "roots": sorted(hashlib.sha256(root).hexdigest() for root in self.roots),
                 "packages": sorted(self.packages), "signers": sorted(d.hex() for d in self.signer_digests),
                 "levels": sorted(self.security_levels), "os": self.minimum_os_version,
                 "patch": self.minimum_os_patch,
                 "boot_keys": None if self.allowed_boot_keys is None else sorted(k.hex() for k in self.allowed_boot_keys)}
        return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).digest()


@dataclass(frozen=True)
class RevocationSnapshot:
    """Verifier-owned status collected over an authenticated channel, never bundle JSON.

    The caller authenticates the source and chooses a validity interval. This
    object is not itself a signed status proof and cannot establish historical
    revocation state. Suspended certificates must also appear in blocked_serials.
    """
    blocked_serials: frozenset[int]
    observed_at: datetime
    valid_until: datetime
    evidence_sha256: bytes


@dataclass(frozen=True)
class EnrollmentResult:
    enrollment: Check
    policy_id: str | None
    policy_sha256: str | None
    evaluated_at: str
    details: dict

    def to_dict(self):
        return asdict(self)


class _Rejected(Exception):
    pass


class _Unsupported(Exception):
    pass


def _spki(certificate):
    return certificate.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


def _extension(certificate, oid):
    try:
        return certificate.extensions.get_extension_for_oid(oid)
    except x509.ExtensionNotFound:
        return None


def _path(chain: tuple[bytes, ...], policy: EnrollmentPolicy, now: datetime):
    if type(chain) is not tuple or not 2 <= len(chain) <= 8:
        raise _Rejected("certificate_path_invalid")
    certs = [_certificate(raw) for raw in chain]
    if len(set(chain)) != len(chain) or chain[-1] not in policy.roots:
        raise _Rejected("attestation_root_untrusted")
    for index, cert in enumerate(certs):
        if not cert.not_valid_before_utc <= now <= cert.not_valid_after_utc:
            raise _Rejected("certificate_time_invalid")
        if cert.signature_hash_algorithm is None or cert.signature_hash_algorithm.name not in {"sha256", "sha384", "sha512"}:
            raise _Unsupported("certificate_algorithm_unsupported")
        key = cert.public_key()
        if isinstance(key, rsa.RSAPublicKey):
            if key.key_size < 2048:
                raise _Rejected("certificate_key_strength_rejected")
        elif isinstance(key, ec.EllipticCurvePublicKey):
            if key.curve.name not in {"secp256r1", "secp384r1", "secp521r1"}:
                raise _Unsupported("certificate_curve_unsupported")
        else:
            raise _Unsupported("certificate_key_algorithm_unsupported")
        # Restricted ordered path, not a general RFC 5280 path builder. Refuse
        # constraints/EKU/etc we do not implement instead of ignoring them.
        for extension in cert.extensions:
            if extension.critical and extension.oid not in {ExtensionOID.BASIC_CONSTRAINTS, ExtensionOID.KEY_USAGE}:
                raise _Unsupported("critical_extension_unsupported")
            if extension.oid in {ExtensionOID.NAME_CONSTRAINTS, ExtensionOID.POLICY_CONSTRAINTS,
                                 ExtensionOID.INHIBIT_ANY_POLICY, ExtensionOID.EXTENDED_KEY_USAGE,
                                 ObjectIdentifier("2.5.29.33")}:
                raise _Unsupported("certificate_constraints_unsupported")
        if index:
            basic = cert.extensions.get_extension_for_class(x509.BasicConstraints).value
            usage = cert.extensions.get_extension_for_class(x509.KeyUsage).value
            if not basic.ca or not usage.key_cert_sign or (basic.path_length is not None and index - 1 > basic.path_length):
                raise _Rejected("certificate_ca_constraints_invalid")
            # Self-issued intermediates require more path processing; unsupported.
            if index < len(certs) - 1 and cert.subject == cert.issuer:
                raise _Unsupported("self_issued_intermediate_unsupported")
        if index < len(certs) - 1:
            cert.verify_directly_issued_by(certs[index + 1])
    return certs


def verify_enrollment(
    public_key: bytes, chain: tuple[bytes, ...], *, policy: EnrollmentPolicy | None,
    requests: EnrollmentRequests | None, revocations: RevocationSnapshot | None,
    now: datetime,
) -> EnrollmentResult:
    """Verify and atomically consume a fresh challenge only on full acceptance.

    Configuration/state/time are verifier-owned. This is enrollment intake;
    receipt verification must separately prove possession using the receipt
    signature and bind to the retained enrollment decision. No network is used.
    """
    instant(now)
    details = {}
    def result(status, code, message):
        return EnrollmentResult(Check(status, code, message), policy.identifier if policy else None,
                                policy.digest.hex() if policy else None, now.isoformat(), details)
    if policy is None:
        return result("indeterminate", "enrollment_policy_unconfigured", "Verifier trust policy is required.")
    try:
        load_installation_key(public_key)
        certs = _path(chain, policy, now)
        indexes = [i for i in reversed(range(len(certs))) if _extension(certs[i], ATTESTATION)]
        if not indexes:
            raise _Rejected("attestation_extension_missing")
        authoritative = indexes[0]
        # v1 accepts only the attested signing certificate as the path leaf.
        if authoritative != 0 or _spki(certs[authoritative]) != public_key:
            raise _Rejected("attested_key_mismatch")
        provisioning = [i for i in reversed(range(len(certs))) if _extension(certs[i], PROVISIONING)]
        if provisioning and provisioning[0] != authoritative + 1:
            raise _Rejected("provisioning_extension_position_invalid")
        leaf_usage = _extension(certs[0], ExtensionOID.KEY_USAGE)
        if leaf_usage is not None and not leaf_usage.value.digital_signature:
            raise _Rejected("leaf_signing_usage_invalid")
        att = der.parse(_extension(certs[authoritative], ATTESTATION).value.value)
        if att.version not in VERSIONS or att.keymaster_version != VERSIONS[att.version]:
            raise _Unsupported("attestation_version_unsupported")
        if att.attestation_level not in policy.security_levels or att.keymaster_level not in policy.security_levels:
            raise _Rejected("hardware_security_level_rejected")
        hw, sw = att.hardware, att.software
        # Security properties must be hardware-enforced, never substituted from SW.
        for tag, expected in ((2, 3), (3, 256), (10, 1), (702, 0)):
            if tag not in hw or der.number(hw[tag]) != expected:
                raise _Rejected("hardware_key_authorization_invalid")
        if 1 not in hw or der.numbers(hw[1]) != {2} or 5 not in hw or der.numbers(hw[5]) != {4}:
            raise _Rejected("hardware_signing_authorization_invalid")
        if any(tag in sw for tag in (1, 2, 3, 5, 10, 702, 704)):
            raise _Rejected("conflicting_software_authorization")
        if 704 not in hw:
            raise _Rejected("boot_evidence_missing")
        boot_key, locked, state, boot_hash = der.boot_state(hw[704])
        if not locked or state != 0 or not boot_key or not boot_hash:
            raise _Rejected("verified_boot_rejected")
        if policy.allowed_boot_keys is not None and boot_key not in policy.allowed_boot_keys:
            raise _Rejected("boot_key_untrusted")
        if 709 not in sw or 709 in hw:
            raise _Rejected("application_id_missing_or_conflicting")
        packages, signers = der.application_id(sw[709])
        if set(packages) != set(dict(policy.packages)) or any(packages[name] < minimum for name, minimum in policy.packages):
            raise _Rejected("application_package_policy_rejected")
        if len(signers) != 1 or not signers <= policy.signer_digests:
            raise _Rejected("application_signer_policy_rejected")
        for tag, minimum in ((705, policy.minimum_os_version), (706, policy.minimum_os_patch)):
            if minimum and (tag not in hw or der.number(hw[tag]) < minimum):
                raise _Rejected("os_policy_rejected")
        if len(att.challenge) != 32:
            raise _Rejected("enrollment_challenge_invalid")
        identity = hashlib.sha256(public_key).digest()
        details.update(installation_id=identity.hex(), attestation_version=att.version,
                       attestation_security_level=att.attestation_level, keymaster_security_level=att.keymaster_level,
                       attested_packages=packages, attested_signer_digests=sorted(s.hex() for s in signers),
                       trust_root_sha256=hashlib.sha256(chain[-1]).hexdigest())
        if revocations is None:
            return result("indeterminate", "revocation_evidence_missing", "Authenticated verifier-owned revocation evidence is required.")
        if (len(revocations.evidence_sha256) != 32
                or not instant(revocations.observed_at) <= instant(now) < instant(revocations.valid_until)):
            return result("indeterminate", "revocation_evidence_stale", "Revocation evidence is not current at evaluation time.")
        if any(cert.serial_number in revocations.blocked_serials for cert in certs):
            raise _Rejected("attestation_certificate_revoked")
        details["revocation_evidence_sha256"] = revocations.evidence_sha256.hex()
        if requests is None:
            return result("indeterminate", "challenge_registry_missing", "Verifier-owned issuance and consumption state is required.")
        if not requests.consume(att.challenge, policy.digest, identity, now=now):
            raise _Rejected("enrollment_challenge_unavailable")
        return result("accepted", "enrollment_accepted", "Attestation policy and fresh one-use enrollment challenge passed.")
    except _Unsupported as exc:
        return result("indeterminate", str(exc), "Attestation or path uses an unsupported verification profile.")
    except _Rejected as exc:
        return result("rejected", str(exc), "Enrollment evidence fails the verifier policy.")
    except (InvalidSignature, ValueError, TypeError, x509.ExtensionNotFound, x509.DuplicateExtension):
        return result("rejected", "enrollment_evidence_invalid", "Malformed evidence or invalid certificate signature.")
    except UnsupportedAlgorithm:
        return result("indeterminate", "enrollment_algorithm_unsupported", "Certificate algorithm is unsupported.")
    except (sqlite3.Error, OSError):
        return result("indeterminate", "enrollment_state_unavailable", "Verifier enrollment state is unavailable.")


def enroll_receipt(receipt_bytes: bytes, asset: bytes | None, credential: bytes | None = None,
                   *, policy: EnrollmentPolicy | None, requests: EnrollmentRequests | None,
                   revocations: RevocationSnapshot | None, now: datetime):
    """Fresh intake with receipt-signature proof of possession before consumption.

    Origin remains independent and indeterminate until a real validator is
    configured. This function does not issue a receipt or allow production
    issuance. Repeated receipt verification must use the retained enrollment
    record; do not run fresh intake again on an already-consumed challenge.
    """
    from .core import Receipt
    from .verification import verify_receipt

    instant(now)
    checked = verify_receipt(receipt_bytes, asset, credential)
    if checked.profile.status != "accepted" or checked.installation_signature.status != "valid":
        return replace(checked, enrollment=Check("indeterminate", "enrollment_possession_unproven",
                                                "A well-formed receipt with a valid installation signature is required."))
    receipt = Receipt.from_bytes(receipt_bytes)
    if policy is not None and (receipt.packageName not in dict(policy.packages)
            or receipt.versionCode < dict(policy.packages).get(receipt.packageName, 0)
            or receipt.signerDigest not in policy.signer_digests):
        return replace(checked, enrollment=Check("rejected", "receipt_app_policy_rejected",
                                                "Signed app declarations fail the verifier release policy."))
    enrollment = verify_enrollment(receipt.pk_I, receipt.chain, policy=policy, requests=requests,
                                   revocations=revocations, now=now)
    return replace(checked, enrollment=enrollment.enrollment,
                   enrollment_details=enrollment.to_dict(), evaluated_at=now.isoformat(),
                   verifier_profile="fingerprinto/v1-enrollment-intake")
