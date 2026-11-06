# UniShuttle Android — Project Rules

## Git & Source Control Safety

- NEVER run `git add .` blindly. Always stage files explicitly by path.
- NEVER commit or push without explicit user approval. Present a full staged file list first.
- NEVER commit, reset, or discard uncommitted changes without explicit instruction.
- Before proposing any commit, run: `testDebugUnitTest`, `lintDebug`, `assembleDebug`.
  All three must pass before staging begins.

## Sensitive File Exclusions

The following files must NEVER be staged or committed:
- `app/google-services.json` — Firebase project config
- `local.properties` — SDK paths and secrets
- `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key` — Signing credentials
- `*.db`, `*.db-wal`, `*.db-shm` — Device SQLite dumps from ADB
- `*.env` — Environment variable files
- Root-level `*.png` files — ADB device screenshots
- `*.code-workspace` — Local editor settings
- Any file containing Firebase service account JSON (`"type": "service_account"`)

## FCM Token Redaction

- Device FCM registration tokens obtained via Logcat (e.g., `onNewToken`) are DEVICE-SPECIFIC secrets.
- If a real token is captured and written into any documentation or report, redact it immediately
  to `[REDACTED_DEVICE_FCM_TOKEN]` before the document is committed.
- Always run `git grep` for the token string to confirm it is absent from tracked and untracked files.

## Secrets Scan Before Commit

Before proposing a commit, verify no secrets exist using:
- `git grep "AIzaSy"` — Google API keys
- `git grep -i "PRIVATE KEY"` — Private keys
- `git grep -i "service_account"` — Service account JSON
- `git grep -i "<known-token-fragment>"` — Any known device-specific token

## Production Safety

- NEVER inject fake telemetry or artificial events into production Firebase RTDB.
- NEVER enable Firestore "Test Mode" security rules — always use authenticated rules.
- NEVER modify production Firestore security rules, create databases, or seed production
  data without explicit product owner approval.
- NEVER deploy Cloud Functions or push to `main` without explicit instruction.

## Firebase Provisioning Rules

- NEVER recommend "Test Mode" Firestore security rules. Always use authenticated rules.
- NEVER create Firestore databases, enable Cloud APIs, change security rules, or seed
  Firestore collections without explicit product owner approval.
- When advising on Firestore database creation, recommend `asia-south1` (Mumbai) for
  Indian mobile client latency. Note that Cloud Functions run in `us-central1`, so
  co-location vs. client latency is a trade-off to surface to the product owner.
- Firestore `stops` collection document IDs must include the `stop_` prefix
  (e.g. `stop_main_gate`) because the Cloud Function builds FCM topics as
  `stop_${stopDoc.id}_${minutes}min` and the Android client also prepends `stop_`,
  resulting in topics like `stop_stop_main_gate_2min`. Mismatch will silently
  break server-push notifications.
- Do not deploy Cloud Functions or change `functions/src/index.ts` without explicit
  instruction.

