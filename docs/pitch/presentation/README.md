<!-- AGENT CONTEXT: Reproducible Lagrange slide deck for the complete three-minute FingerprinTO pitch. Preserve the live-demo slot, the mathematics/claims boundary and the 180-second total. Do not add unsupported implementation claims or invented vendor API limitations. -->

# FingerprinTO pitch slides

The timed presentation is in [main.tex](main.tex), with an exported
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

The opening now follows Claude's counterexample. The [FingerprinTO Stand artwork](../assets/fingerprinto-stand.png) remains available but is not used in this technical revision.
The supplied `to.png` remains untouched and is not required by the build.

## Timing and delivery

| Page | Content | Clock |
|---|---|---|
| 1 | Phone A / signing phone B counterexample | 0:00-0:40 |
| 2 | Five distinct evidence properties, S/H boundary | 0:40-1:05 |
| 3 | Binary tree, exact v3 root/signature/commitment | 1:05-1:30 |
| 4 | Implemented beacon token and undetected attacks | 1:30-1:50 |
| 5 | Supplied PhoTO recording, 1.5x / 29.4 seconds | 1:50-2:20 |
| 6 | What's next, including the close | 2:20-2:55 |

Five seconds remain as buffer. The revised spoken script has **242 words**,
including the 30-second demo narration; the crypto block contains 104 words.
Formulas are visual material, not additional spoken text. The sequence has
not been rehearsed: count and planned timings do not establish delivery time.
No overlay pages or automatic section interstitials are added.

The demo screen now includes a clickable frame and Play demo video button.
They launch `assets/demo-pitch.mp4` relative to the exported PDF in `docs/pitch`.
The supplied original is preserved at `assets/video_2026-11-21_11-05-46.mp4`
(44.143 seconds, 576x1280). The derived H.264/yuv420p MP4 is silent at 1.5x,
29.41 seconds, so narration and playback fit the existing 30-second slot.
The speed/recorded status is visible. No content is selectively cut.

This replaces the proposed synthetic byte-tampering demo. The recording shows
capture preparation, private calibration controls and device enrollment status
in a local/regtest-labelled UI, including unavailable enrollment due to a missing
authority pin. It is not a demonstrated successful end-to-end verification,
mainnet anchoring or physical-origin result. Pitch narration is aligned to those
visible states. The user supplied the video; frame review is not a device test.

Use the [portable presentation bundle](../fingerprinto-pitch-presentation.zip)
to keep the PDF and relative `assets/` video path together. Some viewers block
PDF launch actions: pre-open the MP4 and switch windows if necessary. The video
is linked, not an in-page multimedia stream; browser playback is not guaranteed.
Do not play the 44-second original during the three-minute pitch. The MP4 has
been decoded and its duration checked; GUI launch playback still needs rehearsal
in the actual PDF viewer/player used on stage.

Video processing used FFmpeg 7.1 via isolated imageio-ffmpeg 0.6.0 tooling under
the ignored `.tools-android/video-tools` directory. Reproduction filter:
`-vf setpts=PTS/1.5 -an -c:v libx264 -crf 20 -preset medium -pix_fmt yuv420p -movflags +faststart`.
The poster is extracted at 18 seconds from the unchanged original.

Original SHA-256: `fd9ad43930a27b759312d29e4fda1e31210c8463a98320f20e1e941ca704f35a`.
Derived SHA-256: `280cd7333537f1a6700b44e7afb86093ab3aad9c7feae5507a0af0c282a8ac19`.

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
the required source/theme/artwork, demo poster and MP4, and no auxiliary build files. Download the video alongside the PDF when presenting outside Overleaf. Select pdfLaTeX.

Validation: local latexmk build, six-page PDF, no overfull/underfull boxes in the
final log, and rendered visual review of all six pages. Mathematical construction
was checked against the current v3 specification. The archive is also built
from an isolated extraction before publication. These are document checks,
not a new crypto test run, camera API integration or timed rehearsal.

The supplied project artwork was already generated with AI assistance; this
deck and the aligned pitch/visual brief were prepared with OpenAI Codex.

## Adaptation of the supplied Claude draft

[claude-pitch.md](../claude-pitch.md) is preserved unchanged. It proposes an
8-10-minute presentation, so it is not copied page-for-page into the three-minute
slot. The main deck maps source 1-2 to the opening, 3/5 to the property table,
6 to the construction and 9-10 to the beacon/limits slide. It retains the supplied
recording and the requested future-work close.

Source 4/5/7/8/10/11/12/13 and B1-B3 also feed **11 Q&A backup pages**, available
in [fingerprinto-pitch-technical.pdf](../fingerprinto-pitch-technical.pdf). Build
`extended.tex` for the six main pages plus those eleven backups (17 pages total).
The timed PDF never includes them. Detailed tree fields, reducer, PRNU/SPCE,
attacks, authorities, parser limits, hybrid signatures, vectors and AI disclosure
are therefore available without rushing the live pitch. Italian SAY notes remain
in the Markdown sources, not on projected slides.

Corrections to source claims: v3 golden fixtures already exist; beacon software
is implemented but physical trials remain unverified; signature validity does
not mean every independent origin check passes; the client may invoke a
hardware key without extracting it. Optical decoding cannot prove entry through
the lens. Unknown/missing codes are unavailable evidence, not detection of every
pre-generated file. Conditional content windows require honest release/clock
and independently justified anchor bounds, not precise Bitcoin wall-clock time.
C2PA/provenance signatures require accepted capture semantics.

No new crypto execution tests or independent v1 recomputation were performed for
this adaptation. The source's reported v1 check is not relabelled as our result.
Human-review/team-wide AI disclosure remains explicitly [TBD] and must be
completed by the team; the slides do not assert compliance is already complete.

For this adaptation, both the six-page and seventeen-page editions were
compiled locally and from a fresh isolated extraction of the source archive.
Final logs contain no overfull/underfull boxes. All seventeen rendered pages
were reviewed; the reducer/PRNU spacing fixes were re-rendered and inspected.
Both PDFs retain the two launch links to the unchanged 29.41-second recording.
All 140 local documentation links resolve. No timed rehearsal, GUI playback or
new device/cryptographic execution result is implied by these document checks.
