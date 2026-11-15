package com.quranrecite.app.ui.mushaf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure layout math (no Compose, JVM-testable). Widths are in "em" of the Hafs font.
 *
 * The printed mushaf stretches glyphs to justify lines; a Unicode font cannot, so every line is justified by
 * word spacing instead. The page font size is derived from the widest line of the page, clamped so text size
 * stays within [MIN..MAX_GROW] of a reference across pages (measured over all 604 pages: scale 0.84..1.15,
 * median word gap 0.38 em). See tools/measure_lines.py.
 */
object MushafFit {
    /** Page width, in em, that a page whose widest line is [REFERENCE_EM] wide is fitted to. */
    const val REFERENCE_EM = 18f
    /** Largest page-level enlargement (pages whose lines are all short). */
    const val MAX_GROW = 1.15f
    /** Smallest word gap used when measuring a justified line. */
    const val MIN_GAP_EM = 0.15f
    /** Gap of centered lines = width of U+0020 in the Hafs font. */
    const val NATURAL_GAP_EM = 0.22f
    /** Keeps the widest line 1.5% inside the page width (shaping differences between devices). */
    const val SAFETY = 0.985f
    const val LINE_HEIGHT_EM = 1.6f
    /** A line slot must be at least this many em tall, else the font shrinks. */
    const val MIN_SLOT_EM = 1.45f
    /** surah_header_bg.png is 2147 x 227 px. */
    const val HEADER_ASPECT = 2147f / 227f

    /** Inner opening of surah_header_bg.png as fractions of the image (measured: x 360..1786 of 2147, y 23..202 of 227). */
    const val HEADER_HOLE_W = 0.664f
    const val HEADER_HOLE_H = 0.789f
    /** Largest advance / ink height (em) over the 113 glyphs of header-surah-name.ttf. */
    const val SURAH_GLYPH_MAX_ADVANCE_EM = 2.73f
    const val SURAH_GLYPH_MAX_INK_EM = 0.88f

    /**
     * One font size for every surah heading, so that the tallest/widest glyph still sits inside the decoration's opening
     * with margin (90% of its height, 84% of its width; typical glyphs are shorter than the tallest). [headerHeightPx] = rendered height of the decoration.
     */
    fun headerTitlePx(headerHeightPx: Float): Float {
        val headerWidthPx = headerHeightPx * HEADER_ASPECT
        val byHeight = 0.90f * HEADER_HOLE_H * headerHeightPx / SURAH_GLYPH_MAX_INK_EM
        val byWidth = 0.84f * HEADER_HOLE_W * headerWidthPx / SURAH_GLYPH_MAX_ADVANCE_EM
        return min(byHeight, byWidth)
    }

    fun lineEm(wordEms: List<Float>, centered: Boolean): Float {
        if (wordEms.isEmpty()) return 0f
        val gap = if (centered) NATURAL_GAP_EM else MIN_GAP_EM
        return wordEms.sum() + gap * (wordEms.size - 1)
    }

    fun pageScale(widestLineEm: Float): Float =
        if (widestLineEm <= 0f) MAX_GROW else min(MAX_GROW, REFERENCE_EM / widestLineEm)

    fun fontPx(widthPx: Float, scale: Float, slotPx: Float): Float =
        min(widthPx * SAFETY * scale / REFERENCE_EM, slotPx / MIN_SLOT_EM)

    /**
     * Left x of each word of one line, in reading order (index 0 = first word = rightmost).
     * Justified: gaps are equal and the line spans [available]. Centered (or a single word): natural gap, centered.
     * If the words do not fit, gap becomes 0 and the line is anchored right.
     */
    fun placeRtl(widths: IntArray, available: Int, centered: Boolean, naturalGap: Int): IntArray {
        val n = widths.size
        val xs = IntArray(n)
        if (n == 0) return xs
        val sum = widths.sum()
        val gap: Float
        val rightEdge: Float
        if (n == 1 || centered) {
            gap = if (n == 1) 0f else naturalGap.toFloat()
            val total = sum + gap * (n - 1)
            rightEdge = if (total >= available) available.toFloat() else (available + total) / 2f
        } else {
            gap = max(0f, (available - sum).toFloat() / (n - 1))
            rightEdge = available.toFloat()
        }
        var edge = rightEdge
        for (i in 0 until n) {
            val left = edge - widths[i]
            xs[i] = left.roundToInt()
            edge = left - gap
        }
        return xs
    }
}
