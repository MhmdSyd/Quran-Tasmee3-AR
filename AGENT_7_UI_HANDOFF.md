# AGENT_1_UI_HANDOFF — Quran navigation UI

## Changed files
- `ui/screens/ReaderScreen.kt` — top bar: nav button (menu icon + current surah name) opens the navigation sheet; settings text → gear icon (`IconButton`, same `onOpenSettings`); bottom bar: `<` (previous) and `>` (next) 48dp buttons side by side on the physical left (forced LTR inner row), "ابدأ التسميع" keeps the remaining width. Paging logic (`goTo`) unchanged.
- `ui/screens/QuranNavigationSheet.kt` (new) — bottom sheet with 3 tabs: **السور** (scrollable list of all 114 surahs, from `MushafDataSource.surahs()` → `surahs.start_page`), **الصفحة** (number field 1–604, Arabic or western digits, invalid input shows error and disables "اذهب"), **الجزء** (grid 1–30).
- `domain/JuzStarts.kt` (new) — the 30 canonical first ayahs `(surah, ayah)` only. The page is resolved at runtime with the existing `QuranRepository.getPageForAyah`; no page numbers are hardcoded.
- `ui/components/Icons.kt` — added `GearIcon`, `MenuIcon` (Canvas, no new dependency).
- `test/.../ui/screens/QuranNavigationTest.kt` (new) — input parsing/rejection, Juz table sanity.

## Integration
`QuranNavigationSheet(surahs, currentPage, pageOfJuz, onGoToPage, onDismiss)`; ReaderScreen supplies `onGoToPage = goTo`, so the pager and the "صفحة N" header update. Juz → page verified offline against `quran-index.db`: 1, 22, 42, 62 … 582 (strictly increasing, juz 30 = p.582).

## Not touched
ASR/recognition, canonical text, mushaf layout, other screens, Gradle dependencies.

## Not verified
No Android SDK in the authoring sandbox: the project was **not compiled and the app/unit tests were not run**. Run `./gradlew testDebugUnitTest assembleDebug` before merging.
