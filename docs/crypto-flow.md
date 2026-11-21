# PRNU, hash e firma: schema del profilo v3

Questo schema descrive il profilo **P-256 v3**, separato dall'esperimento ibrido
v2. Il PRNU produce un risultato statistico; hash e firma legano ai byte esatti
quel risultato e le evidenze usate. Non trasformano il risultato in una prova
della verità della scena. Le parti ancora mancanti sono nell'[audit](CRYPTO-MODULE-AUDIT.md).

## 1. Dal sensore al confronto PRNU

![PRNU: reference e confronto](diagrams/prnu-flow.png)

<details>
<summary>Sorgente Mermaid del diagramma PRNU</summary>

```mermaid
flowchart TD
    EN["Immagini di enrollment indipendenti J_i"] --> PRE["Decoder, verde, crop e geometria fissati"]
    PRE --> IW["Intensità I_i e residuo W_i = I_i - denoise(I_i)"]
    IW --> EST["Stima K: somma(m_i I_i W_i) / somma(m_i I_i²)"]
    EST --> REC["Record reference: K, mask, geometria, metodo, hash dei J_i"]
    REC --> RH["refHash = SHA256(byte esatti del record)"]

    J["Foto da verificare: byte originali J"] --> Q["Stesso preprocessing: intensità I, residuo W e mask"]
    Q --> PRED["Componente attesa: I × K"]
    REC --> PRED
    Q --> CORR["Correlazioni normalizzate fra W e I × K"]
    PRED --> CORR
    CORR --> SCORE["PCE con segno: confronto allo spostamento zero"]

    SCORE --> GUARD["Qualità, geometria, reference fidata e calibrazione"]
    RH --> TRUST["Associazione approvata: refHash, installazione, camera e policy"]
    TRUST --> GUARD
    POLICY["Policy approvata: metodo e soglie distinte"] --> GUARD
    GUARD --> RESULT["PRNU: pass / fail / inconclusive / unavailable"]
    RESULT --> REPORT["Report JSON: esito, motivazione, score, qualità e identificatori"]

    classDef statistical fill:#e8f2ff,stroke:#3367a1,color:#142c45;
    classDef trusted fill:#fff4da,stroke:#a37313,color:#4c3509;
    class IW,EST,PRED,CORR,SCORE,RESULT statistical;
    class TRUST,POLICY,GUARD trusted;
```

</details>

`K` è stimato da immagini diverse da `J`: la query non entra nella propria
reference. Dopo residui e stima vengono applicate le soppressioni di artefatti
di riga/colonna previste dal metodo.

Nel metodo sperimentale corrente la griglia è `[-3,3] × [-3,3]`, con allineamento
atteso a zero. Non si sceglie il massimo della superficie:

```text
r0  = correlazione allo spostamento (0,0)
PCE = sign(r0) × r0² / media(r_shift² fuori dalla finestra centrale 3×3)
```

Energia nulla o area valida insufficiente danno un risultato inconcludente.
Le soglie di accettazione e rifiuto si abilitano separatamente: senza relativa
calibrazione approvata non si emette il corrispondente pass/fail. Vedi il
[metodo PRNU](PRNU-EXPERIMENT.md) per maschere, denoiser e parametri esatti.

## 2. Dagli esiti al commitment firmato

![Profilo v3: hash, firma e timestamp](diagrams/crypto-hash-flow.png)

<details>
<summary>Sorgente Mermaid del diagramma hash e firma</summary>

```mermaid
flowchart TD
    PK["Chiave pubblica pk_I"] --> ID["ID_I = SHA256(pk_I)"]
    ID --> FI["F_I: installazione, package, versione e signerDigest"]
    META["ID_I, manufacturer, model, localTime, sessionChallenge"] --> FD["F_D: albero dei metadati dichiarati"]
    ID --> FD

    J["Byte originali immutabili J"] --> HJ["SHA256(J)"]
    J --> CHECKS["Entrambi i controlli: produttore e PRNU"]
    CHECKS --> REPORT["Report JSON esatto: esiti e diagnostica"]
    CHECKS --> CODES["Codici degli esiti dichiarati"]

    POLICY["Policy JSON esatta"] --> HP["policyHash = SHA256(policy)"]
    REF["Record reference, se usato"] --> HR["refHash = SHA256(record)"]
    HP --> REPORT
    HR --> REPORT
    REPORT --> HD["reportHash = SHA256(report)"]
    A["Credenziale separata A, solo se detached"] --> HA["detachedHash = SHA256(A)"]

    CODES --> E["E: placement, detachedHash, esiti,<br/>refHash, policyHash, reportHash"]
    HP --> E
    HR --> E
    HD --> E
    HA --> E

    FI --> FP["F_P = D('photo/evidence-v3', F_I, SHA256(J), E)"]
    HJ --> FP
    E --> FP
    FI --> NIP["N_IP = Node(F_I, F_P)"]
    FP --> NIP
    NIP --> T["T = Node(N_IP, F_D)"]
    FD --> T
    T --> R["R = D('fingerprinto/evidence-v3', T)"]

    R --> SIG["sigma = SHA256withECDSA(sk_I, R)"]
    KEY["Chiave privata P-256 sk_I nel Keystore hardware"] --> SIG
    AUTH["Firma autorizzata solo dopo enrollment e controlli obbligatori"] -.-> SIG
    R --> C["C = D('receipt', R, sigma)"]
    SIG --> C
    C --> FILE["commitment.bin: i 32 byte grezzi di C"]
    FILE --> OTS["OpenTimestamps: digest del file = SHA256(C)"]

    classDef hashes fill:#e9f6ee,stroke:#2f7951,color:#153e29;
    classDef signature fill:#efeaff,stroke:#7251a1,color:#342249;
    classDef finding fill:#e8f2ff,stroke:#3367a1,color:#142c45;
    class ID,HJ,HP,HR,HD,HA,FI,FD,FP,NIP,T,R,C,FILE hashes;
    class KEY,SIG,AUTH signature;
    class CHECKS,CODES,REPORT finding;
```

</details>

Qui `D` non è una concatenazione ambigua di stringhe:

```text
D(label, x1, ...) = SHA256(CBOR_deterministico([label, x1, ...]))
Node(a, b)        = D("node", a, b)
F_I               = D("install", ID_I, packageName, versionCode, signerDigest)
E                 = [placement, detachedHash, manufacturerResult, prnuResult,
                     refHash, policyHash, reportHash]
```

Gli esiti sono interi: `0` unavailable, `1` inconclusive, `2` pass, `3` fail.
`placement` è `0` embedded, `1` detached, `2` absent. Se non è detached,
`detachedHash` è null; senza reference `refHash` è null. Policy e report sono
allegati obbligatori, conservati e hashati nei loro byte originali.

`F_D` usa cinque foglie, nell'ordine indicato nel disegno. Ogni foglia è
`L_i = D("field", nome, valore)` e l'albero è
`Node(Node(Node(L0,L1), Node(L2,L3)), L4)`. I metadati firmati sono dichiarazioni:
in particolare `localTime` non diventa un orario di scatto certificato.

`SHA256withECDSA` applica SHA-256 ai 32 byte di `R` internamente, come nel codice.
La ricevuta conserva la firma DER esatta; cambiarla cambia anche `C`. I tentativi
successivi riutilizzano la ricevuta persistita. Per OTS si usa il file binario
di `C`, non l'hex di `C`, la foto o il file della ricevuta.

## 3. Cosa verifica chi riceve il fascicolo

Il verificatore ricalcola gli hash degli allegati e di `J`, ricostruisce `R` e
`C`, verifica la firma e l'enrollment, poi rivaluta i due controlli con la propria
policy fidata. Un `pass` scritto nel report firmato non basta. La prova OTS viene
verificata separatamente rispetto a `SHA256(C)`.

Se `J` viene alterato **dopo** la firma, il collegamento hash/firma non verifica.
Se un PRNU contraffatto inganna il controllo **prima** della firma, si può invece
firmare una conclusione statistica sbagliata: hash, Keystore e timestamp non
rilevano automaticamente quella falsificazione. Né il PRNU né il timestamp
dimostrano la verità della scena.

Riferimenti al codice: [PRNU](../fingerprinto/prnu.py),
[costruzione v3](../fingerprinto/receipt_v3.py),
[orchestrazione e verifica](../fingerprinto/workflow_v3.py),
[ancoraggio OTS](../fingerprinto/anchoring_v3.py).

## Rigenerare i PNG

Le immagini sono renderizzate dai due blocchi Mermaid di questo documento, a
scala 2 su sfondo bianco. Dopo una modifica ai diagrammi, rigenerare anche i PNG.
Lo script è [render_crypto_diagrams.py](../scripts/render_crypto_diagrams.py).

Il rendering usa il bundle standalone `dist/mermaid.min.js` del pacchetto
`mermaid@12.3.0`, `playwright==1.65.2` e un Chrome/Chromium/Edge già installato:

```sh
python -m pip install playwright==1.65.2
python scripts/render_crypto_diagrams.py --mermaid-bundle /percorso/mermaid/dist/mermaid.min.js
```

Se necessario, specificare il browser con `--browser /percorso/eseguibile`.
Il bundle è disponibile nella [distribuzione ufficiale Mermaid su npm](https://www.npmjs.com/package/mermaid/v/12.3.0).
Il renderer blocca le richieste di rete del browser e non scarica un browser,
non esegue test del modulo e non richiede Android SDK.
