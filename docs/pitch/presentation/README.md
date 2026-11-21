<!-- AGENT CONTEXT: Eight sparse main screens plus nine Q&A backups. Preserve equations, explicit security limits and all video targets. The author declares theory Human, code/slides LLM-generated. Build and production notes belong here, never on projected pages. -->

# FingerprinTO presentation

- [Pitch PDF](../fingerprinto-pitch.pdf): eight main screens.
- [Technical PDF](../fingerprinto-pitch-technical.pdf): those eight plus nine Q&A pages.
- [Portable PDF/video bundle](../fingerprinto-pitch-presentation.zip).
- [Overleaf source archive](../fingerprinto-pitch-overleaf.zip).
- [Spoken script](../pitch.md) and [visual brief](../slides.md).

## Design and provenance

The [author-supplied Claude revision](../claude-slides-revision.md) is preserved
unchanged. This revision shortens titles and tables, removes projected process
notes/placeholders/video durations, keeps the mathematical construction and
merges parser bounds and hybrid signatures into one backup page. The original
[Claude outline](../claude-pitch.md) remains available for provenance.

The user's **Lagrange** theme is copied unchanged from the sibling project
`2026-10-Goteborg-presentation`, default `lagrange` profile; SHA-256:
`18d49b678ee71066691de4d294443db384a0096e3d94d5f5fde2728504ca063d`.
Its original project is untouched. `main.tex` locally overrides the problematic
upstream footer and adds the [supplied logo](../assets/to.png), large on the title
screen and small on every page. The logo's proportions and original bytes are
preserved. Its background is opaque white; the light Lagrange background gives
adequate contrast. `IfFileExists` provides a text fallback when the logo is absent.

The author explicitly supplied the disclosure: **Theory: Human; Code and
Slides: LLM-generated.** Named tools are OpenAI Codex and Claude. The slide
does not assert human code review. This records the author's declaration,
not an independent authorship audit.

## Timing

| Clock | Screen |
|---|---|
| 0:00-0:10 | Logo / title |
| 0:10-0:40 | Actual capture/sign/share flow |
| 0:40-1:05 | Nostr event, published JPEG, OTS checks |
| 1:05-1:30 | Optional device evidence receipt |
| 1:30-1:50 | Beacon token / limits |
| 1:50-2:20 | Latest supplied demo |
| 2:20-2:50 | What's next / close |
| 2:50-2:55 | AI-use disclosure |

Five seconds remain as buffer. The script has **237 spoken words**, including
demo narration; the crypto segment contains 100 words. Formulas are visual
support, not additional speech. These timings are planned, not rehearsed.
Do not present the nine backup pages during the three-minute pitch.

## Video files and playback

Screen 6 uses the latest supplied recording:

- Original: [11:42 video](../assets/video_2026-11-21_11-42-32.mp4), approximately
  35.17 seconds, H.264/AAC, 576x1280, unchanged.
- Pitch copy: [demo-latest.mp4](../assets/demo-latest.mp4), silent, 1.2x,
  approximately 29.3 seconds; no selective content cuts.
- Poster: [demo-latest-poster.png](../assets/demo-latest-poster.png), extracted
  at 12 seconds from that original.

The two older button targets are retained exactly:
`assets/demo-pitch.mp4` and `assets/video_2026-11-21_11-11-30.mp4`.
Only one video is played in the timed pitch. Older recordings are optional.
Duration and speed are deliberately kept in these production notes rather
than projected. The new clip shows capture/preparation and a queued pin;
queued/unavailable operations are not narrated as confirmed publication,
trusted enrollment, mainnet anchoring or sensor provenance.

The poster and three buttons are **external PDF Launch links**, not in-page
video streams. Keep `assets/` beside the exported PDFs. Some PDF/browser
viewers block launch actions; pre-open the MP4 and switch windows if needed.
Playback in the actual presentation viewer still requires rehearsal.

Video processing uses FFmpeg 7.1 through isolated imageio-ffmpeg 0.6.0 tooling
under ignored `.tools-android/video-tools`, not a new project runtime dependency:
`-vf setpts=PTS/1.2 -an -c:v libx264 -crf 20 -preset medium -pix_fmt yuv420p -movflags +faststart`.
All supplied originals remain unchanged. File hashes and measured properties
are recorded in [REVISION-LOG.md](REVISION-LOG.md).

## Build

From this directory, using pdfLaTeX and the installed Cabin/sansmathfonts/Bera
Mono setup from the user's Lagrange presentation:

```bash
latexmk -pdf -synctex=1 -interaction=nonstopmode -file-line-error -halt-on-error -outdir=build main.tex
latexmk -pdf -interaction=nonstopmode -file-line-error -halt-on-error -outdir=build extended.tex
```

The Overleaf archive places `main.tex`, `extended.tex`, `backup.tex`, the unchanged
theme and required assets at its root; only `../assets/` source paths are
rewritten to `assets/`. Select pdfLaTeX. The portable archive contains both PDFs
and the required video sidecars, including the new unchanged original.

## Technical and wording boundaries

The transcript, profile bounds, fixture counts and recorded SHA `8fb3b3c` are
unchanged. Decoder/token validity does not prove optical entry; splicing,
relay and within-session reuse remain undetected. PRNU remains copyable and
uncalibrated on real sensors. Hardware wrapping does not make the hybrid secrets
hardware-native; they enter RAM, and classical trust remains. No RSA was added.

The optimistic-verification/camera-challenge cards remain future work. No
attested API has been validated on the reviewed capture route; ordinary request
metadata alone is insufficient. Source-backed rationale stays in `pitch.md`,
not on the screen. No new runtime tests, device result or independent theoretical
proof is implied by this presentation revision. Actual checks are in the work log.

## Main reconciliation and app-flow correction

Before PR merge, main `18ae342` was integrated. Its Android/web/backend/runtime
code and package extras are preserved; the separate beacon module is the only
additional runtime file relative to that main snapshot. Current app setup and
[recorded Pixel acceptance](../../reviews/PIXEL9_PRO_2026-11-21.md) remain
authoritative rather than older source-branch integration-gap statements.

At the author's request, screen 2 no longer uses Sensor A / Phone B. It shows
the implemented capture, prepare-JPEG, sign-post and publication path. Screen 3
uses only published-image SHA-256 and the Nostr event signature, with separate
event/bytes/timestamp checks. Screen 4 explicitly presents v3 as an optional
device-identity path, separate from the Nostr profile key; the beacon screen
is reduced to one session-binding formula. Full v3 equations stay in the
byte-contract backup. Source checks used upp_protocol/protocol.py,
contracts/README.md and native PhotoProtocol.kt, not the obsolete draft model.

The merged crypto suite passed 97 tests and 134 subtests. This result does not
replace the earlier recorded device tests or assert new Android execution.
The spoken script is 237 words; revised PDF layouts are checked before merging.
