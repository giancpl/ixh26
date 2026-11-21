# Regulations: EU/Italian legal positioning and commercial claims

Research date: **21 November 2026**. Technical baseline: `6ea741a` on `leomain`.
This is a product/legal mapping from official sources, not a conformity
assessment or an opinion on admissibility in a particular dispute. Legal rules,
guidance and provider qualification must be checked again before launch.

## Commercial conclusion

The credible proposition is a **verifiable photographic evidence dossier**:
integrity checks, an installation signature, separately assessed provenance
findings, and independently checkable anchoring evidence. This supports a
customer's evidential/document-management process. It does not currently justify
a blanket EU/Italian compliance label. The branch remains an offline prototype;
physical-camera performance, manufacturer validation and Android integration
retain the acceptance gates in `CRYPTO-V3-ROADMAP.md`.

The clearest potential premium feature is **qualified electronic time stamping
through an existing qualified trust service provider**, alongside OTS. That is
a proposed extension, not an implemented or already qualified service.

## Relevant rules and the actual fit

| Framework | Legal relevance | Position for this module |
|---|---|---|
| eIDAS, Regulation 910/2014, Arts 41–42 | Nonqualified electronic timestamps cannot be excluded solely for electronic form/nonqualification. Qualified timestamps have a specific presumption concerning time accuracy and bound-data integrity. | OTS does not establish qualification. Integrating a qualified timestamp can strengthen the temporal dossier; it still does not prove capture time or scene truth. [eIDAS](https://eur-lex.europa.eu/eli/reg/2014/910/2024-10-18/eng) |
| Italian CAD, Art. 20(1-bis) | Outside the specified signature/identified-author processes, evidential weight is assessed in court with regard to security, integrity and immutability. | A verifiable bundle may support that assessment. It is not automatic full proof, author identification or CAD-compliant conservation. [Official CAD text](https://docs.italia.it/italia/piano-triennale-ict/codice-amministrazione-digitale-docs/it/stabile/_rst/capo_II-sezione_I-articolo_20.html) |
| AI Act, Regulation 2024/1689, Art. 50(2),(4) | Certain AI-generated outputs require machine-readable marking; deployers of deepfakes have disclosure duties. | A camera-provenance tool could support a broader transparency workflow. It currently neither labels synthetic outputs nor fulfils those duties. A real camera can photograph a generated image on a screen. [Current consolidated AI Act](https://eur-lex.europa.eu/eli/reg/2024/1689/2026-07-27/eng) |
| GDPR, Arts 5, 25 and 32 | Data minimisation, privacy by design and proportionate security/integrity measures are relevant when personal data are processed. | Off-chain images and protected keys can contribute technical measures. Lawful basis, notices, roles, access, retention, rights and deployment assessment remain necessary. No blanket GDPR-compliance finding follows. [GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj) |
| Italian D.L. 135/2018, Art. 8-ter(3),(4), converted by L. 12/2019 | Links DLT document storage to electronic temporal validation and delegates technical standards to AgID. | Interesting national connection, but applicability to this hash-anchoring design and the required standards is not established by this research. Do not claim automatic compliance. [Official provision](https://def.giustiziatributaria.gov.it/DocTribFrontend/executePrintArticolo.do?articolo=Articolo+8+ter&codiceOrdinamento=0000000000000089999900003000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000&id=%7B02ABFD43-08A6-4605-B233-899C3E8E043C%7D) |
| eIDAS electronic ledgers, Arts 45k–45l | Qualified ledgers receive presumptions about ordering/integrity and require qualified providers and other safeguards. | Public Bitcoin anchoring does not establish a qualified electronic ledger. Ordering/integrity would not certify the truth of a photograph. [Amending Regulation 2024/1183](https://eur-lex.europa.eu/eli/reg/2024/1183/oj) |

For photographs, Civil Code Art. 2712 is another evidential reference: its
rule depends on whether the opposing party disputes correspondence to the facts.
Cryptographic integrity does not settle that factual correspondence. This is
an evidential use case, not product certification.
[Official Civil Code provision](https://def.giustiziatributaria.gov.it/DocTribFrontend/executePrintArticolo.do?codiceOrdinamento=0000000000027120000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000&id=%7BE6A78DCE-1883-4232-BE4C-E9524D528144%7D&idAttoNormativo=%7B9E93F1BE-06AE-4F24-8E9D-B838F7E0C2E6%7D).

## Important boundaries for sales language

The installation's P-256 signature binds a key to bytes. It does not identify a
natural-person signatory or establish their intent. Hardware protection alone
does not make it an Italian legal "firma digitale", an advanced signature or a
qualified signature. Those categories have additional requirements.
[eIDAS, Arts 3, 25–26](https://eur-lex.europa.eu/eli/reg/2014/910/2024-10-18/eng).

OTS inclusion/calendar promises must retain their existing pending/confirmed
distinction. Blockchain time and localTime must not be presented as the exact
time of camera exposure. A signature may authenticate an installation while
origin findings remain unavailable. PRNU remains statistical and uncalibrated
for physical devices here. These are limitations of our implemented evidence
model, not legal presumptions inferred from successful unit tests.

Keeping images off-chain is useful, but hashing does not automatically anonymise
personal data. Installation identifiers and sensor references can become linked
to people; evaluate that linkage. The EDPB's final blockchain guidelines favour
careful off-chain/proof-based designs while retaining data-protection duties.
The current C must not be marketed as a proven perfectly hiding commitment or a
guarantee that public anchoring falls outside GDPR.
[EDPB Guidelines 02/2025, final version dated 7 July 2026](https://www.edpb.europa.eu/documents/guideline/guidelines-022025-on-processing-of-personal-data-through-blockchain_en).

On Italian DLT standards, targeted official-source searches found AgID's
working-group establishment, not a final technical instrument sufficient to
assess this implementation. This search result is **not proof that no instrument
exists**. Obtain the applicable instrument and a mapping of OTS hash anchoring
to it before using Art. 8-ter as a conformity claim.
[AgID Determination 116/2019](https://trasparenza.agid.gov.it/download/4203.html).

AI Act timing also needs current text: Article 50 generally applies from
2 August 2026; Article 111(4) gives providers of systems placed on the market
before that date until 2 December 2026 for Article 50(2). This is not a blanket
deferral of all transparency duties. Visible disclosure cannot generally be
replaced by an invisible provenance receipt.
[Commission transparency FAQ](https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act).

## Proposed route to a stronger, precise claim

1. Finish the existing device/issuer/calibration gates. Sell integrity/provenance
   findings only within the scope actually validated, including honest absent
   and inconclusive outcomes. Do not turn PRNU into a generic "not AI" detector.
2. Add an optional external qualified timestamp over the existing C, with its
   own preserved token, issuer/service identity and independent verification.
   Keep it separate from the OTS proof and from origin. The service's qualified
   status must be verified in the appropriate trusted list, not inferred from
   a vendor logo or a valid certificate alone.
   [Commission trusted-list guidance](https://digital-strategy.ec.europa.eu/it/policies/eu-trusted-lists).
3. Assess the timestamp integration against the applicable requirements,
   including Implementing Regulation 2025/1929 and its referenced ETSI profiles.
   Integrating a provider does not qualify our whole product or make us a QTSP.
   [Implementing Regulation 2025/1929](https://eur-lex.europa.eu/eli/reg_impl/2025/1929/oj).
4. Define the operational dossier: original bytes, attachment hashes, validation
   reports/policy versions, key/status history, timestamps and custody events.
   If the customer needs regulated document conservation, integrate the broader
   process, roles, metadata and preservation requirements rather than relabel
   our ZIP exporter as a conservation service.
   [AgID document formation/management/conservation guidelines](https://www.agid.gov.it/sites/agid/files/2024-06/Linee_guida_sul_documento_informatico.pdf).
5. Assess the actual deployment's personal-data flows and publish only claims
   supported by its technical and organisational controls. The crypto module
   can supply evidence for an assessment; it cannot replace that assessment.

Steps 2–5 are a proposed commercial/compliance backlog, not changes made to the
cryptographic protocol in this research task. No new trust-service calls,
provider purchases or legal certification have been performed.

## Suggested wording

For the current prototype demonstration:

> Modulo sperimentale per creare fascicoli fotografici verificabili, con
> controlli d'integrità, firma dell'installazione ed evidenze di ancoraggio
> temporale. I risultati di provenienza sono distinti dalle dichiarazioni del
> firmatario e dalla validità dell'ancoraggio.

For a future validated deployment, a possible value proposition is:

> Evidenze fotografiche verificabili a supporto dei processi documentali e
> probatori, con opzione di marca temporale qualificata eIDAS erogata da un
> prestatore qualificato.

Use the second wording only after implementing and validating that option.
For each issued dossier, identify the actual qualified service and verified
result. Avoid "certificato UE/AgID", "foto legalmente autentica", "prova
incontestabile", "firma digitale" for the installation signature, "GDPR
compliant" or "AI Act compliant" without a documented scope-specific basis.

Potential customer contexts are inspections, insurance assessments, maintenance
and property-condition reports. These are our commercial hypotheses, not
sectors shown by this research to be legally required to buy or use the module.
