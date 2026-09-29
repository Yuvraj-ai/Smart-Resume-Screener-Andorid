# Design Handoff

## Stitch assets

| Item | Value |
| --- | --- |
| Project | `projects/13230883889024874888` ("Resume Screener") |
| Design system | `assets/9969633650903088672` ("Ink & Mint Screener") |
| Settings screen | `5cf1f2e44f744cc08a20827a19217a33` |
| Local copies | `.stitch/designs/`, rendered into `design/screens/` |

The design system was seeded with the palette extracted from the earlier Sleek
run, so Stitch and the Compose theme in `ui/theme/` describe the same system.
Compose is the implementation of record; the designs are reference.

To re-render the Stitch HTML: `python3 -m http.server 8899` from `.stitch/designs`.

## Provenance

- **Sleek** produced Dashboard, Screen, Candidates and Candidate Detail. Those
  four are the primary visual reference.
- **Stitch** produced the Settings screen body, which Sleek ran out of credits
  for. Sleek's own bottom nav already included a Settings tab, so only the body
  was missing.

## Corrections to apply during implementation

The Settings design is good but Stitch drifted from the agreed spec in four
places. These are deliberate deviations from the design, not bugs in it.

1. **Bottom navigation is wrong.** Stitch drew
   `CANDIDATES / RUBRIC / ANALYTICS / SETTINGS`. The agreed IA
   (decision d4) is `Dashboard / Screen / Candidates / Settings`. Drop "Rubric"
   and "Analytics"; the rubric breakdown lives inside Candidate Detail, and
   analytics live on the Dashboard.
2. **Model names are wrong.** The design shows "Gemini 1.5 Flash" and
   "Gemini 1.5 Pro". The source pipeline uses `gemini-2.5-flash` for parsing and
   `gemini-2.5-pro` for scoring. Use the real ids, and read them from settings
   rather than hardcoding.
3. **Version is invented.** Design says "1.2.0 (Build 42)". Actual is `0.1.0`.
   Read from `BuildConfig`.
4. **Storage section wording is slightly off.** The design says "Local SQLite
   Database". The app uses Room over SQLite, and there is no upload path at
   all. Keep the honest "stored on this device" claim, but name Room.

Keep from the design: the tri-state key status pill, the quiet factual security
line under the key field, the "Test connection" affordance, the AI Studio link,
the model selector rows with their helper text, and the "100% On-device" badge.

## Coverage

| Screen | Source | State |
| --- | --- | --- |
| Dashboard | Sleek | Designed |
| Screen | Sleek | Designed |
| Candidates | Sleek | Designed |
| Candidate Detail | Sleek | Designed |
| Settings | Stitch | Designed |

All five are designed. No screen is still undesigned.

## Suggestion from Stitch worth noting

Stitch suggested adding an advanced prompt-customisation section. Not adopted:
the scoring rubric is deliberately fixed and guarded by
`ScoringPromptTest`, because letting users edit it silently changes what a score
means. Out of scope unless the user asks for it.
