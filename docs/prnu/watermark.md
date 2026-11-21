<!-- AGENT HANDOFF PER CODEX: Proposta di "watermark/codice ottico di presenza" per FingerprinTO. Spiega perché un watermark inserito dal telefono non vale nulla, perché serve un'origine esterna (hardware/fisica), dove puoi muoverti e dove no. Leggi prima la sezione 1: la distinzione fra componenti di origine software e di origine hardware è il punto centrale di tutto il lavoro. Questa è una PROPOSTA da validare: non modificare il wire format v3 senza una proposta motivata (vedi sezione 6). -->

# watermark.MD — codice ottico di presenza e distinzione delle origini

Data: 21 novembre 2026. Complementare a [task.md](../challenge/task.md) e [review notes](../.notes/review-notes.md).
Questo file è stato scritto con assistenza di Claude (Anthropic) a partire da una discussione con l'autore;
non è un risultato sperimentale né una revisione indipendente. Il lavoro che farai tu va registrato
separatamente, senza attribuirlo retroattivamente a questo testo.

**Aggiornamento implementativo (Codex):** leggere [BEACON-EXPERIMENT.md](BEACON-EXPERIMENT.md).
Il token firmato è implementato come esperimento separato, senza modifiche al wire v3.
Correzione alla proposta sotto: un codice leggibile nei pixel **non prova vicinanza
al beacon né passaggio attraverso l'ottica**. Splicing e relay sono indistinguibili;
la finestra temporale è condizionale e riguarda il contenuto che include il token,
non l'età della scena o l'istante della cattura. Il report verifica evidenza S e
mantiene `physicalOriginVerified: false`. La classificazione H resta un'ipotesi
sul percorso fisico, non un risultato del decoder. La proposta originale rimane
qui sotto per tracciabilità.

## 0. Da dove nasce

L'idea iniziale dell'autore era: un watermark "legato il più possibile all'apertura dell'app fotocamera",
costruito con timestamp e chiavi pubbliche, tenendo conto che l'app fotocamera nativa **non espone API**
(se le esponesse sarebbe facile far entrare un nonce nel percorso di cattura).

L'analisi ha portato a due correzioni:

1. L'apertura dell'app fotocamera non è osservabile in modo affidabile senza API, e comunque non è
   ciò che serve provare. Qualsiasi segnale che la nostra app ricavi dal sistema (eventi d'uso,
   accessibilità, ecc.) è prodotto da software che l'avversario controlla.
2. Un watermark inserito **dal telefono o dall'app** non prova nulla (sezione 1). Un watermark ha valore
   solo se la sua origine è fuori dal controllo software dell'avversario.

Da qui la proposta: un **beacon** esterno che mostra un codice ottico firmato e variabile nel tempo, che la foto
deve contenere. Non è la richiesta iniziale alla lettera: è ciò che ne rimane di sostenibile. Se vedi
una strada migliore che rispetti i vincoli, puoi proporla (sezione 5).

## 1. La distinzione centrale: due origini delle componenti

Ogni componente di evidenza del progetto ha un'**origine**, e le due origini non sono intercambiabili.

- **Origine software (S):** il valore è calcolabile da chiunque possa eseguire codice con gli input giusti.
  Hash, firme, Merkle, report, soglie, timestamp dichiarati dal client, watermark scritto da un'app.
  Un avversario che controlla il client le produce per qualsiasi file.
- **Origine hardware/fisica (H):** il valore richiede un processo fisico fuori dal controllo software
  dell'avversario: il sensore e il suo percorso di cattura fidato, un secondo dispositivo che emette
  un segnale nella scena, un enclave/vendor che osserva la cattura.

**Regola di progetto, da non violare:** una componente S può *vincolare* evidenza H (legarla ai byte,
firmarla, ancorarla) ma non può *crearla*. Una firma sopra un'affermazione falsa resta falsa. Un
registro pubblico ancora ciò che gli viene sottoposto.

Classificazione di ciò che esiste oggi, e della proposta:

| Componente | Origine del valore | Nota da tenere presente |
|---|---|---|
| Hash di `J`, firma P-256, `C`, Merkle | S | Integrità e possesso della chiave; non provano la cattura. |
| Chiave Keystore + attestation | S, con radice di fiducia hardware | Attesta chiave e app al momento della generazione, **non** il fotogramma. Non è una firma del sensore. |
| `localTime`, `sessionChallenge` nel receipt | S | Dichiarati dal client. Una challenge inserita dopo la cattura non prova la cattura. |
| Ancoraggio OTS/Bitcoin | S (commit) | Dà un limite superiore: la ricevuta esisteva prima. Non osserva il mondo fisico. |
| Credenziale del produttore (C2PA, ecc.) | H, **se** il percorso di cattura è attestato dal vendor | Fiducia nel vendor e nel suo percorso; sul nostro percorso non è ancora verificata. |
| PRNU (segnale nel file) | H come segnale, ma **verdetto calcolato in S** | Compatibilità statistica; copiabile. Il report dell'app non è evidenza da solo. |
| Watermark/codice scritto dall'app | S | Non vale nulla come origine. Non implementarlo come se lo fosse. |
| **Codice ottico di un beacon esterno (proposta)** | H come presenza nella scena; **la firma del codice è S** | Il valore sta nel fatto che il codice è entrato attraverso l'ottica, non nella firma. |

Conseguenze operative per te:

- Ogni check, nel report e nella documentazione, deve dichiarare la propria classe (S o H) e, per H,
  da quale radice di fiducia dipende (vendor, sensore, beacon).
- Etichette e UI non devono lasciar leggere una componente S come origine fisica. In particolare non
  introdurre un livello chiamato "verificato" per via di sole firme e ancoraggi.
- Evidenza H mancante non diventa pass. Se l'unica evidenza è S, il livello massimo è "nessuna verifica
  d'origine", con integrità e identità dell'installazione riportate a parte.

## 2. Perché non basta un watermark digitale

Se il client è ostile e ha una chiave valida, può produrre un file qualsiasi e applicargli qualunque
watermark calcolabile da timestamp, chiavi pubbliche o segreti presenti sul dispositivo. Un watermark
robusto a JPEG, scritto in pixel da software, è solo metadato più difficile da cancellare: la firma che già
abbiamo fa meglio lo stesso lavoro. Un watermark simmetrico con chiave segreta custodita sul telefono
non cambia il quadro: l'avversario controlla il telefono. Se pensi di aver trovato un caso in cui questo
non vale, scrivilo con l'ipotesi fidata esplicita prima di implementarlo.

## 3. La proposta: codice ottico di un beacon

Un secondo dispositivo (un telefono o tablet di cui si ha la chiave), il **beacon**, mostra a schermo un codice
che cambia a intervalli (slot). Chi scatta inquadra il codice nella scena. Il verificatore lo estrae da `J`.

Schema di riferimento (puoi modificarlo entro i vincoli della sezione 5):

```
slot      = floor(t_beacon / Δ)
payload   = [versione, beaconId, slot, binding]
code_t    = payload ‖ Sign(sk_B, payload)        # mostrato come codice ottico robusto
binding   = H(sessione o riferimento alla ricevuta attesa)   # da decidere, vedi 5
```

Verifica indipendente:

1. Decodifica il codice da `J` con un decoder fissato e versionato.
2. Verifica la firma con `pk_B`, registrata fuori dal bundle (stesso trattamento di un'autorità: non è un
   trust anchor fornito dal firmatario).
3. Controlla coerenza dello slot con il tempo dell'ancoraggio della ricevuta.

**Cosa ottiene.** Un limite inferiore sul contenuto: la foto non è precedente allo slot (assumendo che
`sk_B` non sia compromessa e che il beacon non pubblichi codici futuri). Insieme all'ancoraggio della
ricevuta si ottiene una finestra `[t_slot, t_ancoraggio]`. È l'analogo, senza vendor, della finestra
descritta nelle slide IXH26, pp. 16–17 (non ho verificato direttamente le slide: leggile se le hai).
Funziona con qualsiasi fotocamera, perché il codice sta nella scena: risponde a p. 21 (generalizzabilità).
Il ruolo della blockchain diventa concreto: registro delle chiavi dei beacon e ancoraggio temporale.

**Cosa NON ottiene, e va scritto ovunque venga presentata la funzione:**

- Non prova il sensore: prova presenza vicino al beacon in una finestra.
- Non prova l'integrità della scena: lo splicing (foto reale del beacon incollata in una scena sintetica) non
  è rilevato.
- Non resiste al relay: un complice vicino al beacon che trasmette il video permette a un attaccante remoto
  di comporre in tempo reale. Con slot brevi è fattibile.
- Il replay di una registrazione dello schermo del beacon dà un limite inferiore valido ma non un limite
  superiore stretto: la finestra è ampia quanto lo è il ritardo fino all'ancoraggio, e va riportata così,
  non come "scattata al tempo t".
- Una foto di uno schermo che mostra il codice è comunque una cattura autentica di contenuto qualsiasi.

Vincoli fisici già emersi: la torcia/flash del telefono **non** è utilizzabile con l'app nativa aperta
(la fotocamera la possiede), quindi serve un secondo dispositivo. Il watermark invisibile e robusto a
JPEG/ISP è un problema di ricerca aperto: per il prototipo usa un codice visibile e dichiara l'invisibile
come lavoro futuro.

## 4. Dove hai spazio di manovra

Libertà reali, entro i vincoli:

- **Formato del codice:** QR con correzione d'errore, pattern a colori, codice temporale (sequenza di frame
  letta con rolling shutter), o altro. Scegli e motiva in base a robustezza a compressione, sfocatura,
  prospettiva, moiré e riflessi.
- **Primitive e codifica:** firma del beacon (P-256 per coerenza col profilo, oppure altro, **non RSA**), lunghezza
  di slot, struttura del payload, protezione da code-reuse tra beacon.
- **Binding:** se e come il codice si lega a una sessione/ricevuta. Un binding forte aiuta a ridurre il replay ma
  richiede un canale verso il beacon prima dello scatto; valuta il costo in usabilità e dichiaralo.
- **Decoder e preprocessing** del verificatore: fissali, versionali, e metti la versione nel report.
- **Dove vive nel codice:** un modulo di verifica separato nel verificatore Python, con report JSON proprio,
  è una soluzione accettabile come primo passo.
- **Harness e simulazioni:** generazione di immagini sintetiche con degradazioni (JPEG a più qualità, blur,
  prospettiva, moiré simulato, rumore) per misurare quando il decoder fallisce. Sono **simulazioni** e vanno
  etichettate come tali: non sono prove sul dispositivo.
- **Classe delle evidenze nel report:** aggiungere un campo che dichiari origine S/H e radice di fiducia per
  ogni check è incoraggiato.
- **Proposte alternative:** se ritieni che una struttura diversa rispetti meglio la regola della sezione 1, proponila
  con le ipotesi, senza implementarla in silenzio.

## 5. Vincoli (non negoziabili senza decisione dell'autore)

- **Non modificare il wire format v3** né riciclare versioni/domini esistenti. L'array `E` ha sette elementi e i
  codici di risultato sono 0–3 per due check. Un terzo check cambia garanzie e formato: prepara prima una
  **proposta motivata** (cosa cambia, compatibilità con i receipt esistenti, vettori nuovi) e fermati lì. Nel
  frattempo il risultato del check beacon può vivere nel report JSON, che è già hashato come `reportHash`.
- **Non indebolire il reducer** per far passare la demo; non far salire un livello per via di evidenza S.
- **Non usare RSA**; mantieni P-256 v3 e l'esperimento ibrido separato.
- **Non compilare l'app né installare Android SDK**: l'autore ha rifiutato. Il lavoro è su modulo
  crittografico e verificatore. Indica i call site Android da collegare in futuro, senza implementarli.
- **Niente XRPL, backend social, UI completa.**
- **Non fare merge della PR #9**; lavora su un ramo/worktree di validazione isolato, commit piccoli
  con prefissi `ADD`, `FIX`, `DEL`.
- **Non inventare dati**: non creare foto di test come se fossero prove su dispositivo; non dichiarare soglie
  senza misure; non dichiarare risolto il problema dell'origine fisica.
- **Chiavi e fixture:** non pubblicare chiavi reali del beacon né immagini personali come fixture.

## 6. Cosa produrre

1. **Specifica breve** del codice e della verifica (payload, firma, slot, registrazione di `pk_B`, ipotesi
   fidate) con la tabella di classificazione S/H aggiornata, nello stile del resto di `docs/`.
2. **Modulo di verifica** nel verificatore Python con decoder fissato e report JSON proprio (classe H,
   radice di fiducia = beacon, esito: pass / fail / inconclusive / non disponibile).
3. **Vettori di test sintetici**, riproducibili, con tamper separati su: codice, firma, slot, `pk_B` non
   registrata, immagine senza codice. Esiti attesi dichiarati prima di eseguirli.
4. **Matrice degli attacchi** con esito dichiarato per ciascuno:
   file pre-generato (deve fallire); codice alterato (deve fallire); beacon non registrato (deve fallire);
   slot incoerente con l'ancoraggio (deve fallire o essere inconclusive, da motivare);
   **splicing, relay, replay di registrazione: non rilevati**: scrivili come limiti, non come "da migliorare".
5. **Proposta di wire** per un terzo check, se la ritieni necessaria (sezione 5).
6. **Registro di lavoro** con: strumento (tu), parti assistite, SHA testato, comandi, versioni, test
   eseguiti e saltati. Per la disclosure AI richiesta dall'evento (slide IXH26, p. 23, da verificare nel PDF)
   registra solo ciò che hai fatto tu.

## 7. Criteri di accettazione

- Un revisore può leggere, per ogni check, se l'origine è S o H e da quale radice di fiducia dipende.
- Nessun testo (README, report, UI proposta) presenta evidenza S come origine fisica.
- Gli attacchi non rilevati sono elencati come limiti nello stesso punto in cui la funzione è descritta.
- Gli esiti sui dati sintetici non sono presentati come risultati su dispositivo.
- Nessuna modifica silenziosa al formato, al reducer o alle versioni esistenti.

## 8. Domande aperte (non bloccanti: scegli, motiva, segnala)

- Il codice è visibile (QR/pattern) o hai un'idea difendibile per uno meno invadente? Non assumere che
  l'invisibile funzioni.
- Quanto deve essere lungo uno slot, e quale ritardo massimo ricevuta→ancoraggio si accetta?
- Chi registra `pk_B` e come si revoca? Rientra in OP-03: non presumere che il registro corregga una
  registrazione falsa.
- Come si evita che il beacon pubblichi codici futuri o riutilizzabili? Il valore del limite inferiore
  dipende da questo.
- Il binding alla sessione vale il costo di un canale verso il beacon prima dello scatto?
