package com.quranrecite.app.ui.mushaf

/** What a word contributes to the line-level underline. Markers and shown words contribute [None]. */
enum class WordLine { None, Hidden, Expected, Skipped, Mistake }

fun WordVisual?.lineKind(): WordLine = when (this) {
    WordVisual.Hidden -> WordLine.Hidden
    WordVisual.Expected -> WordLine.Expected
    WordVisual.Skipped -> WordLine.Skipped
    WordVisual.Mistake -> WordLine.Mistake
    else -> WordLine.None
}

enum class SegmentKind { Base, Expected, Skipped, Mistake }

/** A horizontal piece of underline, [left]..[right] in line coordinates (px). */
data class UnderlineSegment(val left: Int, val right: Int, val kind: SegmentKind)

/**
 * Pure geometry of the connected underline (JVM-testable). Words are given in reading order (index 0 = rightmost),
 * [xs] = left x of each word, [widths] = its width. A run of adjacent unrevealed words becomes ONE [SegmentKind.Base]
 * segment from the right edge of its first word to the left edge of its last word, so the gaps between them are
 * covered; shown words and verse markers break a run. Skipped words keep their own (dashed) segment; the Expected and
 * Mistake words get an emphasised overlay on top of the base line.
 */
object MushafUnderline {
    fun segments(xs: IntArray, widths: IntArray, kinds: List<WordLine>): List<UnderlineSegment> {
        val n = minOf(xs.size, widths.size, kinds.size)
        val out = ArrayList<UnderlineSegment>()
        val overlays = ArrayList<UnderlineSegment>()
        var runStart = -1                       // first word (rightmost) of the current base group
        fun closeBase(lastIdx: Int) {
            if (runStart >= 0) out += UnderlineSegment(xs[lastIdx], xs[runStart] + widths[runStart], SegmentKind.Base)
            runStart = -1
        }
        for (i in 0 until n) {
            when (kinds[i]) {
                WordLine.None -> closeBase(i - 1)
                WordLine.Skipped -> {
                    closeBase(i - 1)
                    out += UnderlineSegment(xs[i], xs[i] + widths[i], SegmentKind.Skipped)
                }
                else -> {
                    if (runStart < 0) runStart = i
                    if (kinds[i] == WordLine.Expected) overlays += UnderlineSegment(xs[i], xs[i] + widths[i], SegmentKind.Expected)
                    if (kinds[i] == WordLine.Mistake) overlays += UnderlineSegment(xs[i], xs[i] + widths[i], SegmentKind.Mistake)
                }
            }
        }
        closeBase(n - 1)
        return out + overlays
    }
}
