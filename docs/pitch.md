<!-- VINCOLO PER GLI AGENTI: Questo file contiene SOLO il testo da pronunciare per la parte crypto del pitch. Durata massima inderogabile: 90 secondi (1,5 minuti), comprese le pause. Mantenere il testo parlato entro 140 parole, puntando a circa 60-75 secondi per lasciare margine. Ad ogni modifica ricontare le parole; per aggiungere un concetto, tagliarne un altro. Non aggiungere appendici, dettagli tecnici o nuove sezioni da leggere. Prima della presentazione verificare la durata con una prova cronometrata. Distinguere sempre prototipo implementato, obiettivi e validazioni ancora mancanti. -->

# Pitch — parte crypto

Una firma crittografica protegge i byte di una foto, ma non dimostra che provengano da un sensore.

FingerprinTO propone un formato verificabile per distinguere tre evidenze: attestazione della cattura, quando disponibile; compatibilità statistica con il sensore tramite PRNU; semplice integrità del file.

Nel prototipo, hash e firme legano immagine, report e policy a una ricevuta, con una chiave d’installazione protetta dal Keystore hardware. OpenTimestamps permette di verificarne l’ancoraggio su Bitcoin. La blockchain documenta l’esistenza dei dati, non la verità della scena.

Il contributo che proponiamo è rendere interoperabili queste evidenze e conservarne la verificabilità quando cambiano o scompaiono i servizi del produttore.

Il nucleo crittografico è implementato. Le integrazioni reali e la calibrazione restano da completare. Ogni garanzia deve essere proporzionata alle evidenze disponibili.
