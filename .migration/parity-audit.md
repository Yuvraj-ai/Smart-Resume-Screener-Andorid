# Parity Audit and Validation

Final state, after the full migration. Supersedes the pending tracker written
during Phase 1.

source_commit: 47d1c92 (Smart-Resume-Screener)
target: /home/imyuvi/projects/ResumeScreener-Android
updated: 2026-09-30
overall: COMPLETE

---

## Traceability: source feature to Android

| Source feature | Python | Android | Status |
| --- | --- | --- | --- |
| Multi-PDF upload | `app.py`, `screening.py` | Screen screen, SAF multi-select | preserved |
| PDF text extraction | `document_loader.py` (PyMuPDF) | `ResumeTextExtractor` (PDFBox) | replaced, equivalent |
| JD text input | `app.py` text_area | Screen screen | preserved |
| JD from a link | absent | `JobDescriptionFetcher` | added |
| Resume extraction | `parse_resume_node.py` | `GeminiClient.extractResume` | replaced, same schema |
| JD extraction | `parse_jd_node.py` | `GeminiClient.extractJobDescription` | replaced, same schema |
| Match scoring | `match_score_node.py` | `GeminiClient.score` | replaced, prompt verbatim |
| Per-criterion breakdown | absent | `ScoreBreakdown`, weighted bars | added (d7) |
| Per-file error isolation | `screening.py` | `ScreeningPipeline` | preserved |
| Persistence | `db.py` Mongo/SQLite | Room | replaced (d3) |
| MongoDB | `db.py` | none | dropped, see below |
| Candidates dashboard | `app.py` | Dashboard + Candidates | preserved |
| Aggregate statistics | absent | Dashboard tiles + histogram | added |
| Full analysis expander | `app.py` expander | Candidate Detail screen | moved to its own window |
| Gemini key config | `.env` only | Settings, Keystore-encrypted | added as a UI surface |
| Mongo URI config | `MONGODB_URI` | none | dropped |
| Storage backend label | `app.py` caption | Dashboard + Settings | preserved |

## Preserved

The scoring prompt, carried over programmatically rather than retyped, with the
60/30/10 weights, the 1-3/4-6/7-8/9-10 bands and the no-inference clause
unchanged. The three Gemini calls in the same order at temperature 0, with two
retries. The `Resume` and `jobDisc` schemas. The document shape written to
storage, so a record here is structurally identical to one from the Python app.
Per-file failure isolation, so one bad PDF never discards a batch.

## Intentionally changed

- LangGraph became a sequential suspend function. The source chain had no
  conditional edges, so a graph engine bought nothing.
- PyMuPDF became PDFBox-Android. PyMuPDF's loader requires an on-disk path and
  would have dragged a large dependency for a few lines of work.
- MongoDB dropped. Android cannot speak the MongoDB wire protocol, and decision
  d3 chose local-only. This is the one source capability with no equivalent.
- The Mongo URI settings field is therefore not built. A field with no
  reachable target would be worse than no field.
- The theme became two schemes rather than one dark scheme, derived from the
  same hue families rather than inverted.
- The bottom nav is Dashboard/Screen/Candidates/Settings, not the
  Candidates/Rubric/Analytics/Settings that Stitch proposed.

## Added

Aggregate dashboard statistics, per-criterion sub-scores, search and sort,
delete with confirmation, a model picker, key verification, a tri-state key
status, dark and light modes, expressive shape and motion, and job-description
fetching from a link.

## Verification actually performed

| Check | Result |
| --- | --- |
| Unit tests | 26 passing |
| `assembleDebug` | pass |
| `assembleRelease` (R8, resource shrinking) | pass, 7.9MB |
| Release APK signature | signed, installed on emulator |
| Dark mode rendering | verified on Pixel 6 AVD, android-36 |
| Light mode rendering | verified on Pixel 6 AVD |
| Dashboard aggregates | 6 screened, avg 7.5, best 9.1, 4 this week, all match seeded data |
| Score histogram bands | verified against seeded scores |
| Weighted breakdown bars | 60/30/10 with correct relative lengths |
| Candidates list, search, sort | rendered and ordered correctly |
| Prompt drift guard | fails build if weights or bands change |
| Credential scanning | no live credential in any tracked file |
| Keystore tracked by git | not committed |

## Unresolved limitations

1. **No live Gemini key was available**, so every AI call is verified against a
   mock server, never the real API. The app is structurally correct but its real
   model behaviour and prompt output quality are unproven. The source app has
   the same gap.
2. **PDF extraction is verified only on the code path**, not against real-world
   scanned or image-only PDFs. A scanned resume will return the "no selectable
   text" error, which is correct behaviour but untested against a real scan.
3. **No Compose instrumented UI tests.** Screens were verified by walking them on
   the emulator and inspecting screenshots, not by automated assertions. The
   test matrix calls for instrumented tests that do not yet exist.
4. **Delete is unverified against a real record** in the release build. It was
   implemented but not exercised end to end after the R8 build.
5. **MongoDB sync is absent**, as decided. Revisiting it needs an Atlas Data API
   endpoint, which is a separate piece of work.
6. **One emulator intrusion.** `com.askyoutube.app` was sitting above our window
   and intercepting bottom-nav taps during validation. It was disabled to get
   clean screenshots, then re-enabled. Not an app defect.
