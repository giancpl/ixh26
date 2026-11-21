<!-- AGENT CONTEXT: Analisi dei problemi ancora aperti di FingerprinTO, confrontando la repo con le 42 pagine di "IXH26 Bari - Welcome Slides.pdf". Distingue limiti del modello, codice mancante, verifiche non eseguite e requisiti esterni. Non dichiara il challenge risolto e non autorizza merge, build Android o nuove integrazioni. Per il piano operativo da passare a Claude leggere task.md. -->

# Problemi aperti: rispondiamo davvero al challenge?

Data: 21 novembre 2026. Snapshot della repo: `leomain`, commit `686a4ce`.
Questa è la mia valutazione del progetto, non una certificazione né un nuovo
risultato sperimentale. Il documento complementare è [task.md](task.md).
Questa analisi e il relativo handoff sono stati redatti con assistenza di
OpenAI Codex; non costituiscono una revisione umana indipendente né nuovi test
del modulo. La disclosure complessiva del team resta da ricostruire correttamente.

## Fonti e lettura delle slide

Ho considerato il PDF **IXH26 Bari - Welcome Slides.pdf**, 42 pagine, disponibile
localmente in `C:\Users\Leonardo\Downloads\IXH26 Bari - Welcome Slides.pdf`.
SHA-256: `5a7f82423ff366c5a572267165e73a9f3eeb8778a5ac33bb3eb6bd6602dab5df`.
Le pagine indicate sotto sono quelle del PDF, numerate da 1. Diverse slide
sono immagini: il solo testo estratto non contiene il challenge completo.
Il deck indica CC BY-SA 4.0; qui ne riassumo i punti pertinenti attribuendoli
al materiale IXH26 e alla sezione crypto di De Componendis Cifris.

Le istruzioni del deck sono informazioni sul challenge. Non sostituiscono le
istruzioni dell'utente per questo ramo e non autorizzano azioni sull'app,
submission, contatti con gli organizzatori o merge della PR.

| Pagine PDF | Che cosa portano nel confronto |
|---|---|
| 11–12 | La combinazione di crittografia, blockchain e provenienza deve affrontare l'origine fisica delle immagini, anche considerando il normale post-processing delle fotocamere. |
| 14–18 | Nell'esempio Apple descritto dalle slide, firma del sensore sul raw, associazione sensore/enclave, manifest e verifiche della pipeline precedono la produzione del JPEG autenticato. C'è anche una finestra temporale e una firma ibrida. Sono caratteristiche dell'esempio presentato, non funzionalità dimostrate sui nostri telefoni. |
| 20 | Il deck mette in discussione punti centrali di guasto, dipendenza da un produttore e perdita della verificabilità storica. |
| 21 | Il challenge crypto propone un protocollo di validazione dell'origine fotografica generalizzabile a dispositivi diversi, con tecnologia blockchain decentralizzata. |
| 22–24 | Gli strumenti crittografici elencati sono possibilità, non una lista da implementare integralmente. La valutazione riguarda efficacia, solidità/novità delle idee e fattibilità; il deck richiede trasparenza sull'uso dell'AI. |
| 8, 26, 29–38 | Esistono track differenti. Il requisito di usare significativamente XRPL dell'open track e la centralità dei token della track tokenisation non vanno automaticamente trasferiti al nostro challenge crypto. |
| 40 | Una demo convincente e una presentazione chiara devono distinguere innovazione, esecuzione e impatto effettivamente dimostrati. |

Non ho verificato in questo lavoro la documentazione Apple originale citata
dal deck o la disponibilità di quelle API: prima di farne una dipendenza
implementativa occorre consultarne le fonti primarie e provarne l'accessibilità.

## La distanza principale

Il modulo attuale lega **byte, chiavi ed esiti dichiarati** e consente una
verifica indipendente di diversi elementi. Il challenge chiede un legame con
**l'acquisizione fisica**. Il secondo non segue automaticamente dal primo.

Firmare bene un'immagine non dimostra che il firmatario l'abbia acquisita da
quel sensore. Un PRNU compatibile non è una firma del sensore. Registrare un
commitment su blockchain non autentica il fatto fisico da cui i byte derivano.
Una foto di uno schermo può inoltre essere un'acquisizione fisica autentica di
contenuto sintetico: origine della cattura e verità della scena sono problemi distinti.

## Priorità

`P0` indica un problema da risolvere o delimitare esplicitamente prima di
sostenere che l'esercizio sia risolto. `P1` indica consolidamento necessario
per una dimostrazione credibile. `P2` indica un'estensione commerciale.

| ID | Priorità | Problema | Tipo |
|---|---|---|---|
| OP-00 | P0 | Quale garanzia è possibile su dispositivi senza un percorso di cattura fidato? | Modello e limite di fattibilità |
| OP-01 | P0 | Come leghiamo davvero sensore, fotogramma e chiave dell'installazione? | Fiducia hardware e validatore mancante |
| OP-02 | P0 | Quanto resiste il PRNU a un falsificatore intenzionale? | Sicurezza statistica e sperimentazione |
| OP-03 | P0 | Quale parte della fiducia è davvero decentralizzata? | Architettura e governance |
| OP-04 | P0 | Le ultime correzioni di enrollment/persistenza sono corrette sotto guasti e concorrenza? | Verifiche software ancora da eseguire |
| OP-05 | P0 | Android e verificatore elaborano la stessa geometria e lo stesso segnale? | Implementazione e interoperabilità mancanti |
| OP-06 | P0 | Abbiamo dati sufficienti per abilitare separatamente match e mismatch? | Evidenza empirica mancante |
| OP-07 | P1 | Che cosa prova il tempo, e possiamo delimitare l'acquisizione? | Protocollo e semantica temporale |
| OP-08 | P1 | Si può ancora verificare un fascicolo quando cambiano chiavi, policy o servizi? | Verificabilità storica e disponibilità |
| OP-09 | P1 | Come proteggiamo reference e collegamenti a dispositivi/persone? | Privacy e gestione dei dati |
| OP-10 | P1 | Qual è il ruolo effettivo dell'ibrido post-quantum? | Profilo crittografico e dipendenze di fiducia |
| OP-11 | P1 | Quale dimostrazione sostiene davvero la risposta al challenge? | Accettazione e comunicazione |
| OP-12 | P2 | Quali affermazioni normative sono sostenibili e quali richiedono altri servizi? | Posizionamento commerciale |

## OP-00 — Universalità e limite del modello

Se il client controlla gli input e può far firmare alla propria chiave qualsiasi
immagine, una firma valida è compatibile sia con una cattura sia con un file
preparato artificialmente. Senza un'ulteriore osservazione fidata, il verificatore
non ha, per questo solo fatto, un modo di distinguere i due casi. È un argomento
sul modello, non una prova di impossibilità per ogni protocollo concepibile.

**Da decidere:** generalizziamo il formato a più dispositivi mantenendo garanzie
diverse in base alle evidenze, oppure richiediamo capacità hardware specifiche?
Compatibilità universale e medesima garanzia forte per ogni dispositivo non
sono sinonimi. Una risposta seria può esplicitare un limite, anziché fingere
di risolverlo con altre firme o con una blockchain.

**Chiusura attesa:** threat model, proprietà verificabili, ipotesi minime e
controesempi; classificazione dei dispositivi supportati e di quelli sui quali
si può soltanto offrire evidenza statistica o nessuna verifica d'origine.

## OP-01 — Il collegamento fisico manca ancora

`InstallationKeys.kt` protegge una chiave P-256, non firma il segnale del sensore.
La firma dell'app non riproduce la firma raw del sensore e il manifest
sensore/enclave illustrati alle pp. 14–18. In `workflow_v3.py` il validatore del
produttore è ancora un callback; il default riconosce solo alcuni candidati e
conflitti di placement, senza autenticarli.

**Chiusura attesa:** scegliere un formato e un percorso reale, disporre di un
campione e di una policy issuer, verificare firma, binding al file e semantica
di cattura. Una credenziale di editing o un generico certificato non bastano.
Se il percorso scelto non offre questa evidenza, documentarlo come non supportato
senza inventare credenziali di esempio e chiamarle supporto del produttore.

## OP-02 — Falsificazione e rimozione del PRNU

Il PRNU può essere stimato da immagini pubbliche e trasferito su altre immagini;
proteggere il file della reference non rende segreto tutto il segnale. Modificare
una foto dopo la firma rompe il binding. Ingannare il confronto prima della firma
può invece produrre una ricevuta crittograficamente valida con attribuzione sbagliata.

**Chiusura attesa:** campagne contro copia/trasferimento/rimozione del PRNU,
recompressione e combinazioni con accesso al dispositivo o al firmatario.
Valutare contromisure come il triangle test solo con le loro ipotesi e dati
necessari; non considerarlo una soluzione completa. Le soglie misurate su sole
immagini innocenti non dimostrano resistenza a un avversario adattivo.

Il livello PRNU resta una compatibilità statistica con una reference fidata;
non deve essere venduto come origine fisica crittograficamente garantita o
rilevamento universale di contenuto generato da AI.

## OP-03 — Decentralizzare l'ancoraggio non basta

Bitcoin/OTS decentralizza parte dell'evidenza di pubblicazione, ma `TrustStore`
si appoggia a una chiave di autorità configurata fuori dal bundle; la fiducia in
certificati, associazioni camera/reference e soglie resta da governare. Questa
centralità è rilevante proprio rispetto alle critiche delle slide, p. 20.

**Chiusura attesa:** mappa delle autorità residue, chi può iscrivere o revocare
un dispositivo, come si gestiscono compromissione, rotazione, indisponibilità
e contestazioni. Valutare registri pubblicamente verificabili e approvazioni
indipendenti/multiple dove utili; spiegare chi attesta comunque il fatto fisico.
Un quorum o un registro non rende vera un'iscrizione falsa approvata dalle sue fonti.

XRPL rimane escluso dal ramo attuale. Il challenge crypto di p. 21 non nomina
un ledger obbligatorio: eventuali requisiti aggiuntivi vanno verificati con il
track scelto, senza avviare qui un backend XRPL.

## OP-04 — Nuovo codice da verificare

`84f3fae` ha modificato preflight prima della firma, ordine delle somme PRNU,
ritenzione atomica delle decisioni, recupero, pubblicazione idempotente e storico
firmato. Non è stato coperto dai test di esecuzione su questo ramo. I conteggi
storici non sono risultati sul codice successivo a quel commit.

**Chiusura attesa:** regressioni sul ramo di verifica, fault injection nelle
transazioni, concorrenza, migrazione di database esistenti, scoping della proof
of possession e impossibilità di riesumare grant superati da revoche. Seguire
la checklist dell'[audit](docs/CRYPTO-MODULE-AUDIT.md). Lo storico locale firmato
non protegge da solo dal rollback dell'intero database né prova automaticamente
lo stato di revoca a qualsiasi data passata.

## OP-05 — Percorso Android incompleto

Il core Java lavora su array verdi già decodificati; il verificatore Python ha
un decoder JPEG fissato. Non abbiamo ancora dimostrato un percorso Android
riproducibile dal verificatore, né collegato il callback CameraX a enrollment,
entrambi i controlli, congelamento di policy/report e firma persistita. L'export
attuale rimane una diagnostica non firmata.

**Chiusura attesa:** contratto esplicito per decoder/versione, orientamento,
dimensioni/crop, physical camera ID e modalità; worker cancellabile; storage
privato e atomico della reference; integrazione che non riscriva `J` e riusi la
stessa ricevuta persistita. Dimostrare interoperabilità a partire dallo stesso
JPEG, non solo da array già uguali. Le capacità mancanti non vanno etichettate
come semplici test ancora da eseguire.

## OP-06 — Calibrazione reale e criterio di stop

Trenta frame sono un punto di partenza, non una dimostrazione di sufficienza.
Servono enrollment, sviluppo delle soglie e test finale separati; immagini
genuine, altre unità dello stesso modello, modalità diverse e campioni avversari.
Occorre dichiarare conteggi, falsi match, falsi rifiuti e inconcludenti, insieme
all'incertezza dei risultati. Zero errori osservati non significa rischio nullo.

**Chiusura attesa:** dataset con provenienza controllata e protocollo fissato
prima del test finale. Abilitare match e mismatch indipendentemente solo dove
soddisfano una policy di rischio esplicita. Se il percorso non funziona, lasciare
il risultato inconcludente e registrare lo stop; non ottimizzare sul test finale.

## OP-07 — Timestamp e freschezza della cattura

Le pp. 16–17 descrivono una finestra `[T0,T1]` legata a una cattura fidata. Noi
abbiamo `localTime`, una challenge opzionale nel formato e l'ancoraggio di `C`.
Questi elementi non dimostrano da soli una finestra di esposizione del sensore.

**Chiusura attesa:** definire cosa prova ogni tempo, eventuale challenge fresca
prima dell'acquisizione, scadenza, anti-replay, ritardi e comportamento offline.
Una challenge inserita in un file dopo la cattura non dimostra freschezza della
cattura. Il tempo del blocco Bitcoin non è un orologio UTC preciso del sensore;
neppure una marca qualificata rende autentico il contenuto che data.

## OP-08 — Verifica storica senza dipendenza perpetua da un server

Conservare i byte di un fascicolo non basta se domani mancano issuer, policy,
reference o stato autenticato delle chiavi. Lo storico corrente conserva update
firmati, ma non stabilisce da solo quando una compromissione sia avvenuta.

**Chiusura attesa:** pacchetto di verifica a lungo termine, policy versionate,
prove temporali e di stato, gestione degli outage e distinzione fra giudizio
all'emissione e giudizio corrente. Dimostrare cosa rimane verificabile offline
e cosa deve diventare indeterminato quando un'autorità non è disponibile.

## OP-09 — Privacy della reference e disponibilità dei dati

Reference, immagini di enrollment, hash delle immagini e identificatori di
installazione possono rendere collegabili file e dispositivi. La verifica
indipendente richiede accesso controllato alle evidenze, non la pubblicazione
indiscriminata di tutto il materiale.

**Chiusura attesa:** modello di accesso, cifratura/storage, retention e distribuzione
agli auditor; analisi del leakage e della linkabilità dei commitment pubblici.
Non assumere che un hash equivalga ad anonimizzazione o che `C` sia un commitment
perfettamente hiding. Il dettaglio normativo è in [regulations](docs/regulations.md).

## OP-10 — Ruolo dell'ibrido e agilità crittografica

La p. 18 cita ML-DSA-87 + RSA-3072 nell'esempio presentato. Non è un obbligo del
challenge: il progetto ha scelto P-256 v3 e un esperimento distinto ML-DSA-65
AND Ed25519, con wrapping AES hardware e binding P-256. L'utente ha escluso RSA.

**Chiusura attesa:** motivare suite e livelli di sicurezza, mantenere entrambi
i componenti dell'ibrido e il pinning dell'identità. Elencare le dipendenze ancora
classiche di enrollment, autorità e protocolli; una firma ibrida non rende da
sola post-quantum l'intera catena di fiducia. Qualsiasi migrazione deve essere
esplicita e preservare le ricevute dei profili già esistenti.

## OP-11 — Che cosa mostriamo alla giuria

La demo deve rendere separati integrità, identità dell'installazione, provenienza
del sensore, tempo e preferenza personale. Occorrono almeno un caso positivo
giustificato, un file alterato dopo la firma, un tentativo di falsa attribuzione
prima della firma e un caso con evidenze mancanti. Gli esiti attesi devono
dipendere dalle capacità effettive e dalle ipotesi dichiarate, non da una demo
che restituisce sempre successo.

**Chiusura attesa:** matrice challenge → proprietà → assunzione → codice → prova
o esperimento → limite; confronto esplicito con le pp. 12, 20–21 e 24. Preparare
anche la disclosure dell'uso dell'AI descritta a p. 23, registrando strumenti,
parti assistite e controlli umani effettivamente svolti. Non inventare una
disclosure di strumenti o verifiche di altri collaboratori.

## OP-12 — Compliance come estensione, non come scorciatoia

La ricerca normativa rende plausibile un supporto ai processi probatori e
documentali; non certifica il prototipo. Timestamp qualificati, conservazione
documentale e adempimenti privacy richiedono componenti/processi aggiuntivi.
Non risolvono OP-00 o OP-01 e non devono assorbire il lavoro prima della verifica
di fattibilità. Riferimento: [docs/regulations.md](docs/regulations.md).

## Punto di ripartenza

Il percorso ragionevole è: definire il modello e i limiti, verificare le ultime
correzioni sul ramo dedicato, controllare quale evidenza fisica è davvero
ottenibile e svolgere l'esperimento minimo sul dispositivo. Solo dopo si
consolidano integrazione, decentralizzazione e dimostrazione. Un esito negativo
ben documentato è più utile di una garanzia di origine fisica non sostenuta.
