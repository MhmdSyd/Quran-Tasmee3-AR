package com.quranrecite.app.session

import com.quranrecite.app.recitation.ReviewReason
import com.quranrecite.app.recitation.RecitationTracker
import com.quranrecite.app.recitation.ArabicMatch
import com.quranrecite.app.recitation.Ev
import com.quranrecite.app.recitation.TestPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {
    @Test fun activeTimerDoesNotCountPausedTime() {
        var now = 1_000L
        val t = ActiveTimer { now }
        t.start(); now += 5_000
        t.pause(); now += 60_000          // paused for a minute
        assertEquals(5_000, t.elapsedMillis())
        t.start(); now += 2_000
        assertEquals(7_000, t.elapsedMillis())
        t.start()                          // idempotent
        assertEquals(7_000, t.elapsedMillis())
    }

    @Test fun summaryIsBuiltFromTheTrackerReportWithCanonicalText() {
        val page = TestPages.page(3)
        val tr = RecitationTracker()
        tr.begin(page, null, emptyList())
        val words = page.recitableWords
        val f = words.map { ArabicMatch.fold(it.text) }
        // skip word 5, then finish the first 12 words
        tr.onEvent(Ev.fin(0, f.take(5) + f.subList(6, 12), 0))
        tr.hint()
        val s = tr.report().toSummary(startedAtMillis = 42, activeMillis = 154_000)
        assertEquals(3, s.pageNumber)
        assertEquals(13, s.passedWords)
        assertEquals(11, s.confidentWords)
        assertEquals(2, s.review.size)
        assertEquals(setOf(ReviewReason.PossiblySkipped, ReviewReason.Hinted), s.review.map { it.reason }.toSet())
        assertEquals(words[5].text, s.review.first { it.reason == ReviewReason.PossiblySkipped }.text)   // verbatim
        assertEquals(84, s.firstAttemptPercent)
        assertTrue(s.ayahsReached >= 1)
        assertEquals(false, s.completed)
    }

    @Test fun arabicFormatting() {
        assertEquals("٢:٣٤", SummaryText.duration(154_000))
        assertEquals("٠:٠٥", SummaryText.duration(5_000))
        assertEquals("الآية ١٠ · الكلمة ٦", SummaryText.location(10, 6))
    }
}
