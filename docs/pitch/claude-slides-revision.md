<!--
HANDOFF PER CODEX: revisione del Beamer `fingerprinto-pitch-technical` (17 pagine).
Obiettivo: meno testo, più incisivo, niente ridondanze, logo aggiunto, disclosure AI completata.
Lavora sul sorgente .tex esistente e mantieni la toolchain attuale. Non cambiare il contenuto tecnico: tagli, riformulazioni più corte, layout.
Queste istruzioni nascono dalla lettura delle 17 pagine del PDF generato il 21 novembre 2026 alle 11:33.
Non ho avuto accesso al sorgente né a `assets/`: i nomi dei file sotto vengono dal PDF e dall'autore.
-->

# Revisione slide FingerprinTO

## 1. Principi

- **Una idea per slide.** Il titolo è l'affermazione; il corpo la sostiene con formula, schema o tabella corta. Se una frase non serve a chi guarda mentre il relatore parla, tagliala.
- **La matematica resta** (è il suo scopo: il relatore non deve dirla a voce). Si tagliano le note, non le formule.
- **Più incisivo:** titoli corti e netti, una riga-slogan per slide al posto di paragrafi, più spazio bianco, tabelle con meno colonne e meno parole per cella. Evidenzia con il colore d'accento la riga che deve restare in mente.
- **Non indebolire le cautele** (`not detected`, `copyable`, `not established`): accorciale, non toglierle.
- **Non aggiungere affermazioni nuove.** Non cambiare numeri, SHA dei test (`8fb3b3c`), conteggi dei test né esiti. Non puoi verificarli, quindi riportali come sono.
- Rimuovi ogni testo che parla della slide stessa o del processo di modifica (vedi 4).

## 2. Da togliere ovunque

- La nota sulla **durata dei video**: `29.4 seconds / 1.5×` in alto a destra nella slide demo, e `(35 s)` nel pulsante del secondo video. Mantieni i pulsanti e **non cambiare i target dei link** (`assets/demo-pitch.mp4`, `assets/video_2026-11-21_11-11-30.mp4`).
- Le frasi di commento meta, ad esempio "No new crypto tests ran for this slide edit", "The draft's reported independent v1 recomputation was not repeated here", "The team must reconcile its actual contributions…".
- I `[TBD]` visibili al pubblico: sostituiscili con "not yet measured" / "pending" dove significano "non ancora misurato" (vedi 4, ultima riga).
- Le note a piè di pagina che ripetono ciò che dice già il titolo o la tabella.

## 3. Logo

Aggiungi il logo `assets/to.png` (cartella `assets/` accanto al `.tex`, la stessa dei video).
- **Slide 1:** grande, ben visibile, titolo "FingerprinTO" accanto o sotto.
- **Tutte le altre:** piccolo e fisso in un angolo (header o footer), senza toccare il testo né i titoli.
- Non conosco forma, sfondo o colori del file: guardalo prima e scegli il posizionamento (versione chiara/scura, margini). Mantieni le proporzioni; se ha sfondo trasparente, controlla il contrasto sul colore di sfondo scelto.
- Se il file manca, il deck deve comunque compilare (`\IfFileExists`) senza errori.

## 4. Slide per slide (pagine del PDF attuale)

**p.1 — titolo.** Oggi mescola titolo e problema. Fanne una vera slide di apertura: logo, "FingerprinTO", sottotitolo breve, "IXH26 Bari". Se vuoi un gancio, proponi una riga in stile "A signature is not a sensor." Il diagramma `Sensor A → Photo J → Phone B signs → Receipt + anchor` passa alla slide successiva. Elimina la nota "Only byte/signature/anchor checks are illustrated…".

**Nuova slide 2 — il problema.** Titolo: "A valid signature does not identify the sensor". Diagramma + tre spunte (firma ok, hash ok, ancoraggio ok) + una sola riga forte: "B never captured J." (resta l'idea "the claim is unsupported").

**p.2 — cinque claim.** Riduci la tabella a due colonne (claim, forza/limite) con frasi di 2–3 parole. Resta la riga forte "Software can bind physical evidence. It cannot create it." Togli la riga "No claim of scene truth…" (è già coperta nel backup).

**p.3 — ricevuta.** Tieni albero + formule `F_P, T, R, σ, C, h_OTS`. Riduci le due note sotto a una riga (definizione di `D` e `Node`). Sostituisci "Preserve the exact DER signature. C is not hiding; receipts are linkable." con una riga-slogan, ad esempio "One 32-byte commitment covers it all." La nota su DER e hiding va nel backup della byte contract.

**p.4 — beacon.** Tieni le tre formule. Sostituisci la riga di stato con una sola: "Software experiment · physical validation pending". Le due caselle (Checks / Does not detect) restano, con meno parole. Accorcia il piè: "Token valid ≠ physically verified. No level upgrade."

**p.5 — demo.** Titolo più forte, ad esempio "See it run". Screenshot a sinistra, tre punti a destra al massimo (Capture preparation / Private calibration controls / Device enrollment status), i due pulsanti video. Togli la durata (vedi 2). Riduci le tre righe grigie finali a una: "Local demo, Bitcoin regtest. Authority trust unavailable. No sensor claim."

**p.6 — what's next.** Due caselle, ma meno testo ciascuna. Titolo in maiuscole corrette ("What's next"). Unisci "Next validation" e il tag finale in una riga. **Togli la riga `Team AI-use disclosure / human review: [TBD]`**: la disclosure ha ora una slide propria (vedi 5).

**Backup (p.7–17)**, da 11 a circa 9 pagine:
- p.7 (modello, transcript identici): tieni, accorcia. Le due note finali diventano una: "Model-level argument, not a universal theorem."
- p.8 (software vs fisico, tabella): tieni, celle più corte. La riga forte finale resta ("A pixel pattern can be pasted. Its signature does not authenticate the lens.").
- p.9 (byte contract): tieni le formule; riduci le due note a una.
- p.10 (reducer): tieni la tabella; una sola riga per i codici 0–3 e la regola "a calibrated mismatch blocks a manufacturer pass".
- p.11 (PRNU): tieni la matematica; unisci le tre note finali in una riga di stato.
- p.12 (matrice attacchi): tieni la tabella; riduci le due note a una.
- p.13 (pubblicazione vs fiducia fisica): tieni le due caselle; accorcia il paragrafo "Proposed lasting verification…" e la nota sulla finestra temporale.
- **p.14 e p.16 (parsing/limiti e ibrido): fondile in una sola slide a due caselle**: a sinistra parsing stretto, limiti e profili isolati (`v1 / hybrid-v2 / evidence-v3`, nessun fallback), a destra ibrido `ML-DSA-65 AND Ed25519` con le sue cautele (chiavi in RAM, non hardware-native, no RSA, fiducia classica residua).
- p.15 (test): tieni la tabella com'è nei contenuti, con celle brevi. Come riga a piè: "Targeted regressions, not a full audit."
- La slide disclosure attuale (p.17) viene sostituita (vedi 5).

## 5. Slide disclosure AI (ultima della parte principale)

Sostituisci "The team must complete its AI-use disclosure" con una slide breve e diretta, ad esempio "AI-use disclosure", con tre righe:

| | |
|---|---|
| **Theory** | Human |
| **Code** | LLM-generated |
| **Slides** | LLM-generated |

Sotto, una riga piccola con gli strumenti: *OpenAI Codex, Claude.* (come nel deck attuale: Codex per modulo, documentazione, slide e artwork; Claude per analisi e bozze di pitch/slide).

Non aggiungere righe sulla revisione umana: non so se e come sia stata fatta, e finché l'autore non la dichiara non va scritta. Non attribuire strumenti diversi da quelli già nominati.

## 6. Verifica prima di consegnare

- Il PDF compila senza errori né overfull importanti; controlla **ogni pagina** a occhio, formule comprese.
- Nessun `[TBD]` visibile al pubblico, nessun riferimento alla durata dei video, nessuna nota sul processo di modifica.
- Il logo compare sulla slide 1 e piccolo sulle altre, senza sovrapporsi a titoli o numeri di pagina.
- I link video funzionano con gli stessi target di prima.
- Ricontrolla che nessuna cautela tecnica sia sparita: `not detected`, `copyable`, `not established`, `not hardware-native`, `no RSA`.
- Nel registro di lavoro scrivi cosa hai cambiato e cosa hai controllato davvero, senza attribuirti verifiche non fatte.
