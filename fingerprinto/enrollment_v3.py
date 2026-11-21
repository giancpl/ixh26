"""Enrollment before receipt issuance, with a separate domain-bound possession proof."""

from . import android_attestation
from .core import digest, domain
from .enrollment import ATTESTATION, verify_enrollment
from .verification import _certificate, verify_installation_signature
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
    verify_installation_signature(fields.pk_I, possession_signature, possession_root(fields))
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
                             revocations=revocations, now=now)


def retain_accepted(fields, result, *, authority_private_key, store, sequence, not_before, expires):
    """Persist the authority's verified decision; never import result from a phone.

    The caller must durably retain/recover the intake result between challenge
    consumption and authority publication. This is not a distributed transaction.
    Camera associations and thresholds start empty and need separate approval.
    """
    details = result.details
    if (result.enrollment.status != "accepted" or details.get("installation_id") != digest(fields.pk_I).hex()
            or details.get("attested_packages", {}).get(fields.packageName) != fields.versionCode
            or fields.signerDigest.hex() not in details.get("attested_signer_digests", [])):
        raise ValueError("Accepted verifier-owned enrollment decision required")
    record = {"schema":"fingerprinto/enrollment-v3/1","installation":digest(fields.pk_I).hex(),
              "package":fields.packageName,"signer":fields.signerDigest.hex(),
              "minimumVersion":fields.versionCode,"sequence":sequence,"notBefore":not_before,
              "expires":expires,"status":"active","attestationPolicy":result.policy_sha256,"references":[]}
    raw = sign_record(record,authority_private_key)
    store.retain(raw)
    return raw
