# Quran Recitation (Android, offline)

Offline Madani Mushaf reader (Hafs, 1405H print layout: **604 pages × 15 lines**, pages 1–2 have 8) with a
recitation mode: the page text is hidden and each word is revealed as the on-device speech recogniser hears it.
Kotlin + Jetpack Compose. **No `INTERNET` permission, no cloud services, no analytics.** Audio never leaves the device.

> **Status:** see [`FINAL_QA_REPORT.md`](FINAL_QA_REPORT.md) for exactly what has and has not been verified.
> The speech recogniser gives *approximate* recognition. The app does **not** judge Tajweed; words listed
> "للمراجعة" (for review) are words whose recognition was uncertain or skipped, not confirmed mistakes.

## Get the APK (GitHub Actions)
1. Push to `main`/`master` (or open **Actions → Android CI → Run workflow**).
2. When the **build** job finishes, open the run and download the artifact **`Quran-Tasmee3-debug`**
   (`Quran-Tasmee3-debug.apk`, roughly 100 MB because it bundles the 36 MB model and native libraries for 3 ABIs).
3. Install it (`adb install Quran-Tasmee3-debug.apk` or copy it to the phone, allowing "unknown sources").

Workflow jobs: `data-checks` (Python, no network) · `build` (fetch assets → debug APK → APK checks → upload APK →
JVM tests → upload test reports) · `asr-smoke` (real model + real audio on the desktop runtime; needs internet, does not
block the APK) · `release` (optional, see below).

### Optional signed release APK
Add these repository secrets (Settings → Secrets and variables → Actions): `RELEASE_KEYSTORE_BASE64`
(`base64 -w0 your.jks`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Then push a tag
`v*` or run the workflow manually with *release = true*; the artifact is `Quran-Tasmee3-release`.
Without the secrets the release job is skipped. Keystores are git-ignored; nothing secret is stored in the repo.

## Build locally
Requires JDK 17 and the Android SDK (platform 34).
```
bash tools/fetch_asr_assets.sh      # once: ASR model (36 MB) + sherpa-onnx native libs; pinned + SHA-256 verified
./gradlew testDebugUnitTest         # JVM tests
./gradlew assembleDebug             # app/build/outputs/apk/debug/Quran-Tasmee3-debug.apk
```
The fetch step is build-time only. Without it the app still builds but the speech engine reports `ModelMissing`.
Python checks (no dependencies beyond the standard library): `python3 tools/validate_quran_data.py`,
`tools/check_mushaf_structure.py`, `tools/check_model_alphabet.py`, `tools/export_test_pages.py --check`.

## Architecture
```
AudioRecord 16 kHz ─▶ UtteranceSegmenter ─▶ ChunkedRecognitionPipeline ─▶ sherpa-onnx (quran-asr-small, INT8)
        speech/asr/                                                      │ SpeechEvent.Partial / Final
                                                                         ▼
 qpc-hafs.db + quran-index.db ─▶ SqliteQuranRepository ─▶ QuranPage ─▶ TrackingRecitationMatcher (recitation/)
        data/                                         │                  │ RecitationProgress
                                                      ▼                  ▼
                                              MushafPage (ui/mushaf) ◀── toWordVisuals ── RecitationScreen ─▶ SessionSummary
```
| Package | Role |
|---|---|
| `data` | Canonical vocalized text from `qpc-hafs.db`; 15-line layout + word ids from `qpc-v1-15-lines.db` via the generated `quran-index.db` (`tools/build_quran_index.py`). |
| `ui/mushaf` | Page renderer: KFGQPC Hafs font, `surah_header_bg.png` headers, justified lines, per-word visual states. |
| `speech/asr` | Offline ASR engine (chunked, not true streaming), capture, VAD segmentation, model installer. |
| `recitation` | Arabic folding, word alignment, state machine. Never generates text; only reports word ids/states. |
| `session`, `settings`, `di`, `ui` | Summary model, preferences, manual DI (`AppContainer`), screens/navigation. |

## Offline behaviour
No `INTERNET` permission (CI fails the build if it ever appears); the model and native libraries are packaged in the
APK and copied/verified locally on first run; there is no download code. Not yet confirmed on a device in airplane
mode (see QA report).

## Licences and attribution
See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). Redistribution rights for the font and the Quran data were
**not** independently verified; read the notes there before publishing the app.

## Docs
`docs/design/` (UI boards) · `docs/handoffs/` (per-agent technical notes: data layer, mushaf, ASR, matching) ·
`docs/mushaf-previews/` (Python-rendered layout previews, not Android screenshots).
