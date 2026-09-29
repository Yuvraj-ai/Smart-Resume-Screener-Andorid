# Analysis: Smart-Resume-Screener (Streamlit) → Android

Source repo: `/home/imyuvi/projects/Smart-Resume-Screener`
Target repo: `/home/imyuvi/projects/ResumeScreener-Android`
Date: 2026-09-30

Evidence base: actual source at commit `47d1c92` of the source repo, plus the
skill's `analyze_repository.py`, `detect_streamlit.py`, and `detect_secrets.py`.

---

## 1. Observed repository structure

```text
app.py                      # Streamlit UI, single page
test_screening.py           # 7 self-checks, no API key or DB required
requirements.txt            # 8 pinned deps
backend/
  graph.py                  # LangGraph StateGraph, 3 nodes, linear chain
  screening.py              # per-file orchestration + persistence boundary
  nodes/
    parse_resume_node.py    # Gemini → Resume schema
    parse_jd_node.py        # Gemini → jobDisc schema
    match_score_node.py     # Gemini → MatchResult schema, long scoring prompt
  utils/
    db.py                   # MongoDB, SQLite fallback
    document_loader.py      # PyMuPDF text extraction
```

12 Python files, ~250 LOC excluding tests. Single entry point: `app.py`.
Streamlit detected in `app.py` (19 distinct `st.*` constructs).

## 2. Runtime pipeline (observed)

`backend/graph.py` builds a `StateGraph` with three nodes and a strictly linear
chain — no conditional edges, no retries, no parallel branches:

```text
START → parseResume → parseJd → computeScore → END
```

`start(res, jd)` invokes it with a `ResumeState` TypedDict of five keys:
`resume_txt`, `jd_txt`, `parsed_resume`, `parsed_jd`, `match_score`.

`backend/screening.py:screen_files(files, jd, run, save)` iterates uploads, and
for each one: writes the PDF to a temp file, extracts text, runs the pipeline,
builds a three-key document, and persists it. Per-file failures are captured
into an `errors` list and do not abort the batch.

## 3. AI layer

All three nodes use `ChatGoogleGenerativeAI` with `temperature=0` and
`with_structured_output(<pydantic model>)` — no `method=` kwarg.

| Node | Model | Schema |
| --- | --- | --- |
| `extract_data` | `gemini-2.5-flash` | `Resume` (8 string fields) |
| `extract_jd` | `gemini-2.5-flash` | `jobDisc` (5 string fields) |
| `resume_match` | `gemini-2.5-pro` | `MatchResult` (`score: float`, `summary: str`) |

`match_score_node.py` embeds a ~60-line scoring rubric (skills 60% / experience
30% / education 10%), explicit "do NOT infer or guess" instructions, and a
required-output JSON block. **This prompt is product behavior and must be
preserved verbatim in the Android port.**

## 4. Persistence (observed)

`backend/utils/db.py` selects a backend once:

- `MONGODB_URI` set → MongoDB Atlas, db `resume_screener`, collection `candidates`
- unset → `_SqliteStore` writing `candidates.db`; documents held as JSON in a
  single TEXT column, so the schemaless shape survives without a relational schema

Document shape written and read back, identical on both backends:

```json
{
  "candidate_details": { "name": ..., "email": ..., "phone": ..., ... },
  "job_details":       { "job_title": ..., "skills_reqd": ..., ... },
  "match_analysis":    { "score": 8.0, "summary": "..." }
}
```

## 5. Configuration

Read from environment via `python-dotenv` (`load_dotenv()` in all three node
modules and in `db.py`):

- `GOOGLE_API_KEY` — required, used by all three nodes
- `MONGODB_URI` — optional, selects the Mongo backend

`.env` is gitignored. `.env.example` is committed as a template.

## 6. UI surface (observed, Streamlit → Android mapping)

| Streamlit construct | Observed use |
| --- | --- |
| `text_area` | Job description input, fixed 200px height |
| `file_uploader` | Multiple PDFs, PDF-only |
| `button` | "Run Screening", "Refresh Data" |
| `spinner` | Wraps the whole batch |
| `write` / `divider` | Per-result name, score, justification |
| `error` | Per-file failure messages |
| `header` / `subheader` / `metric` | Dashboard rows: candidate name, applied-for title, `score/10` |
| `expander` | Collapsed full analysis: summary, candidate JSON, job JSON |
| `json` | Raw candidate and job objects |
| `caption` | Active storage backend label |
| `set_page_config` | wide layout, page title |
| `markdown` | inline CSS: background image, toolbar/menu overrides |

No `st.session_state`, no forms, no multipage nav, no auth, no downloads.

## 7. Uncertainties (explicitly not assumed)

- The intended audience is unknown — personal tool vs. shared/recruiter tool.
  This materially changes the correct answer to the API-key question in
  `security.md`, so it is asked rather than inferred.
- `skills_reqd`, `experience_reqd`, `edu_reqd` are single comma-joined strings,
  not lists. Whether consumers need them as structured lists is unknown.
- `score` is typed `float` while the prompt demands an integer. Current behavior
  is float; preserved as-is unless the user asks otherwise.
- `phone` was changed `int` → `str` during the source-repo repair, so the
  deployed behavior is string-preserving. No data migration concerns: the
  Android app starts with an empty local database.
- The scoring rubric weights (60/30/10) are asserted only in prompt text. No
  code enforces or exposes them, so the Android port cannot display a computed
  sub-score breakdown without inventing new behavior.
- No end-to-end test exists against a live Gemini key in the source repo. The
  port's prompts are unvalidated against the real model.

## 8. Migration-relevant constraint

`langgraph`, `langchain`, `pydantic`, `pymongo`, and `PyMuPDF` are all Python.
None can run on Android. The AI pipeline must be reimplemented in Kotlin against
the Gemini REST API, and PDF text extraction replaced with an Android-native
library. This is a reimplementation of behavior, not a port of source.
