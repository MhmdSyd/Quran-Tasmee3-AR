# Agent 3 Handoff — Madani mushaf reader, Hafs font, surah decoration

## Status / honesty
- **Kotlin was NOT compiled or run here** (no Gradle/Maven access; same limitation as Agents 1–2). CI's `testDebugUnitTest`/`assembleDebug` is the first compile. Expect small API/import fixes at most.
- **Not tested on real devices or multiple densities** (none available). Layout math is density-independent (all sizes derive from the measured page width/height in px; `fontScale` is compensated).
- What *was* verified here, with real data and the exact font: `python3 tools/check_mushaf_structure.py` (page structure, all PASS), `python3 tools/validate_quran_data.py` (Agent 2's checks), and `python3 tools/render_preview.py` — a Python port of the Kotlin layout (same constants and `placeRtl`) drawn with the shipped TTF and PNG. Output in `docs/mushaf-previews/` (pages 1, 2, 3, 50, 76, 77, 187, 262, 586, 601, 604 + a recitation-states page). It previews the algorithm; it is not the Android renderer.

## Done
- **Font**: supplied `UthmanicHafs1Ver18.ttf` = `res/font/uthmanic_hafs.ttf` (byte-identical, verified) via `HafsFont`. Covers every character of all 6,236 verses (checked). All Quran text uses `MushafStyle.textStyle()`: no synthetic bold/italic, `ar` locale, RTL. Verse numbers are drawn *by the font* as ornamented circles from the plain digit token (verified for `٦`, `١٠`, `٢٨٦`), so no marker glyph is added. DB text already uses U+06E0, so the design's U+06DF remap is not needed.
- **15-line reader** (`ReaderScreen`): 604-page `HorizontalPager` (RTL: swipe left = next), prev/next buttons, surah picker sheet (114 surahs → `startPage`), current surah/page label, Start-recitation passes the visible page. Lines come straight from `QuranRepository.getPage` — no reflow; pages 1–2 render 8 lines.
- **Lines**: `SurahHeader` → `surah_header_bg.png` (`ContentScale.Fit`, alpha kept, aspect 2147×227 — the real size, not 2048×216) with "سورة <name>" centered in the cartouche; `Basmallah` → centered, text taken from verse 1:1 in `qpc-hafs.db`; `Ayahs` → justified, or centered when `is_centered`.
- **Known limit**: a Unicode font cannot stretch glyphs like the print, so lines are justified by word spacing. Page font = f(widest line), clamped to 0.84–1.15× of a reference; over all 604 pages median gap 0.38 em, 1% of lines > 1 em. Constants in `MushafFit`; re-derive with `tools/measure_lines.py`.
- **Agent 2 caveat surfaced**: pages 262, 378, 441, 451 show a one-line note (line breaks may be off by one word).

## Components (`ui/mushaf/`)
| Item | Use |
|---|---|
| `MushafPage(page, basmallah, surahName, modifier, mode, wordStates, style, onWordClick)` | Pure renderer. Fills its bounds; each line gets an equal height share. |
| `LoadedMushafPage` / `MushafPageFrame(pageNumber, data, mode, wordStates)` | Loads + draws; Frame also centers and caps width to the printed aspect (0.66). |
| `MushafDataSource(repo)` (`container.mushafData`) | LRU page cache, basmallah words, surahs, unverified pages. Over Agent 2's repo only. |
| `MushafMode { Reading, Recitation }` | Default state of words with no explicit entry: Normal / Hidden. |
| `WordVisual { Normal, Hidden, Revealed, Current, Correct, Uncertain, Mistake }` | Per-word state, keyed by `QuranWord.id` in `wordStates`. |
| `RecitationProgress.toWordVisuals(highlightWordId?)` | Adapter from Agent 4's model (`Hidden/Revealed/Mistake`). |
| `MushafStyle` | Colors/font (design values); `headerTint` optional. |
| `MushafFit` | Pure layout math (unit-tested). |

State styling (design boards 2–3): Hidden = transparent text + thin `#8F8668` underline; Current = `#17513C` on `#D7EADD` chip; Correct = green text; Uncertain = muted text, dashed gold underline on gold tint; Mistake = still hidden, red underline on red tint. **Every state keeps the word's exact width**, so state changes never move words. Verse-number markers are always visible. Hidden words are announced as "كلمة مخفية" to accessibility.

## Changes to others' files
`AppContainer` (+`mushafData`), `AppNav` (Reader no longer navigates per swipe; `onStartRecitation(page)`), `RecitationScreen` (placeholder bars → `MushafPageFrame` in Recitation mode fed by `progress.toWordVisuals()`; lifecycle/finish code untouched).

## For Agent 5
Render only: pass `wordStates = progress.toWordVisuals(lastRecognizedId)`. Matching text: use `QuranPage.recitableWords` (Agent 2). Word `onWordClick` is available for tap-to-start. Basmallah is not part of `recitableWords`.

## Tests
`MushafFitTest`, `MushafStructureTest` (JVM, run by `./gradlew testDebugUnitTest`); Python mirror `tools/check_mushaf_structure.py`. No Compose UI/instrumented tests yet — first things to do on a device: pages 1, 2, 76/77, 187, 586, 604, a recitation page, font scale 1.3, landscape.
