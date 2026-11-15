package com.quranrecite.app.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowAyahTest {

    private fun ayahKey(h: Harness, i: Int) = h.words[i].surah to h.words[i].ayah
    private fun ayahRange(h: Harness, i: Int): IntRange {
        var a = i; var b = i
        while (a > 0 && ayahKey(h, a - 1) == ayahKey(h, i)) a--
        while (b + 1 < h.words.size && ayahKey(h, b + 1) == ayahKey(h, i)) b++
        return a..b
    }

    @Test fun firstPressRevealsOnlyTheCurrentAyahAndMovesPastIt() {
        val h = Harness(3)
        val r = ayahRange(h, 0)
        assertEquals(ShowAyahResult.Shown, h.tracker.showAyah())
        assertTrue(h.states(r.first, r.last + 1).all { it == WordState.Revealed })
        assertTrue(h.states(r.last + 1, h.words.size).all { it == WordState.Hidden })
        assertEquals(r.last + 1, h.cursorIndex())
        assertFalse(h.p.finished)
    }

    @Test fun eachPressShowsTheNextAyahInOrderUntilPageEnd() {
        val h = Harness(50)
        var expectedStart = 0
        while (expectedStart < h.words.size) {
            val r = ayahRange(h, expectedStart)
            assertEquals(ShowAyahResult.Shown, h.tracker.showAyah())
            assertTrue(h.states(r.first, r.last + 1).all { it == WordState.Revealed })
            assertEquals(r.last + 1, h.cursorIndex())
            expectedStart = r.last + 1
        }
        assertTrue(h.p.finished)
        assertEquals(h.words.size, h.p.revealedCount)
        assertEquals(ShowAyahResult.PageEnd, h.tracker.showAyah())   // nothing left: the UI moves to the next page
    }

    @Test fun shownWordsAreTheCanonicalOnes() {
        val h = Harness(3)
        h.tracker.showAyah()
        val r = ayahRange(h, 0)
        val shown = h.p.wordStates.keys
        assertTrue(r.all { h.words[it].id in shown })
    }

    @Test fun midAyahRevealsTheRestAndKeepsEarlierReasons() {
        val h = Harness(3)
        val r = ayahRange(h, 0)
        assertTrue("fixture ayah needs 3+ words", r.count() >= 3)
        h.final(0, 2)                       // recited normally
        h.tracker.showAyah()
        assertTrue(h.states(r.first, r.last + 1).all { it == WordState.Revealed })
        val reasons = h.reasons()
        assertFalse(reasons.containsKey(h.words[0].id))               // recognised words stay "confident"
        assertEquals(ReviewReason.Manual, reasons[h.words[2].id])      // the rest is marked as shown by hand
        assertEquals(r.last + 1, h.cursorIndex())
    }

    @Test fun skippedWordsOfTheAyahAreRevealedToo() {
        val h = Harness(3)
        val r = ayahRange(h, 0)
        h.final(0, 1)
        h.tracker.moveTo(h.words[4].id)     // jumps over words 1..3: they become Skipped
        assertEquals(WordState.Skipped, h.st(2))
        h.tracker.showAyah()
        assertTrue(h.states(r.first, r.last + 1).all { it == WordState.Revealed })
    }

    @Test fun reciting_continues_after_showing_an_ayah() {
        val h = Harness(3)
        val r = ayahRange(h, 0)
        h.tracker.showAyah()
        val next = r.last + 1
        h.final(next, minOf(h.words.size, next + 5))
        assertTrue(h.states(next, minOf(h.words.size, next + 5)).all { it == WordState.Revealed })
        assertEquals(minOf(h.words.size, next + 5), h.cursorIndex())
    }

    @Test fun showingDoesNotCorruptTheMatchingPositionForLaterSpeech() {
        val h = Harness(50)
        h.final(0, 3)
        h.tracker.showAyah()
        val c = h.cursorIndex()
        // speech of an ayah that was already shown does not drag the cursor back
        h.final(0, 3)
        assertEquals(c, h.cursorIndex())
    }

    @Test fun continueOnPageStartsTheNewPageAtItsFirstWord() {
        val h = Harness(531)
        h.final(0, 3)
        h.tracker.continueOnPage(TestPages.page(532), emptyList())
        val p = h.tracker.progress()
        assertEquals(532, p.pageNumber)
        assertEquals(TestPages.page(532).recitableWords.first().id, p.currentWordId)
        assertEquals(0, p.revealedCount)
        assertEquals(ShowAyahResult.Shown, h.tracker.showAyah())
    }
}
