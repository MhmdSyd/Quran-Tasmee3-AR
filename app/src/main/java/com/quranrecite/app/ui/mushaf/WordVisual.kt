package com.quranrecite.app.ui.mushaf

import com.quranrecite.app.recitation.RecitationProgress
import com.quranrecite.app.recitation.WordState

/** Whether the mushaf is being read or recited. Only changes the default state of a word without an explicit entry. */
enum class MushafMode {
    /** Every word without an explicit state is [WordVisual.Normal]. */
    Reading,

    /** Every word without an explicit state is [WordVisual.Hidden]. Verse-number markers stay visible. */
    Recitation,
}

/**
 * How one word is drawn. Pure presentation: the caller (recitation UI) decides which word gets which state.
 * Every state keeps the word's exact layout width, so changing a state never moves other words.
 * Styling follows the design boards 2 and 3.
 */
enum class WordVisual {
    /** Plain visible text (reading mode). */
    Normal,

    /** Not recited yet: text is invisible, a thin underline marks the word's exact width. */
    Hidden,

    /** Recited and shown, no emphasis. */
    Revealed,

    /** The word to emphasise, e.g. just recognised: green text on a light-green chip. */
    Current,

    /** Recited correctly: shown in green, no chip. */
    Correct,

    /** Recognition not confident: dimmed text, gold dashed underline on a gold tint. */
    Uncertain,

    /** Wrong word: stays hidden, red underline on a red tint. (Not produced by the matcher: it never judges pronunciation.) */
    Mistake,

    /** Hidden, and the word the reciter is expected to say next: stronger green underline. */
    Expected,

    /** Hidden, passed without being recognised (possibly skipped): dashed gold underline, no tint. */
    Skipped,
}

fun WordState.toVisual(): WordVisual = when (this) {
    WordState.Hidden -> WordVisual.Hidden
    WordState.Revealed -> WordVisual.Revealed
    WordState.Uncertain -> WordVisual.Uncertain
    WordState.Skipped -> WordVisual.Skipped
}

/**
 * Adapter from the matcher's progress to [MushafPage]'s `wordStates`.
 * The word just recognised is drawn as [WordVisual.Current], the next expected (still hidden) word as
 * [WordVisual.Expected]. With [showReviewMarks] = false, uncertain words look like normal revealed words and skipped
 * words stay plain hidden: the markers then appear only in the summary.
 */
fun RecitationProgress.toWordVisuals(showReviewMarks: Boolean = true): Map<Int, WordVisual> {
    val out = HashMap<Int, WordVisual>(wordStates.size + 2)
    for ((id, state) in wordStates) {
        out[id] = when {
            state == WordState.Uncertain && !showReviewMarks -> WordVisual.Revealed
            state == WordState.Skipped && !showReviewMarks -> WordVisual.Hidden
            else -> state.toVisual()
        }
    }
    currentWordId?.let { if (out[it] == null || out[it] == WordVisual.Hidden) out[it] = WordVisual.Expected }
    lastRecognizedId?.let { if (wordStates[it] == WordState.Revealed || wordStates[it] == WordState.Uncertain) out[it] = WordVisual.Current }
    return out
}

/** 604 -> "٦٠٤" (the mushaf prints page and verse numbers in Arabic-Indic digits). */
fun Int.toArabicDigits(): String =
    toString().map { if (it in '0'..'9') '\u0660' + (it - '0') else it }.joinToString("")
