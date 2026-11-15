# Final QA report (Agent 6)

## Headline
**I could not build or run the Android app in my environment** (no access to Gradle, Google Maven, Maven Central or
Hugging Face; no device). Therefore:
- **No APK was produced by me and no GitHub Actions run was observed by me.** The repository is configured to build
  and upload the APK, but "builds on Actions" is **unverified until you push and the workflow runs**.
- Nothing below about device behaviour (startup time, memory, CPU, latency, airplane mode, rendering, microphone) was measured.

## What was actually verified (and how)
| Check | Result |
|---|---|
| Shipped assets vs supplied files (`qpc-hafs.db`, `qpc-v1-15-lines.db`, font TTF, `surah_header_bg.png`) | byte-identical (`cmp`) |
| `tools/validate_quran_data.py` (114 surahs, 6,236 ayahs, 604 pages, 83,668 word ids, juz anchors) | all hard checks pass |
| `tools/check_mushaf_structure.py` (8/15 lines per page, 114 headers, 112 basmallah, ordering) | all PASS |
| `tools/check_model_alphabet.py`, `tools/export_test_pages.py --check` | PASS |
| Pure-JVM sources (data mapper, matcher, ASR pipeline, session, layout math) compiled with Kotlin 2.0.21 | 0 errors |
| JVM unit tests (real test sources, run with a small JUnit-4 shim because JUnit is unavailable offline) | **102 / 102 pass** (WordMapper, ArabicMatch, RecitationTracker 43, Noise, SessionSummary, CaptureLoop, Pipeline, Metrics, Resources, Segmenter, WordParser/Alphabet, MushafFit) |
| Android/Compose files (25) | **read and cross-checked symbol-by-symbol; not compiled.** Edited files were syntax-checked only. |

**Not run here:** `QuranDataIntegrityTest` and `MushafStructureTest` (need `sqlite-jdbc`; their checks are mirrored by the Python scripts above and they run in CI), all Compose/instrumented tests (none exist), the ASR smoke test (needs the model from Hugging Face).

## Integration defects found and fixed
1. **Recitation progress was lost** when the user opened Settings mid-recitation (screen disposed → `begin()` reran) and on rotation. Now an `AppContainer`-held `RecitationSession` resumes the same page/timer; Back/finish ends it; the activity handles configuration changes so rotation no longer cuts the microphone.
2. **Dead setting:** the font-size slider stored a value nothing read → removed from Settings.
3. **Missing attribution:** the model's CC-BY training-data attribution had no place in the app → added to Settings, plus `THIRD_PARTY_NOTICES.md`.
4. **CI fragility:** the APK build was gated behind a network smoke test (everyayah.com) → split into independent jobs; APK is uploaded before tests; test reports uploaded; added APK content checks (INTERNET permission absent, model, DBs, font, header PNG, 3 native libs present); optional signed-release job + env-based signing config (skipped without secrets).
5. Repo hygiene: removed committed `__pycache__`, moved hand-offs to `docs/handoffs/`, README rewritten.

Not changed (deliberately): matching thresholds, ASR chunking, mushaf layout — no concrete defect found, and they cannot be tuned without real audio/devices.

## Acceptance criteria
| # | Criterion | Status |
|---|---|---|
| 1 | Repo builds on GitHub Actions | **Unverified** (workflow ready; earlier agents' CI fix-commits suggest CI was run for Agents 1–5, result for HEAD unknown) |
| 2 | Workflow produces downloadable APK artifact | **Configured, unverified** (`quran-recitation-debug-apk`) |
| 3 | 604-page, 15-line layout | Data/structure **verified**; on-device rendering unverified (Python port previews only) |
| 4 | Supplied KFGQPC Hafs font used | Wired in `HafsFont`/`MushafStyle`; file verified identical; rendering unverified on device |
| 5 | Surah headers use supplied PNG | Wired; file verified identical; unverified on device |
| 6 | Fully vocalized text from canonical DB | **Verified** (text read verbatim from `qpc-hafs.db`; no generated text anywhere) |
| 7 | Hidden text, progressive reveal | Tracker logic JVM-tested on 10 real pages; UI unverified |
| 8 | ASR + matching without internet | Structural: no `INTERNET` permission (CI-enforced), model bundled, no download code. **Airplane-mode run not done** |
| 9 | Uncertainty handled; no false Tajweed claims | By design: no "mistake" state; neutral "للمراجعة" wording and disclaimer in the summary and Settings |
| 10 | README explains build/test | Done |

## Known limitations (not resolvable here)
- **Unverified line breaks on 4 pages** (262, 378, 441, 451): a ±1-word line-break uncertainty from the word-id table (Reader shows a note). Word order and matching are unaffected. Needs an external per-word table to close.
- **Recognition quality is unmeasured.** Author-reported WER 4.5% (studio) to ~10% (phone recordings); matcher accuracy figures (93% revealed, Normal) come from a *simulated* recogniser. Look-alike words one letter apart can be accepted as correct; dropped words can look "skipped".
- Chunked decoding, not true streaming; reveal latency ≈ partial interval + endpointing (est. 0.6–1.8 s, **not measured**). Energy-based VAD degrades in noisy rooms. Thresholds are untuned on real audio.
- Matching is page-bounded (no auto-advance to next page). Session history is in-memory only (lost on process exit).
- APK ≈ 100 MB (3 ABIs + model); consider dropping `armeabi-v7a`/`x86_64` via `abiFilters` if size matters.
- Hard-coded Arabic UI strings; Cairo font not bundled; no accessibility pass beyond content descriptions.
- Licences: font EULA allows free use/copy/distribute unmodified, no sale (see `THIRD_PARTY_NOTICES.md`); redistribution rights of the Quran DBs and PNG are **unverified**.

## First things to do after pushing
1. Watch Actions → fix any compile error first (Compose code has never been compiled by me; expect small import/API fixes at most).
2. On a phone: Settings → ASR diagnostics (airplane mode on): load time, RTF, memory. Then recite pages 1, 2, 55, 604; check font scale 1.3 and landscape.
3. Log `Final.text` vs expected words and tune thresholds in `recitation/ArabicMatch.kt`.
