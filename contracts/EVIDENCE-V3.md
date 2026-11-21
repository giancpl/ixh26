# PhoTO application evidence-v3 contract

All new private endpoints require NIP-98 (`kind:27235`, empty content, exact
absolute public URL/method, SHA-256 payload including empty GET bodies, ±60 s).
Authorization events are single-use; retries sign a fresh event (random nonce
tag) and reuse the operation ID. Private records are scoped to the Nostr key.
Errors retain the existing `{error:{code,message}}` envelope.

IDs are 32 lowercase hex characters, digests 64 lowercase hex, binary fields
standard base64. JSON fields: `key`, `chain`, `package`, `version`, `signer`,
`manufacturer`, `model`, `time` (milliseconds), `challenge` (32-byte hex).
These map exactly to existing receipt metadata; no new receipt wire version.

| Method/path | Request / response |
|---|---|
| POST `/v1/enrollments/challenge` | `{operation_id}` → challenge, expiry, policy digest |
| POST `/v1/enrollments` | `{operation_id,fields,possession}` → persistent enrollment operation |
| GET `/v1/enrollments/{id}` | state, fields, signed authority record or reason |
| POST `/v1/calibrations` | `{operation_id,enrollment_id,geometry}` → private session |
| PUT `/v1/calibrations/{id}/frames/{role}/{sha256}` | Original JPEG; roles enrollment/development/genuine/impostor; disjoint hashes |
| POST `/v1/calibrations/{id}/evaluate` | `{}` → queued estimator/evaluation; no approval |
| GET `/v1/calibrations/{id}` | state, counts, reference hash, measured scores; no public frame URLs |
| POST `/v1/evidence` | `{operation_id,enrollment_id,media_sha256,time,reference_hash?}` → analysis operation |
| GET `/v1/evidence/{id}` | state, signed preparation envelope, bounded base64 template/policy/report |
| POST `/v1/evidence/{id}/receipt` | Exact signed receipt CBOR, ≤256 KiB; idempotent exact bytes |
| POST `/v1/posts/{event}/evidence/verify` | Fresh origin/trust and C proof verification, independent of Nostr OTS |
| GET `/v1/posts/{event}/evidence/proof` | Standard OTS for 32 raw bytes C, or proof_not_ready |
| GET `/v1/posts/{event}/evidence/bundle` | Standard evidence-v3 ZIP, independent from Nostr ZIP |

Operation state is QUEUED, PROCESSING, READY, COMPLETE, ERROR or UNAVAILABLE;
calibration begins at COLLECTING, and enrollment also uses ACCEPTED, REJECTED
and EXPIRED. Missing policy/root/revocation or
private authority material never accepts enrollment. Timeouts are recoverable;
preparation expiry requires a new operation before signing. Frozen signed bytes
are never replaced. GET requests never submit to calendars.

The preparation envelope is canonical JSON `{body,signature}`; `body` is the
exact JSON string. Its P-256 signature uses SHA256withECDSA over
`D("photo/evidence-preparation/1", SHA256(UTF8(body)))`. Body binds owner,
operation, image hash, R, template/policy/report hashes, issued and expires.
`template` has a placeholder DER signature and is explicitly UNSIGNED. Clients
reconstruct R from their original JPEG and check their key/app identity and
all attachments before signing. Template bytes must never be presented as a
valid receipt. Current trust is checked again on acceptance.

Before signing kind 20, append exactly one optional tag:
`["photo-evidence","fingerprinto/evidence-v3","<C>"]`.
The signed Nostr event endorses that exact receipt commitment for the same JPEG.
`POST /v1/posts` still accepts only `{event}`; no extra author key or signature.
Snapshots add nullable `evidence` with commitment, assessment, settlement,
proof_url and bundle_url. Old events retain evidence:null. Both OTS payloads
and their receipts remain distinct; the existing event envelope is unchanged.

Calibration frames/reference records are private. Thresholds are disabled until
an operator approves a trusted association and calibration report. No public
endpoint accepts approval, roots, trusted booleans or thresholds from the app.
