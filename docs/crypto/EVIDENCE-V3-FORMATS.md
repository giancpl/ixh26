# Evidence-v3 implementation formats

## Beacon compatibility note

The [beacon experiment](../prnu/BEACON-EXPERIMENT.md) is a standalone token
report, not a v3 attachment or a third member of E. Existing receipt bytes,
member lists and result codes are unchanged. The verifier never upgrades a
manufacturer/PRNU result from token validity. Adding diagnostic fields before
freezing `report.json` is a future integration option, not implemented issuance
wiring; editing a frozen report invalidates its signed hash.


The normative tree and wire codes are in `crypto.md`. Version 3 is dispatched
explicitly as `evidence-v3`; no v1/v2 fallback is attempted. A parsed receipt or
a valid installation signature does not authenticate its origin findings.

## Attachments

`policy.json` and `report.json` preserve their exact UTF-8 bytes, each at most
1 MiB, within the existing 160 MiB aggregate bundle limit. Reject duplicate keys
at any depth, nonfinite numbers (including overflow), invalid Unicode, nesting
beyond 16 levels, and non-object roots. Check size and SHA-256 before parsing.
Policy content is a snapshot, never verifier-owned trust configuration.

Report schema `fingerprinto/report-v3/1` requires `manufacturer` and `prnu`
objects, each with a `status` string (`unavailable`, `inconclusive`, `pass`,
`fail`) and a nonempty `reason`. `referenceHash` is lowercase hex or null;
`policyHash` is lowercase hex. They must agree with E. Additional diagnostics
can carry camera, geometry, method, quality, score and threshold data; those
fields become mandatory for the corresponding implemented method. A missing
external reference prevents independent PRNU evaluation, but does not corrupt
the signed report. A supplied reference must match refHash.

ZIP adds only `policy.json` and `report.json` to the existing fixed member list.
Reference material is obtained separately by an authorised verifier; it is not
silently trusted or exposed by the bundle. Legacy profiles reject v3 attachments.

## Current validation boundary

Python performs strict receipt decode, tree reconstruction, signature checking,
attachment validation and bounded ZIP round trips. Java constructs identical
v3 receipts/roots with streaming file hashes and defensive input copies. Java
also strictly decodes the CBOR receipt and verifies its installation signature.
Signed JSON attachment validation/current origin and enrollment evaluation use
the independent Python verifier; Java CBOR import alone is not acceptance.
The four v3 golden vectors are synthetic, including
their claimed pass codes; they establish byte interoperability, not attribution.
