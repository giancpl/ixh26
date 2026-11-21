<!-- AGENT CONSTRAINT: Adapt claude-pitch.md to the user's 180-second TOTAL pitch, including a 29.4-second recorded demo. Six timed screens only; extended technical slides are a separate Q&A edition and MUST NOT be presented during the pitch. Preserve Lagrange, readable mathematics/tables and sparse prose. Spoken text is in pitch.md; SAY comments are Italian presenter notes. Update source claims to the reviewed repository; no invented device results or universal physical-origin guarantee. -->

# A valid signature does not identify the sensor

FingerprinTO: evidence for photo origin, with explicit assurance levels.

Sensor A -> photo J -> phone B signs -> receipt + anchor.

\(\mathrm{Verify}(pk_B,R,\sigma_B)=1\), image hash matches, commitment can be timestamped.

**The claim that B captured J is still unsupported.**

<!-- SAY: 0:00-0:40. Controesempio, non una dimostrazione che ogni controllo d'origine passa. Un attaccante puo invocare la chiave senza estrarla dal Keystore. -->

---

# "Authentic" hides five different claims

| Property | Evidence | Boundary |
|---|---|---|
| Byte integrity | SHA-256 + signature | Cryptographic |
| Installation identity | Key attestation + possession | Hardware trust chain |
| Capture credential | Trusted vendor + asset binding | Vendor capture path |
| Sensor compatibility | PRNU correlation | Statistical; copyable |
| Commitment inclusion | Bitcoin / OpenTimestamps | Not exposure time |

**Software can bind physical evidence. It cannot create it.**

No claim of scene truth, shutter time or AI-content detection. Missing evidence never becomes a pass.

<!-- SAY: 0:40-1:05. Non leggere le cinque righe; distingui integrita, identita e origine. H e un'ipotesi sul processo fisico, non una proprieta autenticata da un pattern decodificato. -->

---

# The receipt binds installation, photo and evidence

Draw the binary tree FI / FP -> Node; Node / FD -> T -> R.

\[
\begin{aligned}
F_P &= D(\texttt{photo/evidence-v3},F_I,H(J),E)\\
T &= \mathrm{Node}(\mathrm{Node}(F_I,F_P),F_D)\\
R &= D(\texttt{fingerprinto/evidence-v3},T)\\
\sigma &= \mathrm{ECDSA\text{-}P256\text{-}SHA256}(sk_I,R)\\
C &= D(\texttt{receipt},R,\sigma),\qquad h_{\mathrm{OTS}}=H(C).
\end{aligned}
\]

\(D(\ell,x_1,\ldots)=H(\mathrm{CBOR}([\ell,x_1,\ldots]))\), \(\mathrm{Node}(a,b)=D(\texttt{node},a,b)\).

E binds check results and reference/policy/report hashes; FI / FD bind installation/device metadata.

**Preserve the exact DER signature. C is not hiding; receipts are linkable.**

<!-- SAY: 1:05-1:30. Le formule sono solo supporto visivo. La firma SHA256withECDSA riceve i 32 byte di R; OTS riceve un file con i 32 byte di C. Non chiamare anonimo un commitment senza hiding. -->

---

# A signed beacon code is not proof of physical presence

**IMPLEMENTED:** standalone software experiment. **PHYSICAL VALIDATION:** not established.

\[
\begin{aligned}
b &= D(\texttt{beacon-session/1},c,ID_I),\quad c\gets\{0,1\}^{256}\\
p &= [\texttt{fingerprinto/beacon/1},H(pk_B),\lfloor t/10\rfloor,b]\\
\mathit{code} &= \mathrm{CBOR}([p,\mathrm{Sign}_{sk_B}(D(\texttt{beacon-code/1},p))]).
\end{aligned}
\]

| Checks | Does not detect |
|---|---|
| Pinned key, signature, session, slot | Splicing, relay, within-session replay |

`status=pass` validates the token; `physicalOriginVerified=false`. No v3 level upgrade.

<!-- SAY: 1:30-1:50. Il beacon e implementato, non una semplice proposta. La verifica e di classe S: i pixel possono essere incollati. Il tempo riguarda contenuto con token sotto ipotesi fidate, non l'esposizione. Non rivendicare ingresso attraverso l'ottica o compatibilita con ogni fotocamera. -->

---

# Demo recording

**29.4 seconds / 1.5x - supplied PhoTO app walkthrough.**

[Preview](assets/demo-poster.png) / [play video](assets/demo-pitch.mp4).

Capture preparation. Private calibration controls. Device enrollment status.

Missing authority trust stays unavailable. Recorded local demo / Bitcoin regtest.
Sensor origin is not established.

<!-- SAY: 1:50-2:20. Video fornito invariato rispetto alla versione precedente, non una nuova prova del beacon o di tampering. L'originale e conservato; la copia da pitch e muta a 1.5x. Preaprire il player: il lettore PDF puo bloccare il link. -->

---

# what's next

| Optimistic verification | Capture-bound camera challenges |
|---|---|
| Richer claims. Independent challenges. | Challenge + image, attested together. |
| claim -> evidence -> verdict | Fresh c <- {0,1}^256 |
| Dispute rules and incentives not implemented. | No attested API validated on our capture route. Metadata alone is insufficient. |

Next validation: manufacturer adapters and physical PRNU/beacon trials.

Team AI-use disclosure / human review: **[TBD]**.

**Verify what the evidence supports.**

<!-- SAY: 2:20-2:55; 5 secondi di margine. Non inventare ragioni storiche per le API: manca la validazione di un binding attestato sul percorso scelto. Completare la disclosure del team prima della presentazione; non attribuire revisione umana mai confermata. -->

## Technical backup - outside the timed pitch

Build `presentation/extended.tex`; `main.tex` remains exactly six timed pages.

1. Identical capture transcripts: model-level boundary, not universal impossibility.
2. S/H classification and residual trust, including the optical-token correction.
3. Full FI/E/FD byte contract.
4. Reducer priorities, pending checks and calibration gates.
5. PRNU estimator and signed PCE; real error rates [TBD].
6. Attack matrix, including successful splicing/relay counterexamples.
7. Publication versus trust, proposed historical verification and conditional time windows.
8. Parser limits and profile isolation.
9. ML-DSA-65 AND Ed25519: software secrets in RAM, classical trust dependencies.
10. Four v1 and four v3 synthetic fixtures; scoped beacon/v3 results at the recorded SHA.
11. Known AI assistance and incomplete team/human-review disclosure.

The supplied [Claude source](claude-pitch.md) remains unchanged. Its outdated
v3/beacon status and overstrong origin/time claims are corrected in the rendered
slides, not retroactively attributed to the source. See [adaptation and build
record](presentation/README.md) for the source-to-deck mapping.
