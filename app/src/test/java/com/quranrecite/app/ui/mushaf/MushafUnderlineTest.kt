package com.quranrecite.app.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafUnderlineTest {
    private val H = WordLine.Hidden
    private val N = WordLine.None

    // 5 words of width 20, gap 10, reading order from the right: word0 is rightmost. Line width 140.
    private val widths = IntArray(5) { 20 }
    private val xs = MushafFit.placeRtl(widths, 140, centered = false, naturalGap = 0)   // 120,90,60,30,0

    private fun segs(vararg k: WordLine) = MushafUnderline.segments(xs, widths, k.toList())

    @Test fun placementAssumptionHolds() {
        assertEquals(listOf(120, 90, 60, 30, 0), xs.toList())
    }

    @Test fun allHiddenIsOneContinuousLineAcrossTheGaps() {
        assertEquals(listOf(UnderlineSegment(0, 140, SegmentKind.Base)), segs(H, H, H, H, H))
    }

    @Test fun firstWordRevealedLineStartsAtTheSecondWord() {
        assertEquals(listOf(UnderlineSegment(0, 110, SegmentKind.Base)), segs(N, H, H, H, H))
    }

    @Test fun lastWordsRevealedLineEndsAtTheLastHiddenWord() {
        assertEquals(listOf(UnderlineSegment(60, 140, SegmentKind.Base)), segs(H, H, H, N, N))
    }

    @Test fun middleWordsRevealedSplitTheLineIntoTwoConnectedParts() {
        assertEquals(
            listOf(UnderlineSegment(90, 140, SegmentKind.Base), UnderlineSegment(0, 20, SegmentKind.Base)),
            segs(H, H, N, N, H),
        )
    }

    @Test fun fullyRevealedLineHasNoUnderline() {
        assertTrue(segs(N, N, N, N, N).isEmpty())
    }

    @Test fun verseMarkerBreaksTheRun() {
        // index 2 is a (always visible) marker -> kind None
        val s = segs(H, H, N, H, H)
        assertEquals(2, s.size)
        assertEquals(UnderlineSegment(90, 140, SegmentKind.Base), s[0])
        assertEquals(UnderlineSegment(0, 50, SegmentKind.Base), s[1])
    }

    @Test fun shortLinesAndSingleWordLines() {
        val w = intArrayOf(50); val x = MushafFit.placeRtl(w, 140, centered = true, naturalGap = 0)
        val s = MushafUnderline.segments(x, w, listOf(H))
        assertEquals(listOf(UnderlineSegment(45, 95, SegmentKind.Base)), s)
        assertTrue(MushafUnderline.segments(IntArray(0), IntArray(0), emptyList()).isEmpty())
    }

    @Test fun skippedWordKeepsItsOwnSegmentAndExpectedGetsAnOverlay() {
        val s = segs(WordLine.Expected, H, WordLine.Skipped, H, H)
        assertEquals(UnderlineSegment(90, 140, SegmentKind.Base), s[0])
        assertEquals(UnderlineSegment(60, 80, SegmentKind.Skipped), s[1])
        assertEquals(UnderlineSegment(0, 50, SegmentKind.Base), s[2])
        assertEquals(UnderlineSegment(120, 140, SegmentKind.Expected), s.last())
    }

    @Test fun segmentsNeverCoverShownWords() {
        val kinds = listOf(N, H, H, N, H)
        for (seg in MushafUnderline.segments(xs, widths, kinds)) {
            for (i in kinds.indices) if (kinds[i] == N) {
                val l = xs[i]; val r = xs[i] + widths[i]
                assertTrue("covers shown word $i", seg.right <= l || seg.left >= r)
            }
        }
    }

    @Test fun visualMappingOnlyUnderlinesUnrevealedStates() {
        assertEquals(WordLine.Hidden, WordVisual.Hidden.lineKind())
        assertEquals(WordLine.Expected, WordVisual.Expected.lineKind())
        assertEquals(WordLine.Skipped, WordVisual.Skipped.lineKind())
        for (v in listOf(WordVisual.Normal, WordVisual.Revealed, WordVisual.Correct, WordVisual.Current, WordVisual.Uncertain)) {
            assertEquals(WordLine.None, v.lineKind())
        }
    }

    @Test fun surahGlyphMapping() {
        assertEquals(null, SurahNameGlyph.of(1))
        assertEquals("\uE002", SurahNameGlyph.of(2))
        assertEquals("\uE072", SurahNameGlyph.of(114))
        assertEquals(null, SurahNameGlyph.of(115))
    }

    @Test fun headerTitleFitsInsideTheDecorationOpening() {
        for (h in listOf(40f, 80f, 160f)) {
            val px = MushafFit.headerTitlePx(h)
            val hole = MushafFit.HEADER_HOLE_H * h
            assertTrue(px * MushafFit.SURAH_GLYPH_MAX_INK_EM <= hole * 0.92f)
            assertTrue(px * MushafFit.SURAH_GLYPH_MAX_ADVANCE_EM <= MushafFit.HEADER_HOLE_W * h * MushafFit.HEADER_ASPECT * 0.85f)
        }
    }
}
