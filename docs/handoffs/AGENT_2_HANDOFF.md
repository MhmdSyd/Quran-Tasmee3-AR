# Agent 2 Handoff — Quran data layer & word mapping

## Verdict
- **Verified (exact):** 114 surahs, 6,236 ayahs, 604 pages, all 83,668 word ids tile contiguously; all 114 surah starts; every page ends on an ayah marker and starts on an ayah start; 29 independent juz-start anchors; displayed text == verbatim Hafs tokens.
- **NOT fully verified — 4 word-id adjustments (must be closed with an external word table):** every page's *word set* is exact, but on pages **262, 378, 441, 451** the exact word carrying a ±1 id adjustment cannot be proven from the two DBs, so **line breaks inside those 4 pages may be off by ≤1 word** (all other 600 pages: exact words *and* lines). Exposed at runtime via `getDataIssues().unverifiedPages` = [262,378,441,451].
- Nothing was fabricated: the 4 adjustments are in `tools/word_adjustments.json` with `"verified": false` and are *provisional candidates*.
- Could not compile Kotlin or fetch external references here (no Gradle/Google Maven, no web access from the sandbox). Python checks were run; **Kotlin + JVM tests are first compiled/run by GitHub Actions** — fix small compile issues first if it fails.

## Schemas
`qpc-hafs.db` `verses(id,verse_key,surah,ayah,text)` — no PK/index; id == rowid == 1..6236 (verified). Text: tokens separated by U+0020/U+00A0 only; ends `NBSP + Arabic-Indic ayah number`; 199 standalone `۞` (U+06DE) tokens (not words); U+06E9 sajdah sign occurs only inside tokens.
`qpc-v1-15-lines.db` `pages(page_number,line_number,line_type,is_centered,first_word_id,last_word_id,surah_number)`; `info(name,number_of_pages,lines_per_page,font_name)`. Empty ranges are stored as `''` (text), not NULL. **Pages 1 and 2 have 8 lines, not 15** (9,046 rows total). Types: surah_name 114, basmallah 112 (none for 1 and 9), ayah 8,820.

## What the word ids mean
Global 1..83,668 in reading order; **every ayah-number marker is a word** (the last word of each verse), `۞` is not. Naive tokenisation gives 83,666 → line DB has +2 net. Offsets vs naive: 0 until 15, +1 from 16, +2 from 28, +3 from 37, +2 from 38 → one extra id (split) inside each of surah 15, 27, 36 and one fewer (merge) in 37. Surah-start ids pin the surah of each change exactly.

## How far the 4 adjustments could be localised (evidence)
Under the plain offsets, 585/604 page-last ids are ayah markers; the 19 exceptions are exactly pages 262-266, 378-384, 441-445, 451-452. A page ends on a marker only once the adjustment has been applied, so each adjustment lies **inside the first failing page** (262, 378, 441, 451). Page-start-of-juz anchors after each page (e.g. 36:28 at p.442, 27:56 at p.382, 39:32 at p.462, 15:1 at p.262) confirm the offsets afterwards. Within a page, line-width fitting is not decisive and centered-line flags (only 7) don't help.
Provisional candidates (chosen from words inside the failing page): 15:7 `لَّوۡمَا` (split) · 27:20 `مَالِيَ` (split) · 36:22 `وَمَالِيَ` (split) · 37:130 `إِلۡ`+`يَاسِينَ` (merge). **To close:** supply any per-word table of the 1405 V1 layout (QUL/Quran.com word-by-word `words` table, ids 1..83668, with verse_key+position); then set `tokenIndex`/`verified` in `tools/word_adjustments.json`, run `python3 tools/build_quran_index.py && python3 tools/validate_quran_data.py`. No Kotlin change needed.

## Mapping logic (`tools/quran_data.py`, mirrored in `data/WordMapper.kt`)
Tokenise verse text (split on U+0020/U+00A0, drop `۞`), assign consecutive ids from verse `first_word_id`. SPLIT: token takes two ids (`QuranWord.idEnd = id+1`, text unchanged; word belongs to the line containing `id`). MERGE: two tokens → one word, text = both verbatim tokens joined by one space. `indexInAyah` counts display words; marker is last. No normalisation anywhere.

## Files
- `app/src/main/assets/quran-index.db` (≈0.6 MB, **generated** by `tools/build_quran_index.py`): `verse_index(verse_id,surah,ayah,hafs_rowid,first_word_id,word_count)`, typed `lines` (NULL instead of `''`, index on `first_word_id`), `surahs(surah,name_ar,ayah_count,start_page,first/last_word_id)`, `word_adjustments`, `meta` (source SHA-256s). Source DBs are untouched; verse text is read from Hafs by `rowid` (O(log n)). `qpc-v1-15-lines.db` is only a build/test source now (not opened at runtime; may be moved out of assets to save 240 KB).
- Surah Arabic names: neither DB has them → static list in `tools/quran_data.py` (metadata, not Quran text); ayah counts cross-checked against Hafs.
- `data/QuranDatabases.kt` copies Hafs+index to `filesDir/quran/` (re-copied on app update), opens read-only. `data/SqliteQuranRepository.kt` (wired in `AppContainer`), `data/WordMapper.kt` (pure Kotlin).

## Interface (`QuranRepository`, all `suspend`, main-safe)
`getPage(n)` (lines → words) · `getPageForAyah` · `getSurahStartPage` · `getSurahName` (unchanged) + new: `getSurahs()`, `getAyahText(s,a)` (verbatim incl. marker), `getAyahWords(s,a)`, `getWord(id)`, `getPageForWord(id)`, `getDataIssues()`. Models: `QuranWord.idEnd`, `SurahInfo`, `DataIssues`, `QuranConstants.linesOnPage()`.
Notes for others: Agent 5 — pages 1-2 have 8 lines; render only lines returned. Agent 4 — use `QuranPage.recitableWords` (markers excluded); match against canonical `QuranWord.text`; merged word text contains a space.

## Tests / how to run
`python3 tools/validate_quran_data.py` (24 checks, all pass; exit 1 on failure; prints unresolved items) · JVM tests `app/src/test/.../data/QuranDataIntegrityTest.kt`, `WordMapperTest.kt` via `./gradlew testDebugUnitTest` (sqlite-jdbc reads the asset DBs; **not yet executed**). CI runs both before `assembleDebug`.
Recall corrections while building anchors: juz 4/7/11 start at 3:92, 5:83, 9:94; juz 26 (46:1) starts mid-page 502 — excluded from the page-start test.
