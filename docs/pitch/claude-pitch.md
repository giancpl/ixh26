<!--
HANDOFF PER CODEX: contenuto ideale delle slide di FingerprinTO per il pitch IXH26.
SCOPO: le slide devono portare la matematica e le tabelle, così chi parla non deve dire tutto a voce.
I commenti <!-- SAY: ... --> sono per chi presenta: dicono cosa raccontare, non vanno sulla slide.
COSA FARE: costruisci le slide da questo file. Una idea per slide. Le formule grandi e leggibili, poco testo.
Se serve un diagramma, usa gli schizzi ASCII come guida e disegnali pulito.

REGOLE (non negoziabili senza decisione dell'autore):
- Non aggiungere affermazioni che non sono qui. Non scrivere "risolto", "dimostrato" o "autentico" dove il file non lo dice.
- Non inventare risultati, numeri o test su dispositivo. Dove manca un dato, lascia il segnaposto [TBD].
- I badge di stato ([IMPLEMENTATO], [PROPOSTA], [NON VERIFICATO]) vanno aggiornati allo stato reale della repo al momento
  della presentazione. Se non sei sicuro dello stato di qualcosa, mettilo come [NON VERIFICATO].
- Le cose marcate [VERIFY] vanno controllate prima di mostrarle (riferimenti alle slide IXH26 non letti direttamente, ecc.).
- Lingua del testo sulle slide: inglese (il pubblico è di crittografi). I commenti SAY sono in italiano. Se la giuria è italiana
  si può cambiare, ma mantieni la notazione matematica identica.
- Durata: le slide 1-12 sono il percorso principale (circa 8-10 minuti, adatta se il tempo è diverso). Le slide B1-B3 sono backup
  per le domande, non vanno presentate.
- Stile: sfondo chiaro, una sola famiglia di font, formule in monospazio o LaTeX reso, nessun muro di testo.
  Ogni slide ha un titolo che è una frase-affermazione, non un'etichetta.
- Disclosure AI: l'ultima slide la richiede l'evento (per quanto riportato dai nostri documenti, slide IXH26 p. 23 [VERIFY]).
  Non inventare le parti. Lasciala come segnaposto finché il team non la compila.
-->

# Slide 1 — We don't output "authentic". We output what is established, and under which assumption.

**On slide**

```
FingerprinTO
Evidence for photo origin, with explicit assurance levels

[TEAM NAMES]   ·   IXH26 Bari, crypto track
```

<!-- SAY: Apertura in 20 secondi. Non siamo qui a dire "abbiamo risolto la provenienza delle foto".
Siamo qui a dire cosa si può dimostrare e cosa no, e a darvi un formato che tiene separate le due cose.
Chi ha già sentito dieci pitch su "blockchain + foto autentiche" capisce subito che qui il tono è diverso. -->

---

# Slide 2 — A valid signature does not mean the photo came from that sensor

**On slide**

```
Phone A  captures a genuine photo J
Phone B  registers J and signs it

Sign(sk_B, R)   ✔ valid
Hash(J)         ✔ matches
Anchor(C)       ✔ timestamped

Every check passes. The origin claim is still unsupported.
```

Small diagram: `Sensor A → J → [Phone B: sign] → ✔✔✔`

<!-- SAY: Il controesempio più semplice. Tutto ciò che è crittografia, hash, firma, ancoraggio torna.
Eppure B non ha scattato niente. Il punto: firmare bene un file non dice da dove viene. La sfida (pagine 12 e 21 del
deck [VERIFY]) chiede proprio il legame con l'acquisizione fisica. Questo legame non segue dalle firme. -->

---

# Slide 3 — "Authentic" bundles five different properties

**On slide**

| | Property | Established by | Strength |
|---|---|---|---|
| P1 | Byte integrity | SHA-256 binding + signature | Cryptographic |
| P2 | Key possession / installation identity | Keystore attestation | Cryptographic, conditional on hardware trust chain |
| P3 | Capture credential | Manufacturer signature (e.g. C2PA) | Conditional on vendor capture path |
| P4 | Sensor compatibility | PRNU correlation | **Statistical, copyable** |
| P5 | Existed before time t | Bitcoin/OTS anchor of the receipt | Cryptographic for the commitment only |

Not claimed: truth of the scene · shutter time · AI-generated content detection.

<!-- SAY: Ogni riga è una cosa diversa, con una forza diversa. Il nostro lavoro è non confonderle.
Una foto di uno schermo è una cattura fisica autentica di contenuto sintetico: origine della cattura e verità della scena
sono problemi distinti. Dillo in una frase e passa oltre. -->

---

# Slide 4 — Without a trusted observation point inside the capture path, strong origin cannot be proven

**On slide**

```
Model.   Adversary controls the client (OS + app) and holds an enrolled sk_I.
         Verifier sees only (J, receipt, bundle).

Claim.   For any file J', the adversary can produce a bundle that passes
         every software-computable check.

⇒  A strong guarantee needs an observation point the adversary does not
   control, located BEFORE the bytes become attacker-chosen.
```

Footer: *A model-level argument, not a universal impossibility theorem.*

<!-- SAY: Questo è il cuore teorico. Se il client è ostile, firma quello che vuole. Una blockchain non cambia nulla:
ancora ciò che le viene dato, anche una dichiarazione iniziale falsa. Un quorum di registratori non rende vera una
registrazione falsa. Sii preciso: non diciamo "impossibile per ogni protocollo", diciamo che nel modello serve un punto di
osservazione fidato prima che i byte siano scelti dall'attaccante. Questo è un risultato, non una resa. -->

---

# Slide 5 — Two origins: software-made evidence can bind physical evidence, but never create it

**On slide**

```
S — software origin                      H — hardware / physical origin
computable by anyone who can run         requires a physical process outside
code on the right inputs                 the adversary's software control

hashes, Merkle, signatures               sensor + trusted capture path
app-declared time, reports               vendor capture credential
thresholds, app-written watermarks       signal emitted into the scene

RULE:   S may bind H (hash it, sign it, anchor it).   S may not create H.
```

Classification of our components:

| Component | Origin |
|---|---|
| Hash of J, P-256 signature, C, Merkle | S |
| Keystore key + attestation | S, hardware-rooted (attests the key, **not** the frame) |
| `localTime`, `sessionChallenge` | S |
| OTS / Bitcoin anchor | S (commitment) |
| Manufacturer credential | H, if the capture path is vendor-attested |
| PRNU | H as a signal, **verdict computed in S** |
| Presence code from an external beacon | H (entered through the optics); its signature is S |

<!-- SAY: La slide più importante. Quasi tutto quello che costruiamo è di origine software: calcolabile da chiunque
controlli il client. Questo non è un difetto, ma deve essere dichiarato. La regola: il software può legare, firmare, ancorare
prove fisiche. Non può inventarle. Per questo la nostra UI non chiama mai "verificato" qualcosa che è solo firme.
Nota sul PRNU: il segnale è fisico, ma il verdetto lo calcola software, e il segnale è copiabile. -->

---

# Slide 6 — The construction: a Merkle commitment over install, photo, and device evidence

**On slide**

```
F_I = D("install", SHA256(pk_I), pkg, versionCode, signerDigest)

E   = [placement, detachedHash, manufacturerResult, prnuResult,
       refHash, policyHash, reportHash]

F_P = D("photo/evidence-v3", F_I, SHA256(J), E)

F_D = Node( Node(Node(L0,L1), Node(L2,L3)), L4 )
      L = ID_I · manufacturer · model · localTime · sessionChallenge

T   = Node( Node(F_I, F_P), F_D )
R   = D("fingerprinto/evidence-v3", T)
σ   = ECDSA-P256( sk_I, R )
C   = D("receipt", R, σ)                 ← 32 bytes, public commitment
```

`D(label, x…) = SHA256(CBOR(label, x…))`, deterministic CBOR (RFC 8949 §4.2.1). `Node(a,b) = D("node", a, b)`.

Diagram: binary tree with `F_I`, `F_P`, `F_D` as three subtrees under `T → R → σ → C`.

<!-- SAY: Una sola slide di costruzione. Tre sotto-alberi: identità dell'installazione, la foto con le evidenze, i dati del dispositivo.
Il root R lega tutto, la firma è sulla root, C è l'impegno pubblico sulla ricevuta firmata.
Tre dettagli che interessano a chi fa crittografia: (1) le etichette danno separazione dei domini fra foglie, nodi e root;
(2) C è calcolato sui byte DER esatti di σ, perché ECDSA non ha firma unica, quindi la firma si salva una volta sola;
(3) C non è un impegno hiding, e le ricevute della stessa chiave sono collegabili per ID_I. Non venderlo come anonimato. -->

---

# Slide 7 — The reducer: evidence levels that never upgrade on missing evidence

**On slide**

| Condition (top to bottom) | Level |
|---|---|
| Definitive origin failure, conflict, or invalid signature / binding / enrollment | **Failed** |
| Manufacturer credential passes trust + hard binding to J | **Manufacturer-verified** |
| Manufacturer absent or inconclusive **and** PRNU passes vs a trusted reference | **PRNU-verified** |
| Neither passes, neither definitively fails | **No verification** |

```
result codes:  0 unavailable · 1 inconclusive · 2 pass · 3 fail
```

Rules: missing evidence is never a pass · both checks always run · a PRNU mismatch blocks even a valid manufacturer credential ·
a user's "trust / don't trust" never changes a level or C.

<!-- SAY: Lo stesso reducer gira sull'app e sul verificatore indipendente. Il livello non è dentro la firma: lo ricalcola
chi verifica, con la propria policy. Un'app che si dichiara "PRNU ok" non basta. Le due verifiche girano sempre entrambe, anche
se la prima passa. Un mismatch PRNU blocca anche con credenziale valida del produttore. -->

---

# Slide 8 — The PRNU layer is statistical evidence, and we say so

**On slide**

```
Reference (per physical lens and mode), frames i with mask m_i:

   W_i = I_i − denoise(I_i)

            Σ_i  m_i[p] · I_i[p] · W_i[p]
   K[p] = ───────────────────────────────
              Σ_i  m_i[p] · I_i[p]²

Query:   compare W with I·K over valid overlap, shift fixed at 0.
         r0 = normalized correlation at that shift

                 sign(r0) · r0²
   SPCE = ──────────────────────────────────────
           mean( r(s)² : s outside the exclusion window )
```

Decision: **Match** (≥ calibrated threshold) · **Mismatch** (validated rejection region) · **Inconclusive** (everything else).

Footer: PRNU is copyable · does not date a capture · thresholds measured on innocent images say nothing about an adaptive adversary.

Status badge: `[NON VERIFICATO]` on real devices, no measured error rates yet. `[TBD]` if data exist at presentation time.

<!-- SAY: Il PRNU è l'impronta del rumore del sensore. Lo usiamo come evidenza di compatibilità, non come prova.
Non spediamo una soglia universale e non trasformiamo un punteggio in una probabilità di verità. Match e mismatch si abilitano
separatamente, solo dove gli errori misurati stanno dentro una policy dichiarata. Oggi non abbiamo dati su un sensore reale:
dillo. Meglio una slide onesta che una soglia di comodo. -->

---

# Slide 9 — A beacon code puts a physical signal into the scene

**On slide**

```
Beacon B (a second device) displays a signed, time-varying optical code:

   slot    = ⌊ t / Δ ⌋
   payload = [ver, beaconId, slot, binding]
   code_t  = payload ‖ Sign(sk_B, payload)

Verifier:  decode code from J  →  verify under registered pk_B
           →  check slot against the receipt's anchor time

Window:    [ t_slot ,  t_anchor ]       (content is not older than the slot;
                                         receipt existed by the anchor)
```

| Gives | Does not give |
|---|---|
| Content not before the slot | Sensor identity |
| Works with any camera | Scene integrity (splicing) |
| Window without a vendor | Resistance to relay |
| Role for the ledger: beacon-key registry + time | Tight upper bound under replay |

Status badge: `[PROPOSTA]`, update to the real state of the repo at presentation time.

<!-- SAY: Se il watermark lo scrive il telefono non vale nulla, quindi il segnale deve arrivare da fuori: un secondo dispositivo
mostra un codice firmato che cambia nel tempo. La foto deve contenerlo. Questo vincola il contenuto a dopo lo slot e, insieme
all'ancoraggio, dà una finestra temporale senza dipendere da un produttore. È l'analogo della finestra [T0,T1] mostrata nelle slide
[VERIFY]. Non prova il sensore e non regge relay o splicing: ripetilo. Se non è ancora implementato, dillo chiaramente. -->

---

# Slide 10 — What breaks the system, what we detect, and what we do not

**On slide**

| Attack | Result |
|---|---|
| Edit J, report, policy, or E after signing | Detected (digest / Merkle / signature) |
| Pre-generated file, no valid code | Detected `[PROPOSTA]` |
| Code from an unregistered beacon / tampered code | Detected `[PROPOSTA]` |
| Phone A captures, phone B registers | Detected only if origin layer applies (credential identity, PRNU vs B's reference) |
| PRNU copied / transferred | **Not reliably detected** |
| Splicing real beacon into synthetic scene | **Not detected** |
| Relay of the beacon feed to a remote attacker | **Not detected** |
| Replay of a recorded beacon screen | Lower bound valid, upper bound loose |
| Compromised authority (enrollment, issuer list) | **Not corrected** by a ledger or a quorum |

<!-- SAY: Una slide che di solito non si vede in un pitch: quello che NON rileviamo. È deliberato. Per ogni attacco indichiamo quale
ipotesi fidata lo ferma, oppure che resta aperto. Una giuria tecnica si fida di più di chi dice dove il suo sistema si rompe. -->

---

# Slide 11 — Decentralization helps with publication evidence, not with who attests the physical fact

**On slide**

```
Decentralized:   existence of C before time t   (Bitcoin / OpenTimestamps)
                 anchor file = commitment.bin = the 32 raw bytes of C
                 OTS start digest = SHA256(C)

Still centralized (configured authorities, outside the bundle):
   · attestation roots          · issuer allow-list and capture policy
   · enrollment decisions       · camera ↔ reference association
   · PRNU thresholds            · beacon key registry   [PROPOSTA]
```

Footer: Missing or unavailable evidence ⇒ **indeterminate**, never **pass**. Current and historical assessments are kept separate.

<!-- SAY: Rispondiamo al punto della sfida sulla fiducia centrale (p. 20 del deck [VERIFY]) in modo onesto. L'ancoraggio su Bitcoin decentralizza
la prova di esistenza della ricevuta. Non decentralizza chi dice che quella camera è quella. Le autorità residue sono elencate qui.
Mapparle e renderle verificabili è lavoro aperto. Un registro pubblico non corregge una registrazione falsa. -->

---

# Slide 12 — Where we are, and what we verified

**On slide**

| | Status |
|---|---|
| Deterministic CBOR codecs + commitments v1 / v3, P-256 signing | `[IMPLEMENTATO]` |
| Reducer, receipt journal, bundle / CLI, OTS adapter | `[IMPLEMENTATO]` |
| PRNU experiment (Python / Java) | `[IMPLEMENTATO]`, no real-sensor results |
| Real manufacturer-credential validator | **Not done** (callback) |
| Android decode / calibration path | **Not done** |
| v3 golden vectors | **Not done** |
| Beacon code | `[PROPOSTA]` |

Independently re-implemented the v1 byte contract and checked one of four published vectors:
`F_I, F_P, F_D, T, R, C` match; σ verifies over R and is rejected over bare T. *Synthetic data, v1 only.*

Next: measure on one device · specify the trust model · golden vectors for v3.

<!-- SAY: Stato reale, senza gonfiarlo. Il lavoro crittografico di formato e verifica è fatto. Mancano il validatore reale del produttore,
il percorso Android e i dati su un sensore vero. Non abbiamo test sul codice dopo l'ultimo commit di hardening. Chi ascolta
deve uscire sapendo cosa è dimostrato, cosa è osservato su dati e cosa è aperto. [Aggiorna questa slide con lo stato vero al momento del pitch.] -->

---

# Slide 13 — AI-use disclosure

**On slide**

```
[TO BE COMPLETED BY THE TEAM]

Tools used:                [TBD]
Parts assisted:            [TBD]
Human review performed:    [TBD]
```

<!-- SAY: Obbligatoria per l'evento (per quanto riportato nei nostri documenti, p. 23 del deck [VERIFY]). Non va improvvisata: va compilata dal team con
ciò che è stato fatto davvero. Ci sono stati interventi di Codex e di Claude su parti diverse; la revisione umana effettiva va dichiarata
solo se c'è stata. -->

---

# Backup B1 — Byte contract and parser limits

**On slide**

```
Encoding:   RFC 8949 §4.2.1 deterministic; definite lengths; shortest ints
Reject:     indefinite lengths, tags, floats, non-minimal encodings,
            trailing bytes, invalid UTF-8, wrong type or arity

pk_I        91 bytes, DER SPKI, id-ecPublicKey, prime256v1, uncompressed point,
            on-curve check
σ           8–72 bytes, strict DER, r,s minimal positive INTEGERs in range
receipt     ≤ 256 KiB      J ≤ 128 MiB     detached A ≤ 16 MiB
JSON files  ≤ 1 MiB each (policy, report)  bundle ≤ 160 MiB
Order:      check lengths and digests BEFORE parsing; reject before allocating
```

<!-- SAY: Solo per le domande tecniche. Un errore di limite di dimensione è un rifiuto di profilo, non un mismatch crittografico:
vanno riportati separatamente. -->

---

# Backup B2 — Hybrid post-quantum experiment (v2)

**On slide**

```
Signature:   ML-DSA-65  AND  Ed25519      (both must verify)
Keys:        software keys wrapped at rest with hardware AES, bound to the
             installation's P-256 identity; in RAM during signing — NOT native-hardware
Excluded:    RSA
Caveat:      a hybrid signature does not make the whole trust chain post-quantum
             (attestation, authorities, enrollment protocols stay classical)
```

<!-- SAY: Se qualcuno chiede del post-quantum. È un esperimento separato, con versione di wire propria. Non lo presentiamo come
migrazione completa. -->

---

# Backup B3 — Test vectors

**On slide**

| Case | Credential placement | Challenge | Independently recomputed |
|---|---|---|---|
| 1 | Embedded | Absent | **Yes** (all intermediates, σ verified) |
| 2 | Embedded | Present | No |
| 3 | Detached | Absent | No |
| 4 | Detached | Present | No |

Check run: v1 profile · synthetic data · no real credential · no ledger proof. **v3 vectors: not yet.**

<!-- SAY: Per mostrare che il controllo fatto è parziale e dichiararlo. Se nel frattempo si è coperto altro, aggiorna la tabella. -->

---

<!--
DOMANDE PROBABILI E RISPOSTE ONESTE (per chi presenta, non per le slide)

D: Quindi non avete risolto il problema?
R: Abbiamo dimostrato dove sta il limite (slide 4), costruito un formato che non confonde le proprietà (slide 3, 5, 7) e
   una proposta per aggiungere origine fisica senza vendor (slide 9), con i suoi limiti (slide 10). Non diciamo di aver risolto l'origine fisica.

D: Perché non usare solo C2PA?
R: Dove il produttore attesta la cattura è il percorso più forte, e lo usiamo come evidenza di tipo H (slide 5, 7). Dipende dal produttore e
   dal suo percorso di cattura, che non abbiamo verificato sul nostro dispositivo. Il nostro formato lo accetta e lo tiene separato dal resto.

D: Il PRNU non si copia?
R: Sì, e lo diciamo. È compatibilità statistica, mai prova. Non ci sono ancora tassi d'errore misurati su dati reali.

D: La blockchain cosa aggiunge?
R: Esistenza della ricevuta prima di un tempo, in modo verificabile e senza un server nostro. Non osserva il mondo fisico e non corregge una
   registrazione falsa (slide 11).

D: Funziona con qualsiasi telefono?
R: Il formato sì, il livello di garanzia dipende dal dispositivo. Chi ha un percorso di cattura attestato arriva più in alto, gli altri
   restano a evidenza statistica o nessuna origine.

D: Cosa succede se il beacon è compromesso o c'è un relay?
R: Resta non rilevato (slide 10). Alza il costo di falsificazione, non lo rende impossibile.
-->
