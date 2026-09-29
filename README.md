# Resume Screener (Android)

Native Android port of the [Smart-Resume-Screener](https://github.com/Yuvraj-ai/Smart-Resume-Screener)
Streamlit app. Upload resume PDFs with a job description, score each candidate
1-10 with a written justification and a per-criterion breakdown, and keep the
results on the device.

## Install

Grab `dist/ResumeScreener-0.1.0.apk` and sideload it. It is a signed release
build; allow installs from your file manager when prompted.

Minimum Android 8.0 (API 26). Requires network access for the Gemini API.

## First run

1. Open **Settings**
2. Paste your Gemini API key (get one at https://aistudio.google.com/apikey)
3. Tap **Test connection** to confirm the key works

The key is encrypted with the Android Keystore, is excluded from backup, and is
sent only to Google Gemini. It is never written to a URL.

## Using it

**Screen** — paste a job description (or a link to the posting and the app will
fetch it), attach one or more PDFs, tap Run. Each file moves through the
pipeline visibly, and a file that fails does not discard the ones that
succeeded.

**Dashboard** — totals, average and best score, a score distribution, top
matches and recent activity.

**Candidates** — everything screened, searchable by name or role, sortable by
date or score. Tap through for the full analysis and delete.

**Settings** — API key, model choice, storage details.

## Models

Parsing and scoring use separate models, matching the original pipeline:
`gemini-2.5-flash` to read the documents, `gemini-2.5-pro` to score them. Both
are selectable in Settings.

## Build from source

Needs JDK 17 and the Android SDK (platform 35).

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease      # signed APK
./gradlew testDebugUnitTest    # 26 unit tests
./gradlew installDebug         # onto a connected device
```

Release signing reads `release.jks` and credentials from `gradle.properties`,
both gitignored.

## What is stored, and where

Everything stays on the device in a local Room database. There is no sync, no
telemetry, and no upload path. Resumes are personal data, so nothing leaves the
phone except the text sent to Gemini for scoring.

## Design

Built against a design system generated with Google Stitch and Sleek, with the
palette, typography and score spectrum defined in `ui/theme/`. See
`design/HANDOFF.md` for provenance and the corrections applied to the generated
screens.

## Migration

`.migration/` holds the full migration specification: feature inventory,
architecture, screen map, data model, API contract, security audit, approved
decisions, and the final [parity audit](.migration/parity-audit.md).
