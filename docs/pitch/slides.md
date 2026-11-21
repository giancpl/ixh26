<!-- AGENT CONSTRAINT: Eight sparse main screens, INCLUDING one video and the author's AI disclosure, fit the existing 180-second total. Nine Q&A slides are outside the timed pitch. Keep Lagrange, mathematics and technical cautions; remove projected process notes, placeholders and video-duration/speed labels. The original video targets must remain. The author declares: theory Human; code/slides LLM-generated. Do not infer human code review. -->

# FingerprinTO

Large `assets/to.png` logo, project name, IXH26 Bari.

**A signature is not a sensor.**

<!-- SAY: 0:00-0:10. Apertura essenziale; logo grande. Sulle altre slide, logo piccolo nel footer. -->

---

# A valid signature does not identify the sensor

Sensor A -> Photo J -> Phone B signs -> receipt + anchor.

Signature matches. Hash matches. Commitment timestamped.

**B never captured J.**

<!-- SAY: 0:10-0:40. Scenario ipotetico: non dire che tutti i controlli indipendenti d'origine passano. -->

---

# "Authentic" hides five different claims

| Claim | Strength / limit |
|---|---|
| Byte integrity | Cryptographic binding |
| Installation identity | Hardware-rooted key |
| Capture credential | Vendor-path dependent |
| Sensor compatibility | Statistical; copyable |
| Commitment inclusion | Not exposure time |

**Software can bind physical evidence. It cannot create it.**

<!-- SAY: 0:40-1:05. Non leggere tutta la tabella. Le cautele su scena/tempo/AI detection restano nel backup. -->

---

# The receipt binds installation, photo and evidence

Binary tree: FI / FP -> Node; Node / FD -> T -> R.

\[
\begin{aligned}
F_P &= D(\texttt{photo/evidence-v3},F_I,H(J),E)\\
T &= \mathrm{Node}(\mathrm{Node}(F_I,F_P),F_D)\\
R &= D(\texttt{fingerprinto/evidence-v3},T)\\
\sigma &= \mathrm{ECDSA\text{-}P256\text{-}SHA256}(sk_I,R)\\
C &= D(\texttt{receipt},R,\sigma),\qquad h_{\mathrm{OTS}}=H(C).
\end{aligned}
\]

\(H=\mathrm{SHA256}\), \(D(\ell,x_1,\ldots)=H(\mathrm{CBOR}([\ell,x_1,\ldots]))\), \(\mathrm{Node}(a,b)=D(\texttt{node},a,b)\).

**One 32-byte commitment binds the record.**

<!-- SAY: 1:05-1:30. Formule visive, non nuove frasi da pronunciare. DER esatto, non-hiding e linkabilita passano al backup del byte contract; non rimuoverli dal materiale tecnico. -->

---

# A valid token is not physical presence

Software experiment / physical validation pending.

\[
\begin{aligned}
b &= D(\texttt{beacon-session/1},c,ID_I),\quad c\gets\{0,1\}^{256}\\
p &= [\texttt{fingerprinto/beacon/1},H(pk_B),\lfloor t/10\rfloor,b]\\
\mathit{code} &= \mathrm{CBOR}([p,\mathrm{Sign}_{sk_B}(D(\texttt{beacon-code/1},p))]).
\end{aligned}
\]

| Checks | Does not detect |
|---|---|
| Key / signature / session / slot | Splicing, relay, within-session replay |

**Token valid != physically verified. No level upgrade.**

<!-- SAY: 1:30-1:50. Non confondere il token software con l'ingresso del segnale attraverso l'ottica. -->

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
