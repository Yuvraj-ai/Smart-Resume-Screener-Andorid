# Implementation Plan

Ordered. Each step is independently verifiable. Steps marked [gate] require a
decision to be resolved first; all gates are now cleared (see decisions.yaml).

Environment, confirmed present on this machine:
- JDK 17 at /tmp/opencode/jdk17 (no system java; JAVA_HOME must be set per build)
- Android SDK at ~/Android/Sdk, platform 36, build-tools 36.0.0
- AVD onemind_test, Pixel 6, android-36, x86_64
- adb available; no device attached, so the emulator must be booted for validation

---

## Step 1 — Project scaffold
Gradle wrapper + Kotlin DSL, AGP, Compose BOM, Material 3, Hilt, Room (KSP),
kotlinx.serialization, OkHttp, PDFBox-Android, security-crypto.
Set compileSdk/targetSdk 36, minSdk 26, JVM toolchain 17.
Add `local.properties` pointing at the SDK (gitignored).
Verify: `./gradlew assembleDebug` produces an APK.

## Step 2 — Domain models and serialization
`Resume`, `JobDescription`, `MatchResult` (with `breakdown`) as
`@Serializable`, plus the Gemini request/response envelopes.
Carry the scoring prompt over from `match_score_node.py`, editing only the
required-output block for `summary` + `breakdown`.
Verify: a unit test asserts the prompt's weights, bands, and no-infer clause are
present, so silent prompt drift fails the build.

## Step 3 — Settings and secure credential storage
`EncryptedSharedPreferences` via Keystore, wrapped in a `SettingsRepository`.
Settings screen: masked API key field, model pickers, security notice, test
connection. Fail closed when the key is absent.
`allowBackup=false`, `usesCleartextTraffic=false`, INTERNET permission.
Verify: unit test on the repository; no key value appears in any log output.

## Step 4 — Persistence
Room entity, DAO, converters, migrations policy (fallToDestructiveMigration is
acceptable pre-1.0; the raw JSON columns make future growth painless).
Repository with insert, list, search, delete, and aggregate queries.
Verify: in-memory Room DAO tests for CRUD, search, and the score histogram.

## Step 5 — PDF text extraction
PDFBox-Android wrapper with a size cap and a per-file error boundary.
Verify: a bundled fixture PDF yields expected text; an oversized and a corrupt
file each return an error rather than throwing.

## Step 6 — Gemini client
OkHttp client, `x-goog-api-key` header, per-model responseSchema, timeout,
2 retries with backoff, and an error taxonomy that distinguishes
invalid-key / rate-limit / network / malformed-response.
Verify: MockWebServer tests for success, 400, 429, 5xx, and empty-candidates.

## Step 7 — Screening pipeline
`ScreeningPipeline`: suspend fun, sequential, three calls, preserves source
order and temperature. Per-file isolation so one failure does not abort a batch.
Verify: fake-client unit test asserting call order, call count, and that a
mid-batch failure still yields the successful results.

## Step 8 — Dashboard
Stat tiles, score-band histogram, top matches, recent activity, empty state.
Verify: Compose UI test against seeded in-memory data.

## Step 9 — Screen flow
SAF multi-PDF picker with persisted URI permission, JD input, run button
gating, live per-file progress, partial-failure rendering.
Verify: UI test for gating and for the partial-success state.

## Step 10 — Candidates and detail
Searchable/filterable list; detail window with score header, weighted
breakdown bars, summary, parsed fields, raw JSON, and delete with confirmation.
Verify: UI test for navigation and delete confirmation.

## Step 11 — Navigation and polish
Bottom bar wiring, back handling, error states, loading states, accessibility
labels on icon-only controls.
Verify: full navigation test.

## Step 12 — Build, install, validate
Boot the AVD, install, walk every screen, capture screenshots, fix crashes.
Reconcile the prompt against the source field-by-field.
Verify: app launches clean; logcat free of errors; screenshots match the design.

## Step 13 — Parity audit
Fill in validation.yaml with the traceability matrix and a parity audit:
preserved, changed, removed, added, and unresolved limitations.

---

## Design dependency

Step 8 onward is UI work and must follow the `design-mobile-apps` skill. Run
that before Step 8 so the visual system is settled rather than retrofitted.

## Known risks

- **Gradle needs network** for the wrapper distribution and all dependencies.
  If the sandbox blocks it, Steps 1 and 12 cannot be verified and the build
  must be reported as unverified rather than assumed good.
- **No live Gemini key**, so steps 6, 7, and 12 are verified against mocks and
  the emulator, not a real model. The scoring prompt's real-world output quality
  is unvalidated.
- **PDFBox-Android on a Pixel 6 x86_64 emulator** should be fine, but text
  extraction fidelity is only confirmed by Step 5's fixture test.
