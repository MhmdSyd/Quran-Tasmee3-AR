# Agent 5 Handoff — Matching, recitation tracking, word reveal, session summary

## Read this first (honest status)
| Item | Status |
|---|---|
| Matching core (`recitation/`: `ArabicMatch`, `WordAligner`, `RecitationTracker`), session model (`session/`) | **Compiled with Kotlin 2.0.21 and 56 JVM tests run, all pass** (real pages from the databases) |
| Compose screens (`RecitationScreen`, `SummaryScreen`, `ui/components/Icons.kt`), mushaf visuals, `AppContainer`/`AppNav` edits, `lifecycle-runtime-compose` dependency | **Written, NOT compiled** (no Gradle/Google Maven in my sandbox). Only a syntax pass was done. First real compile = GitHub Actions. Expect small API/import fixes. |
| Real ASR + real microphone + real device | **Never run.** Everything below about accuracy is from a *simulated* recogniser (see "Noise simulation"). Real accuracy is unmeasured. |
| Persistence | Sessions are kept in `InMemorySessionStore` (Agent 6's task to persist). Nothing leaves the device (no INTERNET permission). |

Not touched: the ASR engine, the data layer, the mushaf layout. `Strictness` moved to its own file `settings/Strictness.kt` (same package, no import changes) so pure-JVM tests can use it.

## What was built
`speech events → TrackingRecitationMatcher → RecitationProgress → toWordVisuals → MushafPageFrame`
- `recitation/ArabicMatch.kt` — folds with Agent 4's `ModelAlphabet.foldWord`, weighted edit distance, thresholds by word length and `Strictness`.
- `recitation/WordAligner.kt` — DP alignment of heard words against a window of expected words (match / merge / split / insertion / skip).
- `recitation/RecitationTracker.kt` — the state machine (pure, not thread-safe); `TrackingRecitationMatcher` wraps it (synchronized, `StateFlow`).
- `RecitationMatcher` interface extended: `begin(page, startWordId, basmallah)`, `moveTo`, `markRecited`, `hint`, `report`. `AppContainer.recitationMatcher` is now the real one; strictness is read from settings at every event.
- UI: recitation screen per boards 2/3 (header with word counter, hidden page, hint / mic / finish controls, status line), tap a word → "ابدأ من هنا" / "أظهر الكلمة"; summary per board 4 (ring, 3 stat cards, review list, retry / next page).
- Word visuals added: `Expected` (hidden, thicker green underline) and `Skipped` (hidden, dashed gold underline). `Current` = word just recognised. `Uncertain` = dimmed text.

## Matching rules
1. **Canonical text only.** The matcher gets words from `QuranPage.recitableWords` and only reports word *ids/states*. The UI draws the database word; folded strings are used for comparison only. No diacritics are ever generated.
2. **Window.** Hypotheses are aligned against the expected words from the segment's start cursor, `heardWords + 20` wide. If nothing there is explained and ≥3 words were heard, the rest of the page is tried (needs a strong anchor, below).
3. **Match classes** (per word pair, by max length of the two folded words, Normal): ≤3 letters exact; 4 → ≥0.75; 5 → ≥0.78; ≥6 → ≥0.7. *Weak* = 0.2 below that (never for ≤2 letters). Strict: ≤5 exact, else ≥0.85. Lenient: ≤2 exact, 3 → 0.66, else 0.6. Confusable letters (ت/ط, س/ص/ث, ذ/ز/ظ/ض/د, ح/ه/خ, ع/ء, ق/ك/غ) and weak letters (ا و ي ء ه) are cheaper edits. Merged/split recogniser words are handled (merge/split only when the simple one-to-one match is not already a match).
4. **What moves the cursor.** Strong match → revealed. Weak match or a different word → *pending*; committed only if a strong match follows (position confirmed by context): weak → shown dimmed (`Uncertain`), different word → stays hidden, marked (`NotRecognized`). Unheard expected words before a strong match → `Skipped` (`PossiblySkipped`). A weak word alone never moves the cursor.
5. **Jump rules** (words skipped before the next strong match): 0 free; 1 → one word of ≥3 letters or two words; 2–4 → ≥2 words and ≥6 letters; ≥5 → ≥3 words and ≥9 letters. Jumps of ≥3 words are refused (reported as repetition) when the anchor equals words the reciter *just said* (restart / repeated refrain such as فبأي آلاء ربكما تكذبان).
6. **Start elsewhere.** If nothing is revealed yet and the first jump is ≥5 words, the start moves there; the earlier words are out of scope (not counted, not marked).
7. **Partials vs Finals.** Partials contribute only `stableWordCount` words (LocalAgreement-2 from Agent 4), Finals all words. Every hypothesis of a segment is re-aligned from the cursor the segment started at, so re-decoding can never reveal twice; reveals only grow (a better reading of the same segment may upgrade Uncertain/Skipped → Revealed).
8. **Overlap / duplicates.** Finals with `segmentId ≤` the last Final are ignored (redelivery, stale Partials). Words whose audio midpoint lies before the consumed audio time (last word end of the previous Final) are dropped, so an overlapping chunk cannot reveal a repeated word twice even when the text itself repeats. Without timestamps it falls back to alignment (extra words = ignored/repetition). A new engine run (`Listening`, then ids/clock going backwards) resets this bookkeeping, not the position.
9. **Corrections.** An extra heard word that strongly matches one of the last 6 skipped/uncertain words fixes it (`SelfCorrected`, stays in the review list). One matching recent words = repetition (counted, no state change). `moveTo` forward marks words in between `ManuallySkipped`, backward = retry (re-hides from there, counts a retry). `markRecited`, `hint` (reason `Hinted`).
10. **Basmala.** If the page has a Basmallah line, a basmala heard at that position (≥2 of its words, from `MushafDataSource.basmallah()`) is ignored. Isti'adha/other extra words are plain insertions.
11. **No Tajweed verdicts.** There is no "mistake" state. States: Hidden / Revealed / Uncertain / Skipped. "للمراجعة" is neutral (`ReviewReason`), and the summary says so. The old setting `showMistakeImmediately` now means "show review markers (uncertain/skipped) live"; when off they only appear in the summary (label changed in Settings).

## Session
`ActiveTimer` (pause-aware), `RecitationReport.toSummary(...)` → `SessionSummary` (page, active time, total/passed/confident words, review entries with canonical text, ayahs reached/completed, repetitions, retries, completed). "من أول محاولة" = confident ÷ passed. Ayah numbers count ayahs touched *on this page*.
Lifecycle: mic paused on `ON_STOP` and resumed on `ON_START` if it was listening; engine stopped (via `appScope`) when the screen is left; screen kept on; finishing calls `engine.stop()` (drains), yields to the collector, then builds the summary. Reaching the last word finishes automatically.

## Tests (`./gradlew testDebugUnitTest`; I ran them with the Kotlin compiler + JUnit directly)
`ArabicMatchTest` (folding, short words, similar words, confusables), `RecitationTrackerTest` (full pages in segments, partials→final monotonic, refrains/repeated words on Ar-Rahman, cuts at many positions, similar words, weak/uncertain, skipped, far jumps, start elsewhere, pauses/resume/flush/forced cut, duplicate/overlapping chunks incl. repeated word resolved by time, engine restart, basmala, hint/move/mark, feedback), `RecitationNoiseTest`, `SessionSummaryTest`. Fixture `app/src/test/resources/test_pages.tsv` = 10 real pages exported by `tools/export_test_pages.py` (CI runs `--check` so it cannot drift from the databases; the app never reads it).

### Noise simulation (SIMULATED errors, not real ASR)
10 pages × 8 seeds; per word: reciter skips 1 %, recogniser drops 3 %, 1 wrong letter 5 %, 2 wrong letters 2 %, glued 2 %, split 2 %. Words revealed (shown): Lenient 96.2 %, Normal 93.1 %, Strict 90.5 % of 7,496 words; words nobody said but shown: 22 / 12 / 1; pages tracked to (within 3 words of) the end: 80/80, 80/80, 78/80. The "shown but unspoken" ones are glued/garbled words accepted by similarity. Real recitation (speed, tajweed elongation, noise, real CTC errors) will differ; measure on device.

## Edge cases & known limitations
- **Refrains/identical repeated text** are inherently ambiguous: repeating the previous refrain instead of continuing is treated as repetition only for jumps ≥3 words; a reciter who truly jumps to a later identical refrain must tap the word ("ابدأ من هنا").
- **Look-alike words** one letter apart in ≥6-letter words (يعلمون/تعلمون, الرحمن/الرحيم) are *accepted* in Normal (ASR error range), only weak in Strict. The app therefore cannot tell a substituted similar word from an ASR slip.
- Words dropped by the recogniser look like skipped words (shown as "possibly skipped"); that is why the wording is "ربما".
- After a skip, the next segment needs enough evidence (see jump rules): a very short segment right after a missed word may wait until more is said.
- Latency: reveals follow `stableWordCount`, i.e. roughly one partial interval (0.6–1.2 s) behind the voice, plus 0.6 s endpointing for Finals. Not measured.
- A word split by a forced cut is usually shown as uncertain/skipped or recognised from its tail; no cross-segment word stitching.
- Page-bounded: matching stops at the page's last word (no auto-continue to the next page; the summary offers "الصفحة التالية"). Pages 262, 378, 441, 451 have Agent 2's unverified line breaks, which do not affect word order/matching.
- Dialog/summary strings are hard-coded Arabic in `session/SummaryText.kt` and the screens. Cairo font still not bundled.
- Not covered: Compose UI tests, device tests, real audio, accessibility pass beyond content descriptions.

## Suggested next steps
Compile on CI and fix small Compose issues → on a phone run Settings → ASR diagnostics, then recite a page and tune `RELOCATE_DISTANCE`, `REPEAT_GUARD_DISTANCE`, `anchorOk`, thresholds in `ArabicMatch` against real transcripts (log `Final.text` vs expected) → Agent 6: persist `SessionSummary`, history screen.
