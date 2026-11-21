<!-- AGENT CONSTRAINT: Eight sparse main screens, INCLUDING one video and the author's AI disclosure, fit the existing 180-second total. Nine Q&A slides are outside the timed pitch. Keep Lagrange, mathematics and technical cautions; remove projected process notes, placeholders and video-duration/speed labels. The original video targets must remain. The author declares: theory Human; code/slides LLM-generated. Do not infer human code review. -->

# FingerprinTO

Large `assets/to.png` logo, project name, IXH26 Bari.

**A signature is not a sensor.**

<!-- SAY: 0:00-0:10. Apertura essenziale; logo grande. Sulle altre slide, logo piccolo nel footer. -->

---

# Capture. Sign. Share. Check.

Capture -> prepare JPEG -> sign post -> publish.

Keep the original. Sign the JPEG selected for publication.
Readers check signature, downloaded bytes and timestamp independently.

**The evidence travels with the photo.**

<!-- SAY: 0:10-0:40. Flusso effettivo della app: nessun sensore A/telefono B ipotetico. La pubblicazione ordinaria puo usare una copia pulita; il percorso v3 opzionale firma l'originale. Non promettere che ogni originale resti privato anche quando l'utente decide di pubblicarlo. -->

---

# Verify the post. Verify the bytes.

\[
h_J=\mathrm{SHA256}(J_{\rm published}),\qquad
\sigma_N=\mathrm{SchnorrSign}_{sk_N}(\mathit{id}(E_N)).
\]

The signed Nostr event EN contains the selected JPEG hash.

| Signed post | Matching JPEG | Timestamp evidence |
|---|---|---|
| Event ID + signature | Download + hash | OTS + selected chain |

**Three separate results. No automatic sensor-origin claim.**

<!-- SAY: 0:40-1:05. Questo e il modello implementato della app: evento Nostr, byte della foto e prova temporale. Non confondere la chiave Nostr con la chiave P-256 del dispositivo. -->

---

# Add a device receipt when evidence is available

Original + report + policy -> root R -> receipt.

\[
\sigma_I=\mathrm{ECDSA\text{-}P256\text{-}SHA256}(sk_I,R),\qquad
C=D(\texttt{receipt},R,\sigma_I).
\]

Device key != Nostr profile key.

**Optional v3 evidence. Independent origin assessment.**

<!-- SAY: 1:05-1:30. Percorso aggiuntivo, non una condizione implicita di ogni post. Tutte le formule di dettaglio restano nel backup, non sono lette a voce. -->

---

# One Merkle root. Many timestamped commitments.

Payload digest + other digests -> Merkle root -> Bitcoin block.

Keep payload + .ots proof. Recompute the path. Verify Bitcoin.

P is the exact application payload: signed-event envelope for Nostr, raw C for optional v3.
The diagram is schematic; actual operations and attestation are specified by the .ots proof.

**Calendar promise != confirmed anchor. Block time != shutter time.**

<!-- SAY: 1:30-1:50. OpenTimestamps e il punto centrale: aggregazione, prova portabile e verifica Bitcoin. Il beacon non e nel percorso principale. -->

---

# See it run

Latest poster at left. Capture preparation / Calibration controls / Submission status.

- [Play latest demo](assets/demo-latest.mp4)
- [Earlier walkthrough](assets/demo-pitch.mp4)
- [Camera recording](assets/video_2026-11-21_11-11-30.mp4)

**Recorded app demo. Queued != verified. No sensor claim.**

<!-- SAY: 1:50-2:20. Un solo video durante il pitch. La copia del nuovo originale delle 11:42 e adattata al tempo disponibile; dettagli di durata/velocita solo in pitch.md e nel registro, non sullo schermo. Non cambiare i due vecchi target. Preaprire il player se il lettore PDF blocca il lancio. -->

---

# What's next

| Optimistic verification | Camera challenges |
|---|---|
| Richer claims. Open challenges. | Challenge + image, attested together. |
| claim -> evidence -> verdict | c <- {0,1}^256 |
| Dispute rules / incentives: pending. | Attested API not validated on our route. |

Next: manufacturer adapters + physical PRNU/beacon trials.

**Verify what the evidence supports.**

<!-- SAY: 2:20-2:50. Nessuna pretesa di verificare qualsiasi fatto senza evidenza. Non inventare API o ragioni storiche: manca un binding attestato validato. -->

---

# AI-use disclosure

| | |
|---|---|
| Theory | Human |
| Code | LLM-generated |
| Slides | LLM-generated |

OpenAI Codex, Claude.

<!-- SAY: 2:50-2:55. Dichiarazione fornita dall'autore. Non aggiungere una revisione umana del codice non dichiarata. Restano cinque secondi di margine. -->

## Nine technical backups - outside the timed pitch

1. Identical transcripts / model boundary.
2. Software versus physical evidence; pasted pixels do not authenticate optics.
3. Full FI/E/FD formulas; raw R, exact DER, non-hiding and linkability.
4. Reducer table / codes / calibrated mismatch blocking.
5. PRNU estimator and SPCE; copyable, real rates not yet measured.
6. Attack matrix / explicit not-detected outcomes.
7. Publication versus physical trust / conditional content-time bound.
8. Combined parser bounds and hybrid-v2: AND verification, keys in RAM, not hardware-native, no RSA, classical trust dependencies.
9. Existing v1/v3 fixtures and scoped results at `8fb3b3c`; targeted regressions, not a full audit.

Sources: [original Claude outline](claude-pitch.md) and
[author-supplied revision](claude-slides-revision.md), preserved unchanged.
Build/validation provenance belongs in [the work log](presentation/REVISION-LOG.md),
not on projected pages.

<!-- MERGE INTEGRATION NOTE: main now includes native v3 software integration and recorded real Pixel acceptance. See ../V3-APP-INTEGRATION.md and ../reviews/PIXEL9_PRO_2026-11-21.md. These later results are separate from the supplied recorded UI footage and the beacon's unvalidated physical path. The eight-screen reviewed deck remains the presentation source. -->
