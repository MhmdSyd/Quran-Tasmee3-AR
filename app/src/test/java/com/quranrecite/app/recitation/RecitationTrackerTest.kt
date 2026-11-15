package com.quranrecite.app.recitation

import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.speech.SegmentEnd
import com.quranrecite.app.speech.SpeechEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationTrackerTest {

    // ---------------------------------------------------------------------------------------- the happy path

    @Test fun reciteWholePageInSegmentsRevealsEverythingInOrder() {
        for (pageNo in listOf(1, 2, 3, 50, 187, 262, 586, 604)) {
            val h = Harness(pageNo)
            val n = h.words.size
            var i = 0
            while (i < n) { val j = minOf(n, i + 7); h.final(i, j); i = j }
            assertTrue("page $pageNo finished", h.p.finished)
            assertEquals("page $pageNo", n, h.p.revealedCount)
            assertTrue(h.states(0, n).all { it == WordState.Revealed })
            val rep = h.tracker.report()
            assertEquals(n, rep.confidentWords); assertTrue(rep.reviewItems.isEmpty())
            assertNull(h.p.currentWordId)
        }
    }

    @Test fun displayedWordsAreAlwaysTheCanonicalOnes() {
        val h = Harness(3)
        h.final(0, 5)
        val shown = h.page.recitableWords.filter { h.p.wordStates[it.id] == WordState.Revealed }
        // the matcher only reports ids; text comes from the page, untouched (diacritics intact)
        assertEquals(h.words.take(5).map { it.text }, shown.map { it.text })
        assertTrue(shown.all { w -> w.text.any { it in 'ً'..'ْ' || it == 'ٰ' || it == 'ۡ' } })
    }

    @Test fun partialsThenFinalNeverRevealTwiceAndNeverShrink() {
        val h = Harness(3)
        val say = h.say(0, 12)
        var prev = emptySet<Int>()
        for (k in 1..say.size) {                      // growing hypotheses of ONE segment, as the engine re-decodes it
            h.tracker.onEvent(Ev.part(0, say.take(k), 0))
            val now = h.p.wordStates.filterValues { it != WordState.Hidden }.keys
            assertTrue("reveals only grow", now.containsAll(prev))
            prev = now
        }
        assertEquals("the last word of a partial is never trusted", 11, h.p.revealedCount)
        h.tracker.onEvent(Ev.fin(0, say, 0))
        assertEquals(12, h.p.revealedCount)
        assertEquals(12, h.cursorIndex())
        assertTrue(h.tracker.report().reviewItems.isEmpty())
    }

    @Test fun partialWithNothingStableRevealsNothing() {
        val h = Harness(3)
        h.tracker.onEvent(Ev.part(0, h.say(0, 3), 0, stable = 0))
        assertEquals(0, h.p.revealedCount)
        assertEquals(h.words[0].id, h.p.currentWordId)
    }

    // ---------------------------------------------------------------------------------------- repeated words

    @Test fun refrainThatRepeatsInTheTextDoesNotMakeTheCursorJump() {
        val h = Harness(531)          // Surat Ar-Rahman: "فبأي ءالاء ربكما تكذبان" every few ayahs
        // find all starts of the 4-word refrain (words of the first occurrence)
        val first = h.words.indexOfFirst { it.surah == 55 && it.ayah == 13 }
        val pattern = h.folded.subList(first, first + 4)
        val starts = h.folded.indices.filter { i -> i + 4 <= h.folded.size && h.folded.subList(i, i + 4) == pattern }
        assertTrue("several refrains on the page: $starts", starts.size >= 2)
        // recite up to and including the first refrain, then stop
        h.final(0, starts[0] + 4)
        assertEquals(starts[0] + 4, h.cursorIndex())
        // continue with the words between refrain 1 and 2, then refrain 2 in its own segment
        h.final(starts[0] + 4, starts[1])
        assertEquals(starts[1], h.cursorIndex())
        h.final(starts[1], starts[1] + 4)
        assertEquals(starts[1] + 4, h.cursorIndex())
        assertTrue(h.tracker.report().reviewItems.isEmpty())
        assertEquals(0, h.tracker.report().repetitions)
    }

    @Test fun reciteTheWholeRahmanPageCutAtEveryPossiblePoint() {
        // every cut position, including cuts inside refrains
        val n = Harness(531).words.size
        for (cut in listOf(1, 2, 3, 5, 9, 13, 21)) {
            val h = Harness(531)
            var i = 0
            while (i < n) { val j = minOf(n, i + cut); h.final(i, j); i = j }
            assertTrue("cut=$cut finished", h.p.finished)
            assertTrue("cut=$cut clean", h.tracker.report().reviewItems.isEmpty())
        }
    }

    @Test fun refrainSaidAgainInsteadOfTheNextAyahIsARepetitionNotAJumpOverAyahs() {
        val h = Harness(531)
        val first = h.words.indexOfFirst { it.surah == 55 && it.ayah == 13 }
        val pattern = h.folded.subList(first, first + 4)
        val second = h.folded.indices.first { it > first + 4 && h.folded.subList(it, it + 4) == pattern }
        h.final(0, first + 4)
        h.final(pattern)                               // reciter says the refrain again
        assertEquals("cursor must not leap to the next refrain", first + 4, h.cursorIndex())
        assertEquals(RecitationFeedback.PossibleRepetition, h.p.feedback)
        assertTrue(h.p.skippedCount == 0)
        assertEquals(1, h.tracker.report().repetitions.coerceAtMost(1))
        // and the reciter can continue normally afterwards
        h.final(first + 4, second + 4)
        assertEquals(second + 4, h.cursorIndex())
    }

    @Test fun restartingTheAyahAfterAHesitationIsIgnoredAsRepetition() {
        val h = Harness(3)
        h.final(h.say(0, 3) + h.say(0, 5))             // "a b c | a b c d e"
        assertEquals(5, h.cursorIndex())
        assertEquals(5, h.p.revealedCount)
        assertTrue(h.tracker.report().repetitions >= 1)
    }

    // ---------------------------------------------------------------------------------------- similar words

    @Test fun aWordOneLetterOffIsAcceptedInNormalAndOnlyUncertainInStrict() {
        val i = 10                                       // "يومنون": six letters, one edit = 0.83
        val probe = Harness(3)
        assertEquals(6, probe.folded[i].length)
        val off = mangle(probe.folded[i], 1)
        val n = Harness(3, Strictness.Normal)
        n.final(n.say(0, i) + off + n.say(i + 1, i + 4))
        assertEquals(WordState.Revealed, n.st(i))
        val s = Harness(3, Strictness.Strict)
        s.final(s.say(0, i) + off + s.say(i + 1, i + 4))
        assertEquals("strict: shown only because the neighbours confirm it, and marked", WordState.Uncertain, s.st(i))
        assertEquals(i + 4, s.cursorIndex())
        // alone (no confirming neighbour) it must not move the cursor in strict mode
        val s2 = Harness(3, Strictness.Strict)
        s2.final(s2.say(0, i)); s2.final(listOf(off))
        assertEquals(i, s2.cursorIndex())
    }

    @Test fun aSimilarWordLaterOnThePageDoesNotPullTheCursor() {
        val h = Harness(3)
        // a lone word that exists further down the page (strong match, but alone and far): rejected
        val far = h.folded.indices.first { it > 40 && h.folded[it].length >= 5 && h.folded.subList(0, 20).none { f -> f == h.folded[it] } }
        h.final(listOf(h.folded[far]))
        assertEquals(0, h.cursorIndex())
        assertEquals(0, h.p.revealedCount)
        assertEquals("a single stray word is not worth a message", RecitationFeedback.None, h.p.feedback)
    }

    @Test fun shortLookAlikesNeverAdvance() {
        // expected "في"/"من"/"لا" etc. are matched only by the identical word
        val h = Harness(3)
        val i = h.folded.indexOfFirst { it.length <= 2 }
        h.final(0, i)
        h.final(listOf(if (h.folded[i] == "ما") "لا" else "ما"))
        assertEquals(i, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- weak / uncertain

    @Test fun aWeakWordAloneDoesNotMoveTheCursor() {
        val h = Harness(3)
        val i = 10                                       // six letters: two edits = 0.67 = weak band
        h.final(0, i)
        val w = h.folded[i]; assertEquals(6, w.length)
        h.final(listOf(mangle(w, 1, 3)))               // 2 edits: weak band
        assertEquals(i, h.cursorIndex())
        assertEquals(WordState.Hidden, h.st(i))
    }

    @Test fun aWeakWordConfirmedByTheNextWordIsShownAsUncertain() {
        val h = Harness(3)
        val i = 10
        h.final(0, i)
        val w = h.folded[i]
        assertEquals(6, w.length)
        h.final(listOf(mangle(w, 1, 3)) + h.say(i + 1, i + 4))
        assertEquals(WordState.Uncertain, h.st(i))
        assertEquals(RecitationFeedback.Uncertain, h.p.feedback)
        assertEquals(i + 4, h.cursorIndex())
        assertEquals(ReviewReason.Uncertain, h.reasons()[h.words[i].id])
        assertEquals(1, h.p.uncertainCount)
    }

    // ---------------------------------------------------------------------------------------- skipped / extra words

    @Test fun aWordTheReciterLeftOutIsMarkedPossiblySkippedAndStaysHidden() {
        val h = Harness(3)
        val i = 5
        h.final(h.say(0, i) + h.say(i + 1, i + 5))
        assertEquals(WordState.Skipped, h.st(i))
        assertEquals(ReviewReason.PossiblySkipped, h.reasons()[h.words[i].id])
        assertEquals(RecitationFeedback.PossibleSkip, h.p.feedback)
        assertEquals(i + 5, h.cursorIndex())
        assertEquals(1, h.p.skippedCount)
        assertEquals(i + 4, h.p.revealedCount)
    }

    @Test fun extraWordsBetweenRealOnesAreIgnored() {
        val h = Harness(3)
        h.final(h.say(0, 3) + listOf("اعوذ", "بالله") + h.say(3, 8))
        assertEquals(8, h.cursorIndex())
        assertEquals(8, h.p.revealedCount)
        assertEquals(0, h.p.skippedCount)
    }

    @Test fun aFarJumpNeedsASeveralWordAnchor() {
        val h = Harness(3)
        val far = 30
        h.final(h.say(far, far + 2))                    // two words, but 30 words away: not enough
        assertEquals(0, h.cursorIndex())
        h.final(h.say(0, 3))
        val h2 = Harness(3)
        h2.final(0, 6)
        h2.final(h2.say(20, 24))                        // 4 consecutive strong words: a real jump
        assertEquals(24, h2.cursorIndex())
        assertTrue((6 until 20).all { h2.st(it) == WordState.Skipped })
        assertTrue((20 until 24).all { h2.st(it) == WordState.Revealed })
    }

    @Test fun startingElsewhereOnThePageMovesTheStartInsteadOfFlaggingEverythingBeforeIt() {
        val h = Harness(3)
        h.final(h.say(25, 31))
        assertEquals(31, h.cursorIndex())
        assertEquals("nothing before the real start is marked", 0, h.p.skippedCount)
        assertEquals(h.words.size - 25, h.p.totalWords)
        assertTrue(h.tracker.report().reviewItems.isEmpty())
    }

    @Test fun startingAtAChosenWordTracksFromThere() {
        val h = Harness(3, startWord = Harness(3).words[10].id)
        assertEquals(10, h.cursorIndex())
        h.final(10, 15)
        assertEquals(15, h.cursorIndex())
        assertEquals(h.words.size - 10, h.p.totalWords)
        assertEquals(WordState.Hidden, h.st(0))
    }

    @Test fun aMissedWordSaidAgainAfterwardsIsRecognisedAsSelfCorrection() {
        val h = Harness(3)
        h.final(h.say(0, 4) + listOf("شزجش") + h.say(5, 8))
        assertEquals(WordState.Skipped, h.st(4))
        h.final(listOf(h.folded[4]))                    // "oops, it was this word"
        assertEquals(WordState.Revealed, h.st(4))
        assertEquals(ReviewReason.SelfCorrected, h.reasons()[h.words[4].id])
        assertEquals(8, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- pauses & interruptions

    @Test fun aLongPauseChangesNothingAndTheReciterContinuesWhereTheyStopped() {
        val h = Harness(3)
        h.final(0, 6)
        val before = h.p
        h.clock += 10 * 60_000                           // ten minutes of silence: the engine emits nothing
        assertEquals(before, h.tracker.progress())
        h.tracker.onEvent(SpeechEvent.Listening)         // resume()
        h.final(6, 12)
        assertEquals(12, h.cursorIndex())
        assertTrue(h.tracker.report().reviewItems.isEmpty())
    }

    @Test fun speechFlushedByPauseIsAnOrdinarySegmentAndTheRemainderContinues() {
        val h = Harness(3)
        val a = Ev.fin(0, h.say(0, 5), 0, SegmentEnd.Flushed)       // pause(): open speech is flushed as a Final
        h.tracker.onEvent(a)
        h.tracker.onEvent(SpeechEvent.Listening)
        h.tracker.onEvent(Ev.fin(1, h.say(5, 9), a.audioEndMs + 1))   // the clock does not advance while paused
        assertEquals(9, h.cursorIndex())
    }

    @Test fun forcedCutKeepsTheNextSegmentContinuing() {
        val h = Harness(3)
        val a = Ev.fin(1, h.say(0, 6), 0, SegmentEnd.MaxLength)
        h.tracker.onEvent(a)
        h.tracker.onEvent(Ev.part(2, h.say(6, 10), a.audioEndMs, stable = 3))
        assertEquals(9, h.cursorIndex())
        h.tracker.onEvent(Ev.fin(2, h.say(6, 10), a.audioEndMs))
        assertEquals(10, h.cursorIndex())
    }

    @Test fun aWordSplitByAForcedCutIsStillMatched() {
        val h = Harness(3)
        val w = h.folded[5]; assertTrue(w.length >= 4)
        val a = h.say(0, 5) + w.substring(0, 2)          // cut inside word 5: its first half ends segment 1
        val e1 = Ev.fin(1, a, 0, SegmentEnd.MaxLength)
        h.tracker.onEvent(e1)
        h.tracker.onEvent(Ev.fin(2, listOf(w.substring(2)) + h.say(6, 9), e1.audioEndMs))
        assertEquals("the rest of the page is tracked", 9, h.cursorIndex())
        assertTrue("the cut word is either recognised from its tail or marked, never lost", h.st(5) != WordState.Hidden)
        assertTrue((6 until 9).all { h.st(it) == WordState.Revealed })
    }

    @Test fun manualPositionSurvivesAndContinues() {
        val h = Harness(3)
        h.final(0, 4)
        h.tracker.moveTo(h.words[10].id)
        assertEquals(10, h.cursorIndex())
        assertTrue((4 until 10).all { h.st(it) == WordState.Skipped })
        h.final(10, 14)
        assertEquals(14, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- chunk overlap

    @Test fun theSameFinalDeliveredTwiceIsIgnored() {
        val h = Harness(3)
        val e = h.final(0, 6)
        val after = h.p
        h.tracker.onEvent(e)
        h.tracker.onEvent(e)
        assertEquals(after, h.p)
        assertEquals(6, h.cursorIndex())
        assertEquals(0, h.tracker.report().repetitions)
    }

    @Test fun aPartialOfAnAlreadyClosedSegmentIsIgnored() {
        val h = Harness(3)
        h.final(0, 6)
        h.tracker.onEvent(Ev.part(0, h.say(0, 6), 0))
        assertEquals(6, h.cursorIndex())
    }

    @Test fun anOverlappingChunkDoesNotRevealTheOverlapTwice() {
        // words 4,5 of the first chunk are heard again at the start of the second chunk (same audio, same times)
        val h = Harness(3)
        val first = h.say(0, 6)
        val e1 = Ev.fin(0, first, 0)
        h.tracker.onEvent(e1)
        val second = h.say(4, 9)                         // 4,5 overlap
        val t0 = Ev.STEP * 4                             // they sit at the very same times as in chunk 1
        h.tracker.onEvent(Ev.fin(1, second, t0))
        assertEquals(9, h.cursorIndex())
        assertEquals(0, h.tracker.report().repetitions)
        assertTrue(h.tracker.report().reviewItems.isEmpty())
    }

    @Test fun overlapOfAWordThatLegitimatelyRepeatsIsResolvedByAudioTime() {
        // expected text "a b b c": the second chunk starts with the first "b" again. By time it is the same audio.
        val page = QuranPage(
            900,
            listOf(
                PageLine.Ayahs(
                    1,
                    listOf("بسم", "كلا", "كلا", "سوف", "تعلمون").mapIndexed { i, t -> QuranWord(i + 1, 1, 1, i + 1, t) },
                    false,
                ),
            ),
        )
        val t = RecitationTracker()
        t.begin(page, null, emptyList())
        t.onEvent(Ev.fin(0, listOf("بسم", "كلا"), 0))                                   // 0..800
        t.onEvent(Ev.fin(1, listOf("كلا", "كلا", "سوف"), Ev.STEP))                        // first "كلا" is the SAME audio (t=400)
        assertEquals("expected: بسم كلا(1) | كلا(2) سوف -> 4 words done", 4, t.progress().revealedCount)
    }

    @Test fun overlapWithoutTimestampsFallsBackToAlignmentAndStillCountsEachExpectedWordOnce() {
        val h = Harness(3)
        val a = SpeechEvent.Final(text = h.say(0, 6).joinToString(" "), segmentId = 0)
        val b = SpeechEvent.Final(text = h.say(4, 9).joinToString(" "), segmentId = 1)
        h.tracker.onEvent(a); h.tracker.onEvent(b)
        assertEquals(9, h.cursorIndex())
        assertEquals(9, h.p.revealedCount)
        assertEquals(0, h.p.skippedCount)
    }

    @Test fun aNewEngineRunRestartsSegmentIdsAndTheClockWithoutLosingTheSession() {
        val h = Harness(3)
        val e = Ev.fin(5, h.say(0, 5), 20_000)           // a long first run
        h.tracker.onEvent(e)
        h.tracker.onEvent(SpeechEvent.Listening)         // start() again after an error: ids and clock restart
        h.tracker.onEvent(Ev.fin(0, h.say(5, 9), 0))
        assertEquals(9, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- basmala

    @Test fun aSpokenBasmalaBeforeTheFirstAyahIsIgnored() {
        val h = Harness(262, withBasmallah = true)       // surah 15 starts here: basmallah line before ayah 1
        val b = TestPages.basmallah.map { ArabicMatch.fold(it) }
        h.final(b + h.say(0, 4))
        assertEquals(4, h.cursorIndex())
        assertEquals(0, h.p.skippedCount)
        assertEquals(4, h.p.revealedCount)
    }

    @Test fun basmalaInItsOwnSegmentThenTheAyah() {
        val h = Harness(262, withBasmallah = true)
        h.final(TestPages.basmallah.map { ArabicMatch.fold(it) })
        assertEquals(0, h.cursorIndex())
        h.final(0, 5)
        assertEquals(5, h.cursorIndex())
    }

    @Test fun pageWithoutBasmallahLineDoesNotTreatItAsSpecial() {
        val h = Harness(187, withBasmallah = true)       // surah 9 (At-Tawba) has none
        h.final(h.say(0, 5))
        assertEquals(5, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- manual correction & hint

    @Test fun hintRevealsTheExpectedWordAndMovesOnMarkingItForReview() {
        val h = Harness(3)
        h.final(0, 3)
        h.tracker.hint()
        assertEquals(WordState.Revealed, h.st(3))
        assertEquals(ReviewReason.Hinted, h.reasons()[h.words[3].id])
        assertEquals(4, h.cursorIndex())
        assertEquals(1, h.p.hintCount)
        assertEquals(h.words[3].id, h.p.lastRecognizedId)
    }

    @Test fun movingBackRestartsFromThatWordAndHidesTheRest() {
        val h = Harness(3)
        h.final(0, 8)
        h.tracker.moveTo(h.words[3].id)
        assertEquals(3, h.cursorIndex())
        assertEquals(3, h.p.revealedCount)
        assertEquals(1, h.tracker.report().retries)
        h.final(3, 9)
        assertEquals(9, h.cursorIndex())
    }

    @Test fun markRecitedRevealsASkippedWordByHand() {
        val h = Harness(3)
        h.final(h.say(0, 5) + h.say(6, 10))
        assertEquals(WordState.Skipped, h.st(5))
        h.tracker.markRecited(h.words[5].id)
        assertEquals(WordState.Revealed, h.st(5))
        assertEquals(ReviewReason.Manual, h.reasons()[h.words[5].id])
        assertEquals(10, h.cursorIndex())
    }

    @Test fun manualMoveAtTheVeryStartJustSetsTheStart() {
        val h = Harness(3)
        h.tracker.moveTo(h.words[20].id)
        assertEquals(20, h.cursorIndex())
        assertEquals(0, h.p.skippedCount)
        assertEquals(h.words.size - 20, h.p.totalWords)
    }

    @Test fun moveToAfterSpeechIgnoresWordsAlreadyHeard() {
        val h = Harness(3)
        val e = h.final(0, 6)
        // the reciter taps word 12 while the engine still re-delivers audio up to the same time
        h.tracker.moveTo(h.words[12].id)
        h.tracker.onEvent(Ev.fin(1, h.say(12, 16), e.audioEndMs + 100))
        assertEquals(16, h.cursorIndex())
    }

    // ---------------------------------------------------------------------------------------- feedback & misc

    @Test fun unrelatedSpeechGivesAGentleNoMatchHintAndNothingElse() {
        val h = Harness(3)
        h.final(listOf("زززز", "شششش", "جججج"))
        assertEquals(RecitationFeedback.NoMatch, h.p.feedback)
        assertEquals(0, h.p.revealedCount)
        assertEquals(0, h.cursorIndex())
    }

    @Test fun lowConfidenceNeverProducesAMistakeState() {
        // there is no such state: every WordState is one of hidden / shown / uncertain / skipped
        assertEquals(setOf("Hidden", "Revealed", "Uncertain", "Skipped"), WordState.values().map { it.name }.toSet())
    }

    @Test fun errorEventsAreIgnoredAndFinishedSessionsStayFinished() {
        val h = Harness(1)
        h.tracker.onEvent(SpeechEvent.Error("mic"))
        h.final(0, h.words.size)
        assertTrue(h.p.finished)
        h.final(listOf("زززز"))
        assertTrue(h.p.finished)
        assertEquals(h.words.size, h.p.revealedCount)
    }

    @Test fun reportListsAyahsReachedAndCompleted() {
        val h = Harness(1)
        val n = h.words.size
        h.final(0, n - 2)
        val r = h.tracker.report()
        assertEquals(7, r.ayahsReached.size)
        assertEquals(6, r.ayahsCompleted.size)
        assertEquals(n - 2, r.passedWords)
    }

    @Test fun strictnessChangesAreAppliedOnTheNextEvent() {
        var mode = Strictness.Normal
        val t = RecitationTracker { mode }
        val page = TestPages.page(3)
        t.begin(page, null, emptyList())
        val words = page.recitableWords
        val f = words.map { ArabicMatch.fold(it.text) }
        val i = 10
        mode = Strictness.Strict
        t.onEvent(Ev.fin(0, f.take(i) + mangle(f[i], 1) + f.subList(i + 1, i + 4), 0))
        assertEquals(WordState.Uncertain, t.progress().wordStates[words[i].id])
    }

    @Suppress("unused") private fun unused(a: QuranWord) = a
}
