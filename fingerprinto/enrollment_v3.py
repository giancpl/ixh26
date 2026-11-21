"""Enrollment before receipt issuance, with a separate domain-bound possession proof."""

from . import android_attestation
from .core import digest, domain
from .enrollment import ATTESTATION, EnrollmentResult, verify_enrollment
from .verification import Check, _certificate, verify_installation_signature
from .receipt_v3 import json_object
from .trust_v3 import sign_record


def possession_root(fields):
    if fields.sessionChallenge is None:
        raise ValueError("Enrollment challenge required")
    return domain("enrollment-possession/evidence-v3", digest(fields.pk_I), fields.packageName,
                  fields.versionCode, fields.signerDigest, fields.sessionChallenge)


def intake(fields, possession_signature, *, policy, requests, revocations, now):
    """Check possession/declarations/challenge before consuming fresh intake.

    An enrollment proof is not a photo receipt. No receipt is required before
    the installation is authorised to issue its first evidence-v3 receipt.
    """
    root = possession_root(fields)
    verify_installation_signature(fields.pk_I, possession_signature, root)
    cert = _certificate(fields.chain[0])
    att = android_attestation.parse(cert.extensions.get_extension_for_oid(ATTESTATION).value.value)
    if att.challenge != fields.sessionChallenge:
        raise ValueError("Possession/attestation challenge mismatch")
    if 709 not in att.software:
        raise ValueError("Attested application identity required")
    packages, signers = android_attestation.application_id(att.software[709])
    if packages.get(fields.packageName) != fields.versionCode or fields.signerDigest not in signers:
        raise ValueError("Declarations differ from attested application identity")
    if policy is None:
        raise ValueError("Verifier-owned enrollment policy required")
    if (fields.packageName not in dict(policy.packages) or fields.versionCode < dict(policy.packages)[fields.packageName]
            or fields.signerDigest not in policy.signer_digests):
        raise ValueError("Enrollment declarations fail release policy")
    return verify_enrollment(fields.pk_I, fields.chain, policy=policy, requests=requests,
                             revocations=revocations, now=now, possession_proof=(root, possession_signature))


def retain_accepted(fields, result, *, authority_private_key, store, sequence, not_before, expires):
    """Persist the authority's verified decision; never import result from a phone.

    Intake retains its decision atomically with challenge consumption; recover
    it using recover_intake if authority publication is interrupted. Publication
    remains separate and subject to current provisioning policy approval.
    Camera associations and thresholds start empty and need separate approval.
    """
    details = result.details
    if (result.enrollment.status != "accepted" or details.get("installation_id") != digest(fields.pk_I).hex()
            or details.get("attested_packages", {}).get(fields.packageName) != fields.versionCode
            or fields.signerDigest.hex() not in details.get("attested_signer_digests", [])):
        raise ValueError("Accepted verifier-owned enrollment decision required")
    _check_possession(fields, details)
    record = {"schema":"fingerprinto/enrollment-v3/1","installation":digest(fields.pk_I).hex(),
              "package":fields.packageName,"signer":fields.signerDigest.hex(),
              "minimumVersion":fields.versionCode,"sequence":sequence,"notBefore":not_before,
              "expires":expires,"status":"active","attestationPolicy":result.policy_sha256,"references":[]}
    def published():
        for original in store.history(digest(fields.pk_I)):
            body = store.authenticate(original)
            if body["sequence"] == sequence:
                if body != record:
                    raise ValueError("Publication sequence already has a different decision")
                # Refuse to resurrect an old grant after a later update/revocation.
                store.retain(original)
                return original
        return None
    existing = published()
    if existing is not None:
        return existing
    raw = sign_record(record,authority_private_key)
    try:
        store.retain(raw)
    except ValueError:
        # Another publisher may have committed the identical decision while
        # this process signed. Reuse its exact bytes, never replace its DER.
        existing = published()
        if existing is None:
            raise
        return existing
    return raw


def _check_possession(fields, details):
    root = possession_root(fields)
    if details.get("possession_root") != root.hex() or type(details.get("possession_signature")) is not str:
        raise ValueError("Retained v3 proof of possession required")
    verify_installation_signature(fields.pk_I, bytes.fromhex(details["possession_signature"]), root)


def recover_intake(fields, *, policy, requests):
    """Recover from the verifier-owned DB, not from a phone-supplied receipt.

    Returns the original evaluation time. Reassess current policy/revocation
    before publishing a grant; recovery is not a fresh acceptance decision.
    """
    if policy is None or requests is None or fields.sessionChallenge is None:
        raise ValueError("Trusted policy, challenge registry and challenge required")
    raw = requests.recover(fields.sessionChallenge, policy.digest, digest(fields.pk_I))
    if raw is None:
        return None
    obj = json_object(raw)
    obj["enrollment"] = Check(**obj["enrollment"])
    result = EnrollmentResult(**obj)
    if (result.details.get("attested_packages", {}).get(fields.packageName) != fields.versionCode
            or fields.signerDigest.hex() not in result.details.get("attested_signer_digests", [])):
        raise ValueError("Recovered decision application identity mismatch")
    _check_possession(fields, result.details)
    return result
