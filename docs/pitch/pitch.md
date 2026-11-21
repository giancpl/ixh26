<!-- AGENT CONSTRAINT: This file proposes the COMPLETE English pitch, including the crypto segment and demo direction. Absolute maximum: 180 seconds TOTAL, including the approximately 30-second demo, narration, pauses and slide transitions. Aim to finish by 175 seconds and reserve the last 5 seconds. The crypto block remains at most 90 seconds / 140 spoken words; its scheduled budget is 70 seconds, so it must NOT be extended to 90 without cutting another block. Keep the complete spoken script within 280 words, including demo narration, targeting a deliberate 110-120 words per minute. Only blockquotes are spoken; headings, tables, commands and operator directions are NOT spoken. Demo narration happens DURING the demo, not after it. Recount words after every edit and rehearse the entire sequence with a stopwatch before presenting; these timings are a plan, not a measured result. Coordinate the THREE crypto slides with slides.md; intro and closing slides below are proposed additions. Do not read formulas, invent product capabilities or present synthetic evidence as a real-device result. Preserve the distinction between implemented offline primitives, proposed workflow and unfinished integration/validation. -->

# Complete pitch proposal - three minutes including demo

## Beacon status - editorial only, not spoken

The external-beacon token experiment is now implemented separately on
`leomaster`; see [profile](../prnu/BEACON-EXPERIMENT.md) and
[validation record](../prnu/BEACON-VALIDATION.md). It is not the live demo below:
no real display/camera trial or complete beacon-to-v3 workflow has been validated.
Do not add a second demo or extend the 180-second plan. If replacing the demo
later, retain the 30-second slot and explicitly show synthetic/token-only status;
never call successful token decoding sensor origin or physical presence.
The native integration on later main also needs a separate status review before
presenting this script; this branch's old integration statements are not an audit
of those subsequent changes.

## Running order

| Clock | Budget | Purpose | Visual / speaker cue |
|---|---|---|---|
| 0:00-0:20 | 20 s | Problem: what can we actually trust about a photo? | Proposed slide 1: problem and intended workflow; product speaker |
| 0:20-0:40 | 20 s | Product: evidence travels with the original | Same slide; product speaker hands over to crypto speaker |
| 0:40-1:50 | 70 s | Crypto boundary, receipt and lasting verification | Existing three crypto slides become presentation slides 2-4 |
| 1:50-2:20 | 30 s | Demo: original evidence versus changed bytes | Prepared offline terminal / short screen recording; demo operator |
| 2:20-2:45 | 25 s | What exists and what the next experiment must establish | Proposed slide 5: implemented core / validation gates |
| 2:45-2:55 | 10 s | Close with the contribution | Same slide; product speaker |
| 2:55-3:00 | 5 s | Safety margin | Finish; no additional content |

Use at most five slides plus the demo view. With one presenter, omit speaker
handoffs; with several, include them inside these budgets. Do not add a separate
team introduction or an extra closing slide unless equivalent time is cut.

## 0:00-0:20 - Problem

> A photo can tell a convincing story. But who captured it, has it changed, and what evidence survives when the platform disappears? These are different questions. Too often, these become one label: authentic.

## 0:20-0:40 - Product and intended experience

> FingerprinTO is our evidence layer for that problem. The intended workflow is simple: keep the original, attach a portable receipt, and let another verifier inspect it. The result explains the evidence behind each claim.

## 0:40-1:50 - Crypto

<!-- CRYPTO SLIDE 1 (presentation slide 2): boundary and available evidence; approximately 35 seconds. -->

> Without a trusted capture path, no signature or blockchain can prove that an image came from a physical sensor. The problem happens before signing.

> Our answer is a single, verifiable format that accepts different sources of evidence and gives each capture the assurance it can support: manufacturer capture attestation where available, statistical compatibility through PRNU, or simply file integrity.

<!-- CRYPTO SLIDE 2 (presentation slide 3): receipt and anchoring; approximately 15 seconds. Do not read formulas. -->

> Our prototype binds the image, evidence and policy into a signed receipt, anchored through OpenTimestamps on Bitcoin. This records commitments, not the truth of the scene.

<!-- CRYPTO SLIDE 3 (presentation slide 4): proposed contribution and current status; approximately 20 seconds. Crypto block target: 70 seconds; absolute crypto maximum: 90 seconds. The full presentation must still finish within 180 seconds. -->

> Our proposed contribution is interoperability and lasting verification: preserving certificates, manifests and trust evidence, so verification need not disappear with a vendor.

> The crypto core is implemented. Real capture integrations and calibration remain unfinished.

## 1:50-2:20 - Demo

> Here is a synthetic signed example. Its signature checks out; sensor origin remains unverified. Change the file and verification fails. The receipt binds evidence to bytes. It cannot create evidence that was never there.

### Operator plan - not spoken

| Demo clock | Action | What the audience should see |
|---|---|---|
| 0-8 s | Open the prepared original fixture and its receipt; keep both verifier commands ready | Explicit label: synthetic example, not a live camera-origin demonstration |
| 8-18 s | Verify the original bundle using the independent CLI | Valid installation signature; missing trusted enrollment keeps the overall decision pending, not authenticated |
| 18-26 s | Verify a prepared copy with changed asset bytes and the unchanged receipt | Binding/signature failure; no acceptance of the modified file |
| 26-30 s | Hold the two results side by side while finishing the narration | Integrity and origin are separate findings |

Suggested existing entry point, with filenames to be prepared before presenting:

```bash
python -m fingerprinto v3 verify demo-original.zip
python -m fingerprinto v3 verify demo-modified.zip
```

Preparation is still required: use the existing synthetic v3 fixtures, preserve
the signed receipt and attachments, and label the fixture as synthetic. Without
a separately trusted enrollment, the original's overall result is expected to
remain pending even though its installation signature verifies. Keep that
outcome visible. Do not substitute `inspect` for verification or show a generic
green authenticity badge. Use a readable terminal with only the relevant fields
highlighted, preserving the actual verifier output.

This is a proposed demonstration, not a demo already executed in this revision.
Rehearse on the separate validation branch before choosing it for the stage.
If a real Android integration is subsequently validated, reassess this plan;
there is no validated live capture-to-signed-receipt demo in this branch.

Use a pre-recorded version of the same actual commands/results if live execution
is unreliable; label it as recorded. Keep its duration within the same 30 seconds.
Do not wait for Bitcoin confirmation, provision a phone key, perform calibration
or download dependencies on stage. If a timestamp proof is shown in a later
revision, prepare and verify it beforehand and identify its network; do not
claim instant confirmation for the photo shown live.

## 2:20-2:45 - Status and next experiment

> The offline crypto core is implemented. We still need real manufacturer validation, Android capture integration and sensor calibration. Our next milestone is a controlled trial across devices, including forged fingerprints, to establish which assurance each capture can actually support.

## 2:45-2:55 - Close

> We offer a verifiable record of evidence, with a path towards verification that outlasts its vendor.

## Rehearsal and cut rules - not spoken

- Count only the blockquotes as speech, including the demo narration. The crypto segment must remain within 140 words and 90 seconds; the full script must remain within 280 words and 180 seconds including the demo.
- Run the whole sequence, with actual clicks and handoffs, at least once with a stopwatch. Plan to finish by 2:55; do not infer compliance from word count alone.
- If running late, omit the second product sentence and shorten the status block to its first sentence. Retain the crypto limitations and the original/modified comparison. Do not add explanations while waiting for a command.
- Keep the demo to 30 seconds. On failure, switch immediately to the labelled recording; if unavailable, skip the demo and describe the intended comparison without claiming it ran successfully.
- Do not add formulas or extend the 70-second crypto block during questions or transitions; questions follow the timed pitch if the event allows them.
