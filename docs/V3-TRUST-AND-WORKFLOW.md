# v3 authority records and offline workflow

`trust_v3.TrustStore` retains P-256 authority-signed enrollment records in a
verifier-owned SQLite database. Pin the authority SPKI through deployment,
separately from the phone key, bundle, policy and report. Each lookup checks the
signature again, installation hash, release policy, validity and current status.
Higher sequence updates replace older records; rollback/conflicting updates are
rejected. Replaying an identical record is idempotent. Local database rollback
by its administrator is outside this mechanism; protect backups and freshness
when deploying it. Expired/missing records keep verification pending.

The authority approves installation enrollment **after** the existing Android
attestation, proof-of-possession, revocation and one-use-challenge checks. A
`sign_record` call is an administrative primitive; it does not perform those
checks or turn synthetic test chains into Android attestations. Reference
associations additionally require independent physical-camera provisioning and
separate calibration approval. Never let the phone hold this authority key.

Record schema `fingerprinto/enrollment-v3/1` binds installation SHA-256, package,
APK signer digest, minimum version, sequence, validity, active/revoked status,
attestation-policy digest, and optional reference associations. Each association
binds refHash, complete geometry/decoder, approved policy hash, calibration
evidence hash, and independently optional acceptance/rejection thresholds. Null
disables the corresponding decision region. Updating policy/thresholds requires
another authenticated authority record; a bundled policy cannot approve itself.

`workflow_v3.issue_v3` evaluates manufacturer and PRNU independently on the same
bytes, checks current enrollment, builds policy/report/E and R, invokes the
installation signing callback and verifies the returned signature. It then
persists exactly one receipt per operation/request in the existing journal.
Retries reuse stored bytes; changed input/policy/report/key produces a conflict.
The producer performs no external submission. Preserve the returned original
asset, credential and attachments together with the journalled receipt. Reference
records are published separately; this function does not capture/calibrate a phone.

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
