package com.quranrecite.app.ui.mushaf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafFitTest {
    @Test fun pageScaleIsClamped() {
        assertEquals(1.15f, MushafFit.pageScale(0f), 1e-6f)
        assertEquals(1.15f, MushafFit.pageScale(10f), 1e-6f)
        assertEquals(1f, MushafFit.pageScale(18f), 1e-6f)
        assertEquals(18f / 21.4882f, MushafFit.pageScale(21.4882f), 1e-4f) // widest line over all 604 pages
    }

    @Test fun widestLineAlwaysFitsThePage() {
        var em = 4f
        while (em <= 30f) {
            val font = MushafFit.fontPx(1000f, MushafFit.pageScale(em), 1e9f)
            assertTrue("em=$em", em * font <= 1000f)
            em += 0.25f
        }
    }

    @Test fun smallSlotShrinksFont() {
        assertEquals(40f / MushafFit.MIN_SLOT_EM, MushafFit.fontPx(10_000f, 1f, 40f), 1e-3f)
    }

    @Test fun lineEmUsesGapPerKind() {
        assertEquals(3f + 2 * MushafFit.MIN_GAP_EM, MushafFit.lineEm(listOf(1f, 1f, 1f), false), 1e-6f)
        assertEquals(3f + 2 * MushafFit.NATURAL_GAP_EM, MushafFit.lineEm(listOf(1f, 1f, 1f), true), 1e-6f)
        assertEquals(0f, MushafFit.lineEm(emptyList(), false), 0f)
    }

    @Test fun justifiedLineSpansFullWidthFromTheRight() {
        // first word is rightmost; last word touches x = 0
        assertArrayEquals(intArrayOf(90, 45, 0), MushafFit.placeRtl(intArrayOf(10, 10, 10), 100, false, 5))
    }

    @Test fun centeredLineUsesNaturalGap() {
        assertArrayEquals(intArrayOf(60, 45, 30), MushafFit.placeRtl(intArrayOf(10, 10, 10), 100, true, 5))
    }

    @Test fun singleWordIsCentered() {
        assertArrayEquals(intArrayOf(35), MushafFit.placeRtl(intArrayOf(30), 100, false, 5))
    }

    @Test fun overflowAnchorsRightWithZeroGap() {
        assertArrayEquals(intArrayOf(40, -20), MushafFit.placeRtl(intArrayOf(60, 60), 100, false, 5))
    }
}
