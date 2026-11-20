# PhotoAnchor contracts v1

`openapi.json` is the shared HTTP contract. `fixtures/` contains a real 64×48
JPEG, a valid SDK-signed Nostr kind 20, its exact OTS payload and expected
SHA-256 values. All SDK operations use `nostr-sdk==0.45.1`. Fixture identity uses
the publicly known secret scalar 1 for tests only; never use that key in an app.

`vectors.json` covers Unicode captions, JSON quoting, backslashes, newline/tab,
empty captions and nonstandard tag order. Validate each event's ID **and**
signature with the official SDK. Kotlin must reproduce `payload_utf8` exactly
as UTF-8 and compare `payload_sha256`. JSON formatting/key order outside the
signed Nostr serialization is not significant; tag order is signed.

`negative/signature.json`, `negative/metadata.json` and `negative/photo.jpg`
alter one property of the basic fixture. They must fail signature, event ID
and photo integrity checks respectively. `profile.json` and
`profile-updated.json` use the same public key and different declared names;
updating the profile must not invalidate the original photo event.

The payload is the UTF-8 encoding, without whitespace or final newline, of:

```text
["upp-nostr-photo/1","<event.id>","<event.pubkey>","<event.sig>"]
```

Lowercase hex lengths are 64, 64 and 128. The event must validate before this
payload is constructed. This envelope is application specific, not NIP-03.

Python consumer API (`upp_protocol`):

- `validate_photo_event(event, *, expected_media_url=None, expected_sha256=None)`
  returns immutable `PhotoEvent` fields: `event_id`, `pubkey`, `signature`,
  `photo_sha256`, `photo_url`, `mime`, `width`, `height`, `event`.
- `build_payload(event) -> bytes` revalidates the signed photo event.
- `verify_photo_bytes(event, photo: bytes) -> PhotoEvent` additionally hashes
  the downloaded original JPEG. It never downloads the signed URL itself.
- `validate_profile_event(event) -> dict` validates kind 0 and JSON metadata.
- Invalid input raises `ProtocolError` with sanitized `code` and `message`.
- `MAX_MEDIA_BYTES` is 20 MiB; `MAX_POST_BYTES` is 16 KiB.

HTTP responses replace settlement's internal `proof_file` with `proof_url`.
`settlement: null` means no actual result yet. Example receipts are illustrative
metadata, **not evidence of an actual Bitcoin confirmation**. A reader verifies
the signed event, hashes the JPEG, reconstructs the payload, then verifies the
standard proof against its explicitly selected Bitcoin node/network.

Bundle ZIP members are `photo.jpg`, `event.json`, `payload.bin`, `proof.ots`,
and `README.txt`. A receipt may be included as optional untrusted metadata.
No user key belongs in this API or bundle.

Independent bundle verification (no API connection or receipt required):

```sh
photoanchor-bundle exported.zip --network regtest --bitcoin-conf /path/to/bitcoin.conf
# Equivalent: python -m upp_protocol.verify_bundle exported.zip ...
```

The verifier reads the expected ZIP entries without extracting arbitrary paths,
verifies ID/signature and JPEG hash, reconstructs and compares `payload.bin`,
then invokes local-only settlement verification of `proof.ots`. It prints JSON.
Exit codes match settlement verification: 0 confirmed, 2 pending, 3 invalid,
4 unverifiable, 1 local/configuration error. Mainnet remains the default; select
regtest explicitly. A valid `.ots` commitment is network neutral, so only the
explicitly configured node determines the Bitcoin network being verified.

Author name, caption and capture metadata remain declarations. These checks
establish key ownership of a signature, byte integrity and Bitcoin temporal
evidence; they do not establish the scene's authenticity or capture time.
