<!-- AGENT CONTEXT: Work provenance for the sparse slide revision, not projected material or a new crypto/device validation. -->

# Sparse deck revision record

Date: 21 November 2026. Branch: `leomaster`; pre-edit baseline `37a8f59`. Tool: OpenAI Codex.
Input: the author's [Claude revision](../claude-slides-revision.md), preserved
unchanged. The author supplied the disclosure categorisation; no independent
authorship/human-code-review assessment was performed.

## Changes

- Split title/problem and move disclosure to the end of the main sequence.
  Eight sparse main screens plus nine backup pages: 17 pages in the technical
  edition. Timing remains 175 planned seconds plus five seconds buffer,
  including one demo; spoken script is 238 words (crypto 104).
- Shortened titles, two-column evidence table and repeated explanatory notes.
  Retained the construction/PRNU/beacon equations, test counts/SHA and bounds.
  Combined parser/profile isolation and hybrid-v2 into a single backup page.
- Kept not-detected, copyable, not-established, keys-in-RAM, not-hardware-native,
  no-RSA and residual-trust cautions. Replaced visible placeholders with actual
  pending/not-yet-measured states. Removed editing-process and duration/speed
  text from projected pages; production details remain here and in pitch.md.
- Added supplied to.png unchanged, large on the first screen and small in the
  common footer, including backups. Logo is opaque white; viewed against the
  light theme. Aspect ratio preserved and missing-file fallback provided.
- Added the supplied 11:42 video unchanged, a silent 1.2x pitch copy and a poster
  from 12 seconds. Retained both older button targets. Did not reinterpret
  queued/unavailable application states as verified origin or publication.
- Disclosure now reads Theory: Human; Code/Slides: LLM-generated; tools OpenAI
  Codex and Claude. No new claim of human review.

## Media hashes

| Asset | Duration / preservation | SHA-256 |
|---|---|---|
| `video_2026-11-21_11-42-32.mp4` | 35.17 s | `71a697b631e03c976f6c98bdb6f57777d2a05fa41969789f3da1ec05ed76a41a` |
| `demo-latest.mp4` | 29.28 s | `a7fba2cef5e2a8b95c8807e12cd2c206129b5edbd60b864495c7dc686992178b` |
| `demo-pitch.mp4` | preserved original bytes | `280cd7333537f1a6700b44e7afb86093ab3aad9c7feae5507a0af0c282a8ac19` |
| `video_2026-11-21_11-11-30.mp4` | preserved original bytes | `f14142a5cb700ad0cb9ad1383c7ddec9f7d9591816457b8167bdd3440fbc45c3` |
| `to.png` | preserved original bytes | `8ee0f5a0e0c97ee65225e04d139d6c904ab175eb8d9810b0f824fc5ff321645d` |

## Checks and limits

Built main.tex and extended.tex with the existing latexmk/pdfLaTeX toolchain.
Final layout logs were inspected; all 17 pages were rendered and visually
reviewed, with the final affected backup pages checked again after spacing fixes.
The export text is checked for absent placeholders, video durations/speed labels
and editing-process notes, and for retained technical cautions and `8fb3b3c`.

The source archive is compiled from a fresh isolated extraction. Its logo is
also temporarily removed in that scratch copy to check the fallback; no original
asset is deleted or changed. The PDF Launch actions retain the two old targets
and add the latest demo; package file existence/byte identity is checked.
This does not test clicking in the actual presentation GUI; that playback and
a timed rehearsal remain to be done on the presentation computer.

No new cryptographic test run, Android build, real-camera verification, network
anchor verification or independent v1 recomputation was performed. Recorded
10 beacon + 3 receipt-v3 + 6 reducer tests at code SHA `8fb3b3c` are inherited
provenance, not execution results of this slide edit.

Final check: 149 local documentation links resolve; eight/17-page normal and isolated builds have clean layout logs. Four external Launch actions per PDF include both unchanged older targets. Source and portable archives contain byte-identical media. The isolated eight-page missing-logo build exits 0 and the scratch logo is restored. All seventeen rendered pages were inspected, with final S/H and attack-table spacing rechecked. No GUI launch or timed rehearsal was performed.
