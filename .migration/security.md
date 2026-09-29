# Security Audit and Trust Boundaries

Covers the source app and the proposed Android port. The user explicitly asked
for Gemini API key and Mongo URI entry in an Android settings screen, so that
request is analyzed here rather than silently accepted or silently refused.

---

## 1. Findings in the source repository

Status as of source-repo commit `47d1c92`. Items 1 and 2 were found and fixed
during repair; recorded here so the new app does not reintroduce them.

| # | Finding | Status |
| --- | --- | --- |
| 1 | `backend/nodes/.env` was committed with a live `GOOGLE_API_KEY`, despite `.gitignore` listing that path. Gitignore never untracks. | Fixed: untracked, `.env.example` added. Key reported expired by owner. |
| 2 | `backend/utils/db.py` hardcoded a MongoDB Atlas URI including username and password, in tracked source, twice. | Fixed: now read from `MONGODB_URI`. |
| 3 | Committed `__pycache__/*.pyc` build artifacts. | Fixed: untracked. |
| 4 | Uploaded files were written to the working directory under the client-supplied filename — a path-traversal vector (`../../x.pdf`). | Fixed: fixed filename inside a private temp dir. |
| 5 | `exit()` inside the db layer terminated the whole Streamlit process on a transient DB error. | Fixed: errors surface in the UI. |

`detect_secrets.py` reports one hit on `.env.example` line 8. **False positive** —
that line is the `mongodb+srv://<user>:<password>@<cluster>` placeholder template.
No live credential remains in the source tree.

## 2. The central trust-boundary question

The source app is server-side Python: the API key and DB credential live in a
server's environment, never reach the client, and are never distributed.

An Android APK is **not a trusted client**. Moving the same key into the app
changes its security properties in ways that matter:

1. **The key ships with the app.** If it is ever hardcoded, built into a
   release APK, or committed, it is public to anyone who unzips the APK. A
   user-entered key entered at runtime is meaningfully safer than a baked-in
   one, because it is never in version control — but it is still only as
   protected as the device.
2. **Device-local storage is recoverable.** App-private storage is readable on a
   rooted device, and via ADB backup unless backups are excluded. Keystore-backed
   encryption mitigates but does not eliminate this.
3. **A leaked Gemini key is directly monetizable** against the owner's quota, and
   unlike a DB password it is a single high-value string with no second factor.
4. **MongoDB credentials in a client app are worse than a Gemini key.** The app
   would need direct database connectivity, which means shipping a usable
   database credential to every device. Anyone extracting it gets direct data
   access, bypassing whatever network restrictions protect the cluster.

### What is being recommended

- **Gemini key in settings, on-device** — acceptable, and it is what the user
  asked for. Mitigation: Keystore-backed storage, exclude from backup, never
  log it, and a visible warning in Settings that the key is stored on-device.
  This is the right call for a personal or small-team tool.
- **MongoDB in-app** — the higher risk of the two, for the reason in point 4
  above. It also has a hard technical blocker, see section 3.
- **Local Room storage as the default** — the strongest recommendation, and it
  matches the source app's own behavior, which already falls back to SQLite
  whenever `MONGODB_URI` is absent. An Android app can be fully functional with
  no database credential at all.

## 3. Technical blocker: Android cannot speak the MongoDB wire protocol

`pymongo` talks the MongoDB wire protocol. There is no Android client library
for it, and the protocol is not exposed over plain HTTPS. An Android app cannot
do what `db.py` does when `MONGODB_URI` is set.

Options, none of which are free:

| Option | Cost | Notes |
| --- | --- | --- |
| MongoDB Atlas Data API | Free tier exists | Official HTTPS REST for Atlas. Requires a Data API deployment and per-cluster config. Closest to current behavior. |
| Small proxy service | Real hosting cost | The Python app, deployed, exposing a narrow REST API. Also restores server-side secret handling. |
| Local-only, drop Mongo | Zero | Matches the source's existing SQLite fallback. Loses multi-device sync. |
| Realm / Firebase | Migration cost | Replaces the database rather than keeping it. Out of scope unless asked. |

This is the single largest scope risk in the migration and is the primary
question for the user in Phase 2.

## 4. Proposed Android mitigations (to be applied)

- Store `GOOGLE_API_KEY` and any DB credential via `EncryptedSharedPreferences`
  backed by Android Keystore AES256-GCM.
- Set `android:allowBackup="false"` and exclude the credential store from any
  backup path, so keys do not travel to cloud backups.
- Never log keys. Redact in any error surfaced to the UI.
- `android:usesCleartextTraffic="false"`; all traffic is HTTPS.
- Release builds must never contain a default or placeholder key. Fail closed
  with a clear message pointing at Settings.
- No `INTERNET` permission is needed for local-only mode; request it only if a
  remote backend is actually configured.
- PDF parsing handles untrusted files: enforce a size cap, and treat parser
  failures as per-file errors rather than crashing the batch.
- Resumes are PII. The source app already isolates this concern; the Android
  app should not log resume text to logcat.

## 5. Open question for the user

The intended audience is not known, and it changes the correct answer:

- **Personal / small-team tool** — on-device keys are fine. Ship it.
- **Distributed to many users** — keys must not live on-device; a backend is
  required, and the user should authenticate instead.

Asking rather than inferring, per the skill's decision rules.
