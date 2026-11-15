# Agent 1 Handoff

## Status
- Project scaffolded: single Gradle module `:app`, Kotlin 2.0.20, AGP 8.5.2, Gradle 8.9, Compose BOM 2024.09.00, minSdk 26 / target+compile 34, JDK 17.
- **Not compiled in Agent 1's sandbox** (no access to Gradle/Google Maven). First verification is the GitHub Actions run. If it fails, fix that first; expect small issues only.
- Local git repo created; not pushed (no GitHub credentials available). Push to `main` and Actions builds the APK.

## Build
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## Design reference (docs/design/…html), 8 boards
1 Reader (Madani mushaf) · 2 Recitation: listening · 3 Words revealed + a mistake · 4 Session summary ·
5 Recitation settings · 8 Surah opening page · 6 On-device pipeline (diagram) · 7 App icon concepts.
Palette → `ui/theme/Theme.kt` (Ink 1F2A24, Paper FFFDF6, Gold D9C48A/B8923A, Green 1F6B4F/17513C, Mistake A33A2B/F6D9D3). UI font Cairo is **not bundled** (default sans used); Quran font = `HafsFont` (res/font/uthmanic_hafs.ttf). RTL is forced in `QuranTheme`.

## Architecture
Manual DI in `di/AppContainer.kt` — the only place to swap stubs for real implementations. No ViewModels yet (screens use `produceState`/`collectAsState`); add if needed.

| Package | Interface | Stub now | Owner |
|---|---|---|---|
| `data` | `QuranRepository` (getPage, getPageForAyah, getSurahStartPage, getSurahName) | `StubQuranRepository` (empty pages) | Agent 2 |
| `speech` | `SpeechEngine` (`events: SharedFlow<SpeechEvent>`, start/stop/release) | `NoOpSpeechEngine` | Agent 3 |
| `recitation` | `RecitationMatcher` (`progress: StateFlow<RecitationProgress>`, begin/onSpeech/reset) | `NoOpRecitationMatcher` | Agent 4 |
| `ui/screens` | Reader, Recitation, Summary, Settings shells | working placeholders | Agent 5 |
| `settings`, `session` | `SettingsRepository`, `SessionStore` | Prefs-backed / in-memory | Agent 6 |

Data flow in recitation: `SpeechEngine.events` → `RecitationMatcher.onSpeech` → `progress` → UI reveals words. Wiring lives in `RecitationScreen` (Agent 5); mic permission request (RECORD_AUDIO, declared in manifest) is also Agent 5's.
Models: `domain/Models.kt` (`QuranWord`, `PageLine`, `QuranPage`). Navigation: `ui/AppNav.kt` (reader/{page} → recite/{page} → summary; settings).

## Data facts for Agent 2 (verified)
- `qpc-hafs.db`: `verses(id, verse_key, surah, ayah, text)`, 6236 rows. `text` ends with `\u00A0` + Arabic-Indic ayah number. Verse text also contains standalone `۞` tokens (199) that are not words.
- `qpc-v1-15-lines.db`: `pages(page_number, line_number, line_type, is_centered, first_word_id, last_word_id, surah_number)`; line_type ∈ `surah_name` (114), `basmallah` (112), `ayah` (8820); `info`: 604 pages, 15 lines. Word ids are global, 1..83668; there is **no word table**, so words must be derived from verse text.
- **Word-id mismatch:** naive tokenisation (whitespace split, drop `۞`, keep ayah-number marker as a word) gives 83666 tokens vs 83668 ids. Offset vs. line DB: +1 from surah 16, +2 from 28, +3 at 37, back to +2 at 38 → the line DB splits one word in each of surahs 15, 27, 36 and merges one in surah 37 relative to the Hafs text. `python3 tools/check_word_ids.py` reproduces this. Agent 2 must locate these four spots and build a correct word→id table (e.g. by checking line breaks/page boundaries), otherwise lines after surah 15 are misaligned.
- Font in `res/font` is a Unicode Hafs font (not the per-page V1 glyph fonts), so render words from `verses.text`.
- `surah_header_bg.png` (2147×227) is in `res/drawable-nodpi`.

## Remaining tasks
1. Agent 2: real `QuranRepository` (copy assets DBs to app storage or open read-only), fix word ids, surah names.
2. Agent 3: offline Arabic ASR `SpeechEngine` (no INTERNET permission — keep it that way).
3. Agent 4: `RecitationMatcher` with Arabic normalisation and strictness from `RecitationSettings`.
4. Agent 5: real mushaf rendering, word-hiding, listening/mistake UI, mic permission, summary UI per design.
5. Agent 6: persist sessions, polish settings, release signing config via CI secrets (optional), Cairo font if wanted.
