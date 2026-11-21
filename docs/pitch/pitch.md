<!-- AGENT CONSTRAINT: This file proposes the COMPLETE English pitch, including the crypto segment and demo direction. Absolute maximum: 180 seconds TOTAL, including the approximately 30-second demo, narration, pauses and slide transitions. Aim to finish by 175 seconds and reserve the last 5 seconds. The crypto block remains at most 90 seconds / 140 spoken words; its scheduled budget is 70 seconds, so it must NOT be extended to 90 without cutting another block. Keep the complete spoken script within 280 words, including demo narration, targeting a deliberate 110-120 words per minute. Only blockquotes are spoken; headings, tables, commands and operator directions are NOT spoken. Demo narration happens DURING the demo, not after it. Recount words after every edit and rehearse the entire sequence with a stopwatch before presenting; these timings are a plan, not a measured result. Coordinate the THREE crypto slides with slides.md; intro and closing slides below are proposed additions. Do not read formulas, invent product capabilities or present synthetic evidence as a real-device result. Preserve the distinction between implemented offline primitives, proposed workflow and unfinished integration/validation. -->

# Complete pitch proposal - three minutes including demo

## Beacon status - editorial only, not spoken

The external-beacon token experiment is now implemented separately on
`leomaster`; see [profile](../prnu/BEACON-EXPERIMENT.md) and
[validation record](../prnu/BEACON-VALIDATION.md). It is discussed on slide 4, but is not what the recorded app demonstration shows: no real display/camera trial or complete beacon-to-v3 workflow has been validated.
Do not add a second demo or extend the 180-second plan. If replacing the demo
later, retain the 30-second slot and explicitly show synthetic/token-only status;
never call successful token decoding sensor origin or physical presence.
The supplied app recording illustrates a later interface; this branch's old integration statements are not an audit of that code or device run.

## Running order

| Clock | Budget | Purpose | Visual / speaker cue |
|---|---|---|---|
| 0:00-0:20 | 20 s | Problem: what can we actually trust about a photo? | Proposed slide 1: problem and intended workflow; product speaker |
| 0:20-0:40 | 20 s | Product: evidence travels with the original | Same slide; product speaker hands over to crypto speaker |
| 0:40-1:50 | 70 s | Crypto boundary, receipt and lasting verification | Five properties (25 s), receipt (25 s), beacon boundaries (20 s): slides 2-4 |
| 1:50-2:20 | 30 s | Demo: recorded PhoTO app walkthrough | Supplied recording at 1.5x, 29.4 seconds; demo operator |
| 2:20-2:45 | 25 s | What exists and what the next experiment must establish | Presentation slide 6: what's next / validation gates |
| 2:45-2:55 | 10 s | Close with the contribution | Same final slide; product speaker |
| 2:55-3:00 | 5 s | Safety margin | Finish; no additional content |

Use five content slides plus the dedicated demo view (six PDF pages total). With one presenter, omit speaker
handoffs; with several, include them inside these budgets. Do not add a separate
team introduction or an extra closing slide unless equivalent time is cut.

## 0:00-0:20 - Counterexample

> Imagine one phone captures a photo and another signs it. The hash matches, the signature verifies, and the receipt can be timestamped. None of this proves that the signing phone captured the image.

## 0:20-0:40 - Contribution

> FingerprinTO separates those claims. We report what the evidence establishes, under which assumptions, rather than compressing everything into an authenticity badge.

## 0:40-1:50 - Crypto

<!-- SAY / SLIDE 2: 0:40-1:05. Le cinque righe separano proprieta diverse. Non leggere la tabella. -->

> We distinguish file integrity, installation identity, capture credentials, statistical sensor compatibility, and timestamped publication. Software can bind physical evidence; it cannot create it.

<!-- SAY / SLIDE 3: 1:05-1:30. Tre sottoalberi, una root, firma e commitment. Le formule restano visive. -->

> Our receipt commits to the image, installation metadata, evidence report and policy. We sign that root, preserve the exact signature bytes, and anchor a commitment through OpenTimestamps on Bitcoin.

<!-- SAY / SLIDE 4: 1:30-1:50. Il beacon e implementato come esperimento S, non prova H. Non rivendicare ingresso attraverso l'ottica. -->

> We also implemented an external beacon experiment. A separate device displays a signed code bound to a fresh session challenge and time slot. The verifier checks the token, but splicing and relay remain possible. A valid code is not proof of physical presence, and it does not upgrade the photo's origin level.

## 1:50-2:20 - Demo

> This recording shows capture preparation, private calibration controls and enrollment status. The authority pin is not configured, so enrollment remains unavailable. We show that state explicitly: the interface does not turn missing trust into verified origin.

### Operator plan - not spoken

Play [the supplied demo, adapted to 1.5x](assets/demo-pitch.mp4) from slide 5.
The 44.143-second [original recording](assets/video_2026-11-21_11-05-46.mp4)
is preserved unchanged. The pitch copy is silent and approximately 29.4 seconds;
read the narration above during playback. Both the recording and playback speed
are labelled on the slide. This replaces the earlier proposed synthetic
original/modified-bundle demonstration; that comparison is not shown in this video.

Before presenting, open the exported PDF from `docs/pitch` with its `assets`
folder alongside it. Click the preview or Play demo video button; they launch
`assets/demo-pitch.mp4` in an external player. Some PDF/browser viewers block
external launch actions. Open the MP4 in advance and use a direct player/window
switch if the link is blocked. The slide also links an [additional original recording](assets/video_2026-11-21_11-11-30.mp4)
(35.05 seconds, normal speed), showing enrollment status and camera capture.
It is optional material for questions, not a second clip to play in the timed
pitch; its unavailable trust/not-submitted states remain visible.

Return to slide 6 immediately after playback;
do not add another live test or repeat the original at normal speed.

The screen recording shows a local/regtest-labelled interface, not a verified
mainnet anchor. Its unavailable authority pin and enrollment state must remain
visible and must not be narrated as successful enrollment or physical origin.
No claim is made that the entire app/crypto pipeline ran successfully. The video
was supplied by the user; reviewing its frames is not an independent device test.
A real-time verifier test can replace this recording in a later rehearsal, but
is not part of the present 30-second slot.

## 2:20-2:45 - Status and next experiment

> The offline crypto core is implemented. Next, we want optimistic verification of richer claims and capture-bound camera challenges. Those need explicit dispute rules and a validated attested API. Real-device calibration remains essential.

## 2:45-2:55 - Close

> We offer a verifiable record of evidence, with a path towards verification that outlasts its vendor.

## Rehearsal and cut rules - not spoken

- Count only the blockquotes as speech, including the demo narration. The crypto segment must remain within 140 words and 90 seconds; the full script must remain within 280 words and 180 seconds including the demo.
- Run the whole sequence, with actual clicks and handoffs, at least once with a stopwatch. Plan to finish by 2:55; do not infer compliance from word count alone.
- If running late, omit the contribution paragraph and shorten the status block to its first sentence. Retain the crypto limitations and the recorded app walkthrough. Do not add explanations while waiting for a command.
- Keep the demo to 30 seconds. Pre-open the supplied recording; if its launch fails, switch to the player. If playback is unavailable, skip it without claiming it was shown.
- Do not add formulas or extend the 70-second crypto block during questions or transitions; questions follow the timed pitch if the event allows them.

## Camera challenges and optimistic verification - editorial rationale

The final slide replaces the previous status/closing screen; it does not extend
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

The source is [claude-pitch.md](claude-pitch.md), preserved as supplied. Its
8-10-minute outline is adapted to the existing three-minute budget: six timed
screens, including the unchanged supplied video, with technical detail in a
separate Q&A PDF. Do not present the backup during the timed pitch.

Corrections: four synthetic v3 fixtures already exist; beacon software is
implemented rather than merely proposed; decoded tokens do not prove optical
entry or proximity. The indistinguishability argument does not imply forging
vendor signatures or passing every independent check. Missing codes are
unavailable evidence, not proof that a pre-generated scene was detected.
Beacon windows require honest release/clock and independently justified anchor
time bounds; no precise exposure or Bitcoin wall-clock claim is made. The
draft's reported independent v1 recomputation is not newly verified here.
Team AI-use disclosure and human review remain [TBD] pending team completion.
