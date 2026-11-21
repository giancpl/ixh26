# v3 authority records and offline workflow

Integration status: this source-branch document predates the
[executed integration review](../reviews/LEOMAIN_PR9.md). Statements below about
awaiting execution describe that earlier snapshot. Hardware/camera acceptance
remains open; see the [PhoTO documentation map](../README.md).

`trust_v3.TrustStore` retains P-256 authority-signed enrollment records in a
verifier-owned SQLite database. Pin the authority SPKI through deployment,
separately from the phone key, bundle, policy and report. Each lookup checks the
signature again, installation hash, release policy, validity and current status.
Higher sequence updates change the current record and retain the original
signed versions in a separate history table; conflicting updates are rejected.
Current/history disagreement fails closed. Existing stores migrate their current
record only; previously overwritten history is unavailable. Replaying an
identical record is idempotent. Local whole-database rollback
by its administrator is outside this mechanism; protect backups and freshness
when deploying it. Expired/missing records keep verification pending.

The authority approves installation enrollment **after** the existing Android
attestation, proof-of-possession, revocation and one-use-challenge checks. A
`sign_record` call is an administrative primitive; it does not perform those
checks or turn synthetic test chains into Android attestations. Reference
associations additionally require independent physical-camera provisioning and
separate calibration approval. Never let the phone hold this authority key.

`enrollment_v3.intake` accepts a separate P-256 possession proof, so enrollment
does not require issuing a photo receipt first. Python/Java share the root
`D("enrollment-possession/evidence-v3", ID_I, package, version, signerDigest,
challenge)`. The challenge must match the attestation and the declarations
must match its application identity before the registry is consumed. After
acceptance, `retain_accepted` signs/retains an installation-only authority record.
The intake now stores the original accepted decision and proof in the challenge
database in the same transaction as consumption. `recover_intake` can recover
it after interruption before publication, scoped to key/challenge/policy/app
identity; an acceptance lacking the v3 possession proof cannot be promoted.
`retain_accepted` reuses an existing identical publication's original bytes and
refuses to resurrect older grants. Recovery preserves the original evaluation
time; the provisioner must reassess current policy/revocation before publication.
Older consumed challenges without retained decisions cannot be reconstructed.
The trust database/publication remains separate from intake; this is not a
cross-service transaction. Physical-camera/reference associations remain empty
until separately approved; installation attestation alone does not prove them.

Record schema `fingerprinto/enrollment-v3/1` binds installation SHA-256, package,
APK signer digest, minimum version, sequence, validity, active/revoked status,
attestation-policy digest, and optional reference associations. Each association
binds refHash, complete geometry/decoder, approved policy hash, calibration
evidence hash, and independently optional acceptance/rejection thresholds. Null
disables the corresponding decision region. Updating policy/thresholds requires
another authenticated authority record; a bundled policy cannot approve itself.
For PRNU decisions the signed policy must have schema
`fingerprinto/policy-v3/1` and a `prnu` object containing the exact method,
geometry, accept/reject thresholds and calibrationHash from the approved
association. Its raw-byte hash must also agree. A later policy update makes an
old snapshot unapproved, not corrupt; it does not change the old signed claims.

`workflow_v3.issue_v3` evaluates manufacturer and PRNU independently on the same
bytes, checks current enrollment, builds policy/report/E and R, invokes the
installation signing callback and verifies the returned signature. It then
persists exactly one receipt per operation/request in the existing journal.
Retries reuse stored bytes; changed input/policy/report/key produces a conflict.
The producer performs no external submission. Preserve the returned original
asset, credential and attachments together with the journalled receipt. Reference
records are published separately; this function does not capture/calibrate a phone.
Attachment/schema checks and aggregate limits (reserving the longest allowed
signature) run before signing, so an oversized report does not create an
unexportable journalled receipt. These latest audit corrections await execution
testing on the dedicated branch; see `../implementation/CRYPTO-MODULE-AUDIT.md`.

`verify_v3` reconstructs and verifies the signed receipt, re-evaluates both origin
checks using caller-configured validators/store/reference retrieval, and reports
signed claims separately from current findings. A missing external reference
withholds PRNU evidence; a supplied reference with the wrong digest fails.
No configured manufacturer validator means no manufacturer pass. The default
adapter detects some JPEG JUMBF/C2PA candidates and evidence-selection conflicts;
it is **not** a C2PA verifier or a complete catalogue of manufacturer formats.
A deployment adapter must inspect original embedded evidence regardless of the
declared placement and validate issuer, signature, binding and capture semantics.

The current executable workflow is offline Python. Java exposes matching receipt,
reducer and PRNU numeric primitives; the actual Android capture/calibration UI,
decoder route, authority provisioning service and upload lifecycle remain separate
integration work. No app build, SDK licence acceptance or XRPL is required here.

`personal_choice.PersonalChoices` stores optional trust/distrust locally against
the exact C, only when the reducer allows the choice. Its database is separate
from exported evidence and is never read by verification. A previously stored
choice cannot change a later failed finding or raise the evidence level.
