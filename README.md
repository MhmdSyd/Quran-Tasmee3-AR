# Quran Recitation (Android, offline)

Offline Madani Mushaf (Hafs, 1405H, 604 pages × 15 lines) reader with recitation mode: text is hidden and words
are revealed as the user recites. Kotlin + Jetpack Compose. No network permission, no cloud services.

## Build
```
bash tools/fetch_asr_assets.sh # once: ASR model (36 MB) + sherpa-onnx native libs; pinned and SHA-256 verified
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
```
The fetch step is build-time only (needs internet once; CI does it). The app never downloads anything and has no
INTERNET permission. Without the fetched files the app still builds; the speech engine then reports `ModelMissing`.
Requires JDK 17 and the Android SDK (platform 34). GitHub Actions (`.github/workflows/android.yml`) builds the
debug APK on every push and uploads it as the `quran-recitation-debug-apk` artifact. No secrets needed.

## Layout
`app/src/main/java/com/quranrecite/app/` — `domain` (models), `data` (Quran repo), `speech` (offline ASR),
`recitation` (matcher), `settings`, `session`, `di` (AppContainer), `ui` (theme, nav, screens).
Assets: `qpc-hafs.db`, `qpc-v1-15-lines.db`, Uthmanic Hafs font (`res/font`), surah header image.
UI reference: `docs/design/Quran_Recitation_App_Design.html`. Hand-off notes: `AGENT_1_HANDOFF.md`.

Speech recognition (Agent 4): `speech/asr/`, notes in `AGENT_4_HANDOFF.md`. On-device check: Settings -> ASR diagnostics.
