<!-- AGENT CONSTRAINT: This file proposes the COMPLETE English pitch, including the crypto segment and demo direction. Absolute maximum: 180 seconds TOTAL, including the approximately 30-second demo, narration, pauses and slide transitions. Aim to finish by 175 seconds and reserve the last 5 seconds. The crypto block remains at most 90 seconds / 140 spoken words; its scheduled budget is 70 seconds, so it must NOT be extended to 90 without cutting another block. Keep the complete spoken script within 280 words, including demo narration, targeting a deliberate 110-120 words per minute. Only blockquotes are spoken; headings, tables, commands and operator directions are NOT spoken. Demo narration happens DURING the demo, not after it. Recount words after every edit and rehearse the entire sequence with a stopwatch before presenting; these timings are a plan, not a measured result. Coordinate all eight main screens with slides.md; nine backup slides are outside the timed pitch. Do not read formulas, invent product capabilities or present synthetic evidence as a real-device result. Preserve the distinction between implemented offline primitives, proposed workflow and unfinished integration/validation. -->

# Complete pitch proposal - three minutes including demo

## Beacon status - editorial only, not spoken

The external-beacon token experiment is now implemented separately on
`leomaster`; see [profile](../prnu/BEACON-EXPERIMENT.md) and
[validation record](../prnu/BEACON-VALIDATION.md). It is discussed on slide 5, but is not what the recorded app demonstration shows: no real display/camera trial or complete beacon-to-v3 workflow has been validated.
Do not add a second demo or extend the 180-second plan. If replacing the demo
later, retain the 30-second slot and explicitly show synthetic/token-only status;
never call successful token decoding sensor origin or physical presence.
The supplied app recording illustrates a later interface; this branch's old integration statements are not an audit of that code or device run.

## Running order

| Clock | Screen | Purpose |
|---|---|---|
| 0:00-0:10 | 1 | Logo, FingerprinTO, opening claim |
| 0:10-0:40 | 2 | Signing phone versus capturing sensor |
| 0:40-1:05 | 3 | Five separate claims |
| 1:05-1:30 | 4 | Tree, root, signature and commitment |
| 1:30-1:50 | 5 | Beacon token and physical-origin limits |
| 1:50-2:20 | 6 | Latest supplied app recording |
| 2:20-2:50 | 7 | What's next and closing contribution |
| 2:50-2:55 | 8 | AI-use disclosure |
| 2:55-3:00 | - | Buffer |

Eight short main screens, including the demo and disclosure. Nine technical
backup slides are outside the timed pitch. Do not add explanations of the
formulas or play more than one video in the main slot.

## 0:00-0:10 - Opening

> FingerprinTO reports what the evidence establishes, rather than compressing everything into an authenticity badge.

## 0:10-0:40 - Counterexample

> Imagine one phone captures a photo and another signs it. The hash matches, the signature verifies, and the receipt can be timestamped. None of this proves that the signing phone captured the image.

## 0:40-1:50 - Crypto

<!-- SAY / SLIDE 3: 0:40-1:05. Le cinque righe separano proprieta diverse. Non leggere la tabella. -->

> We distinguish file integrity, installation identity, capture credentials, statistical sensor compatibility, and timestamped publication. Software can bind physical evidence; it cannot create it.

<!-- SAY / SLIDE 4: 1:05-1:30. Tre sottoalberi, una root, firma e commitment. Le formule restano visive. -->

> Our receipt commits to the image, installation metadata, evidence report and policy. We sign that root, preserve the exact signature bytes, and anchor a commitment through OpenTimestamps on Bitcoin.

<!-- SAY / SLIDE 5: 1:30-1:50. Il beacon e implementato come esperimento S, non prova H. Non rivendicare ingresso attraverso l'ottica. -->

> We also implemented an external beacon experiment. A separate device displays a signed code bound to a fresh session challenge and time slot. The verifier checks the token, but splicing and relay remain possible. A valid code is not proof of physical presence, and it does not upgrade the photo's origin level.

## 1:50-2:20 - Demo

> The app captures a photo and prepares a pin for publication. Here, the pin remains queued. We show that state explicitly: a queued signature is not confirmed publication, and neither proves sensor origin.

### Operator plan - not spoken

Play [the latest pitch copy](assets/demo-latest.mp4) from screen 6. It is derived
from the [11:42 original](assets/video_2026-11-21_11-42-32.mp4), preserved unchanged.
The original is approximately 35.17 seconds; the silent pitch copy at 1.2x is
approximately 29.3 seconds and fits the 30-second slot. Narrate during playback.
Timing/speed belongs in these notes, not on projected slides, as requested.

The older links `assets/demo-pitch.mp4` and
`assets/video_2026-11-21_11-11-30.mp4` remain unchanged on screen 6. They are
optional recordings, not additional clips to play during the timed pitch.
No original was trimmed or replaced. The latest poster is from 12 seconds.

Keep the exported PDF and its assets folder together. PDF/browser viewers may
block external Launch actions; pre-open the chosen MP4 in a player and switch
windows if needed. Return to screen 7 after playback. The video shows UI state,
including queued/unavailable operations, not independent confirmation of
successful publication, enrollment, mainnet anchoring or sensor provenance.
User-supplied footage and frame review do not constitute a new device test.

## 2:20-2:40 - Status and next experiment

> The offline crypto core is implemented. Next, we want optimistic verification of richer claims and capture-bound camera challenges. Those need explicit dispute rules and a validated attested API. Real-device calibration remains essential.

## 2:40-2:50 - Close

> We offer a verifiable record of evidence, with a path towards verification that outlasts its vendor.

## 2:50-2:55 - AI-use disclosure

> Human theory, LLM-generated code and slides.

## Rehearsal and cut rules - not spoken

- Count only the blockquotes as speech, including the demo narration. The crypto segment must remain within 140 words and 90 seconds; the full script must remain within 280 words and 180 seconds including the demo.
- Run the whole sequence, with actual clicks and handoffs, at least once with a stopwatch. Plan to finish by 2:55; do not infer compliance from word count alone.
- If running late, shorten the opening and keep only the first sentence of the status block. Retain the crypto limitations and the recorded app walkthrough. Do not add explanations while waiting for a command.
- Keep the demo to 30 seconds. Pre-open the latest pitch copy; if its launch fails, switch to the player. If playback is unavailable, skip it without claiming it was shown.
- Do not add formulas or extend the 70-second crypto block during questions or transitions; questions follow the timed pitch if the event allows them.

## Camera challenges and optimistic verification - editorial rationale

The future-work slide occupies the previous status/closing slot; it does not extend
the 180-second sequence. Optimistic verification means a proposed claim/evidence
and challenge process, with dispute rules and incentives still to design. It
does not mean verifying arbitrary statements about the world without evidence.

A useful camera challenge must be bound to the resulting image by a trusted
capture path. Standard request metadata is insufficient: Android documents
[`CaptureRequest.Builder.setTag`](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Builder#setTag(java.lang.Object))
as an application correlation tag that is not used by the camera device.
[`ACTION_IMAGE_CAPTURE`](https://developer.android.com/guide/components/intents-common#Camera)
specifies image capture/output, not an attested nonce-to-image contract. These
documented APIs do not by themselves supply the required guarantee. We have
not validated a vendor-specific attested API on our target route; compatibility
and trust-policy checks therefore precede this integration. This is an explicit
engineering/security dependency, not a claim that no such vendor API can exist
or an invented explanation of work already attempted.

For current experimental slots, the separately implemented external beacon is
not a substitute: its signed code can still be pasted or relayed. Its token
validity never proves physical presence. See the beacon profile and validation
record linked above.

## Claude source adaptation - editorial only

Sources are [claude-pitch.md](claude-pitch.md) and [the author-supplied revision](claude-slides-revision.md), preserved as supplied. The original
8-10-minute outline is adapted to the existing three-minute budget: eight timed
screens, including the latest supplied video, with technical detail in a
separate Q&A PDF. Do not present the backup during the timed pitch.

Corrections: four synthetic v3 fixtures already exist; beacon software is
implemented rather than merely proposed; decoded tokens do not prove optical
entry or proximity. The indistinguishability argument does not imply forging
vendor signatures or passing every independent check. Missing codes are
unavailable evidence, not proof that a pre-generated scene was detected.
Beacon windows require honest release/clock and independently justified anchor
time bounds; no precise exposure or Bitcoin wall-clock claim is made. The
draft's reported independent v1 recomputation is not newly verified here.
The author has now supplied the disclosure: theory is human, code and slides are LLM-generated. Tools named: OpenAI Codex and Claude. No assertion of human code review is added.
