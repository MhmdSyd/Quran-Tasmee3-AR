# AGENT_3_UI_FINAL_REPORT — Typography, connected underline, final UI review

## Changed files
- `res/font/header_surah_name.ttf` (new; the supplied font, renamed — Android resource names cannot contain `-`).
- `ui/theme/Theme.kt` — `SurahNameFont`.
- `ui/mushaf/SurahName.kt` (new) — `SurahNameText`, `SurahNameGlyph`.
- `ui/mushaf/MushafUnderline.kt` (new) — pure underline geometry (`WordLine`, `UnderlineSegment`, `MushafUnderline.segments`).
- `ui/mushaf/MushafPage.kt` — `JustifiedLine` draws the connected underline; `SurahHeaderLine` uses the surah font.
- `ui/mushaf/MushafWord.kt` — per-word underlines for hidden/expected/skipped/mistake removed (chips and the uncertain dashed line stay).
- `ui/mushaf/MushafFit.kt` — `headerTitlePx` + decoration geometry constants.
- `ui/screens/ReaderScreen.kt`, `QuranNavigationSheet.kt` — surah names (title button, list) use the surah font.
- `ui/screens/RecitationScreen.kt` — header settings icon sliders -> gear (consistency fix).
- `test/.../ui/mushaf/MushafUnderlineTest.kt` (new); `THIRD_PARTY_NOTICES.md`.

## 1. Surah-name font
Findings from the font file (fontTools): one glyph per surah that already contains the word "سورة" (e.g. "سُورَةُ البَقَرَةِ"), mapped at **U+E000 + surah number, surahs 2..114** (also reachable by the ligature `surah002`…). So the heading draws only that glyph — no extra "سورة " text. Used on the mushaf heading line (reader, recitation page), the reader title button and the surah list. Ayah text still uses the unchanged Hafs font; the decoration PNG is untouched.
**Surah 1 (الفاتحة) has no glyph in the supplied font** (113 glyphs, none for 001). It falls back to "سورة الفاتحة" in the existing Hafs font, scaled to fit. Send a font with glyph 001 and change `SurahNameGlyph.of` to `1..114`.
Size/centering: the decoration's inner opening was measured from the PNG (x 360–1786 of 2147, y 23–202 of 227). One font size for all headings = 90% of the opening height for the tallest glyph (0.88 em) and ≤84% of its width (`MushafFit.headerTitlePx`), i.e. ≈0.81 × the decoration's height at any screen size (it scales with the decoration, not the page text). Glyph ink centre is on average 0.314 em above the baseline vs 0.30 em for the text line box, so Compose's normal centering is within ~1% of the font size; over all 113 glyphs the ink centre stays within −7%…+4.5% of the header height (tall diacritics/descenders).

## 2. Connected underline
Drawn per line in `JustifiedLine` from the real word positions/widths (from `MushafFit.placeRtl`, baseline via `FirstBaseline`). A run of adjacent unrevealed words = one segment from the right edge of its first word to the left edge of its last, so spaces between them are covered. Revealed words and verse-number markers break the run; nothing is drawn under them; a fully revealed line has no underline. Expected word: thicker green overlay; skipped word: its own dashed gold segment. Words still occupy their space (transparent text), so revealing never shifts anything; the 15-line layout code is untouched.

## Tests
- Not run: no Android SDK, JVM build tooling or network in this sandbox. `MushafUnderlineTest` expectations were verified against a line-for-line Python port of `placeRtl`/`segments` (all match).
- Python port rendered with the real fixture pages, the real Hafs and surah fonts and the real decoration PNG: pages 2 (first words revealed; several middle runs revealed), 187, 604 (headers for surahs 2, 9, 112–114 and short lines); underline stays connected and breaks exactly at revealed words/markers; headings centered at 360/720/1080 px widths.
- Static check: brackets balance in all changed Kotlin files.

## 3. Integration review (code inspection)
| Item | Result |
|---|---|
| Nav button opens surah list; Surah / Page / Juz tabs | present (`ReaderScreen`, `QuranNavigationSheet`) |
| Settings = gear icon | present on reader; recitation header changed from sliders to gear |
| `<` `>` arrows, no Arabic words, together on the left | present (reader bottom row; recitation control row, forced LTR) |
| Mic on the right; idle/active/paused icon+colour | present (green mic / amber pause / slate-blue play, no glow) |
| إظهار الآية reveals current ayah, then next ones | present (`showAyah`, tested in `ShowAyahTest`) |
| Surah names use header-surah-name.ttf | present (except surah 1, see above) |
| Hidden words: connected underline | present |

Not fixed / worth knowing: nothing could be compiled or run on a device, so please run `./gradlew testDebugUnitTest assembleDebug` and look at a surah-opening page (e.g. 2, 187, 604) and a partly revealed page. No git repo/remote was in the upload, so nothing was committed or pushed.

## Follow-up (UI round 2)
- Hidden-word underline: `hiddenLine` = gray `0x738A8A8A` (~45% opacity), 0.75dp (`MushafStyle`, `MushafPage`).
- Background: `Paper` / `@color/paper` / recitation + summary `ScreenBg` = pure white.
- Recitation controls: arrows, mic, "تلميح", "إظهار الآية" are all 48dp high; row padding = the reader's bottom bar (8dp vertical), so the page area has the same size while reading and reciting. Hint/show-ayah are compact text buttons (no label under them).
- Welcome page: `WelcomeScreen` (white, `res/drawable-nodpi/bismillah.png`, transparent-background version of the supplied image), start destination, ~2.2 s or tap, then the reader opens at page 1.
- Not compiled/run (no Android SDK here).
