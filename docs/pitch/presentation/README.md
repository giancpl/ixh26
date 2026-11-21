<!-- AGENT CONTEXT: Reproducible Lagrange slide deck for the complete three-minute FingerprinTO pitch. Preserve the live-demo slot, the mathematics/claims boundary and the 180-second total. Do not add unsupported implementation claims or invented vendor API limitations. -->

# FingerprinTO pitch slides

The presentation is in [main.tex](main.tex), with an exported
[six-page PDF](../fingerprinto-pitch.pdf) and an
[Overleaf source archive](../fingerprinto-pitch-overleaf.zip).
Spoken text and operator direction live in [pitch.md](../pitch.md);
the visual brief is [slides.md](../slides.md).

## Theme and assets

The user requested **Lagrange** after clarifying the template choice.
`beamerthemeLagrange.sty` is copied byte-for-byte from the sibling project
`2026-10-Goteborg-presentation`; it is loaded with its default `lagrange`
profile. Source SHA-256:
`18d49b678ee71066691de4d294443db384a0096e3d94d5f5fde2728504ca063d`.
The original project is untouched. No Peano files are used or packaged.
Cabin/sansmathfonts/Bera Mono follow the user's existing Lagrange setup.
Only the footline is overridden locally in `main.tex` to remove the upstream
extreme negative vertical skips; the theme file remains unchanged.

The opening uses the corrected [FingerprinTO Stand artwork](../assets/fingerprinto-stand.png).
The supplied `to.png` remains untouched and is not required by the build.

## Timing and delivery

| Page | Content | Clock |
|---|---|---|
| 1 | Product/problem, no separate title slot | 0:00-0:40 |
| 2 | Capture attestation, statistical PRNU, byte integrity | 0:40-1:15 |
| 3 | Exact v3 root/signature/commitment and OTS start digest | 1:15-1:30 |
| 4 | Interoperability and proposed lasting verification | 1:30-1:50 |
| 5 | Live verifier / screen share | 1:50-2:20 |
| 6 | What's next, including the close | 2:20-2:55 |

Five seconds remain as buffer. The revised spoken script has **268 words**,
including the 30-second demo narration; the crypto block retains 119 words.
Formulas are visual material, not additional spoken text. The sequence has
not been rehearsed: count and planned timings do not establish delivery time.
No overlay pages or automatic section interstitials are added.

The demo screen reserves space, not an embedded live application or a fake
success screenshot. Follow `pitch.md` to prepare the synthetic signed bundle,
show pending enrollment honestly, then show changed-byte verification failure.
Switch to the actual terminal/screen share during the 30-second slot. A recording
must be labelled. No network confirmation wait, live key provisioning or extra
beacon demonstration is included.

## Future work and defensible wording

The final slide occupies the existing status/closing slot. Optimistic verification
is a research direction for richer claims and explicit challenges; dispute
rules, admissible evidence, challengers and incentives remain to be designed.
It does not mean verifying arbitrary world facts without evidence.

Camera challenges require a trusted binding between a fresh challenge and the
captured image. We have not validated such a vendor API on our capture route.
Android's documented
[request tag](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Builder#setTag(java.lang.Object))
is an app correlation value not used by the camera device; the
[capture intent](https://developer.android.com/guide/components/intents-common#Camera)
does not specify that attested binding. These sources support why those APIs
alone are insufficient. They do not prove that vendor-specific support cannot
exist. No fabricated engineering history or universal API-impossibility claim
is used. Manufacturer adapters and real PRNU/beacon trials remain explicit
validation items. Status statements refer to this reviewed crypto branch;
later native integration on main needs a separate assessment before presenting.

## Build and verification

From this directory, with pdfLaTeX, Beamer and the installed font/theme packages:

```bash
latexmk -pdf -synctex=1 -interaction=nonstopmode -file-line-error -halt-on-error -outdir=build main.tex
```

The Overleaf archive has `main.tex` at its root and rewrites only the image
path to `assets/fingerprinto-stand.png`; the theme remains identical. It contains
the required source/theme/artwork and no auxiliary build files. Select pdfLaTeX.

Validation: local latexmk build, six-page PDF, no overfull/underfull boxes in the
final log, and rendered visual review of all six pages. Mathematical construction
was checked against the current v3 specification. The archive is also built
from an isolated extraction before publication. These are document checks,
not a new crypto test run, camera API integration or timed rehearsal.

The supplied project artwork was already generated with AI assistance; this
deck and the aligned pitch/visual brief were prepared with OpenAI Codex.
