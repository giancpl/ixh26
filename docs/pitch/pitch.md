<!-- AGENT CONSTRAINT: This file contains ONLY the spoken crypto pitch, coordinated with docs/pitch/slides.md. Absolute maximum: 90 seconds TOTAL, including pauses and slide transitions. Keep spoken text within 140 words, targeting approximately 60-75 seconds to leave a margin. Recount words after every edit; adding a concept requires cutting another. Formulas and visual details belong in slides.md and MUST NOT be spoken. Do not add spoken appendices or sections. A timed rehearsal is required before presenting. Always distinguish the implemented prototype, proposed contributions and outstanding validation. -->

# Crypto pitch

<!-- SLIDE 1: boundary and available evidence; approximately 35 seconds. -->

Without a trusted capture path, no signature or blockchain can prove that an image came from a physical sensor. The problem happens before signing.

Our answer is a single, verifiable format that accepts different sources of evidence and gives each capture the assurance it can support: manufacturer capture attestation where available, statistical compatibility through PRNU, or simply file integrity.

<!-- SLIDE 2: receipt and anchoring; approximately 15 seconds. Do not read formulas. -->

Our prototype binds the image, evidence and policy into a signed receipt, anchored through OpenTimestamps on Bitcoin. This records commitments, not the truth of the scene.

<!-- SLIDE 3: proposed contribution and current status; approximately 20 seconds. Target approximately 70 seconds overall; absolute maximum 90 seconds. -->

Our proposed contribution is interoperability and lasting verification: preserving certificates, manifests and trust evidence, so verification need not disappear with a vendor.

The crypto core is implemented. Real capture integrations and calibration remain unfinished.
